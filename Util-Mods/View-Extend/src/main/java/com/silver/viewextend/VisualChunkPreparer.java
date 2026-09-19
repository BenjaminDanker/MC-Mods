package com.silver.viewextend;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;

/** Worker-only conversion of saved NBT to immutable visual snapshots. Never loads or ticks a world chunk. */
final class VisualChunkPreparer {
    private static final Map<Integer, byte[]> WATER_SKYLIGHT_FALLBACK_CACHE = new ConcurrentHashMap<>();
    private static final int LEGACY_BLOCK_REMAP_MAX_DATA_VERSION = 1518;

    private static final Map<String, String> LEGACY_BLOCK_ID_REMAP = Map.ofEntries(
            Map.entry("minecraft:grass", "minecraft:grass_block"),
            Map.entry("minecraft:grass_path", "minecraft:dirt_path"),
            Map.entry("minecraft:chain", "minecraft:iron_chain"),
            Map.entry("minecraft:lit_pumpkin", "minecraft:jack_o_lantern"),
            Map.entry("minecraft:portal", "minecraft:nether_portal"),
            Map.entry("minecraft:lit_furnace", "minecraft:furnace"),
            Map.entry("minecraft:stone_slab", "minecraft:smooth_stone_slab"),
            Map.entry("minecraft:stone_slab2", "minecraft:red_sandstone_slab"),
            Map.entry("minecraft:wooden_slab", "minecraft:oak_slab"),
            Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
            Map.entry("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate"));

    private static final String[] UNUSED_VISUAL_NBT_KEYS = {
        "entities", "block_entities", "block_ticks", "fluid_ticks", "PostProcessing",
        "structures", "UpgradeData", "blending_data", "below_zero_retrogen", "carving_mask"
    };

    private final ViewExtendConfig config;
    private final PipelineMemoryMetrics memoryMetrics;
    private final PreparationMetrics preparationMetrics;
    private final byte[] fallbackSkyLightNibble;
    private final byte[] fallbackBlockLightNibble;
    private final byte[] oceanFallbackSkyLightNibble;

    VisualChunkPreparer(ViewExtendConfig config) {
        this(config, new PipelineMemoryMetrics(), new PreparationMetrics());
    }

    VisualChunkPreparer(ViewExtendConfig config, PipelineMemoryMetrics memoryMetrics) {
        this(config, memoryMetrics, new PreparationMetrics());
    }

    VisualChunkPreparer(ViewExtendConfig config, PipelineMemoryMetrics memoryMetrics,
            PreparationMetrics preparationMetrics) {
        this.config = config;
        this.memoryMetrics = memoryMetrics;
        this.preparationMetrics = preparationMetrics;
        fallbackSkyLightNibble = createLightLevelNibble(config.fallbackSkyLightLevel());
        fallbackBlockLightNibble = createLightLevelNibble(config.fallbackBlockLightLevel());
        oceanFallbackSkyLightNibble = createLightLevelNibble(config.oceanFallbackSkyLightLevel());
    }

    PreparedVisualChunk prepare(
            ChunkPos expectedPos,
            int lodLevel,
            CompoundTag sourceNbt,
            LoadContext context) {
        long totalStarted = System.nanoTime();
        long validationNanos = 0;
        long copyNanos = 0;
        long transformNanos = 0;
        long parseNanos = 0;
        long lightNanos = 0;
        long packetNanos = 0;
        try {
            long started = System.nanoTime();
            var status = sourceNbt.read("Status", net.minecraft.world.level.chunk.status.ChunkStatus.CODEC)
                    .orElseThrow(() -> new IllegalArgumentException("Missing or invalid chunk Status: " + sourceNbt.getStringOr("Status", "<missing>")));
            if (status != net.minecraft.world.level.chunk.status.ChunkStatus.FULL) {
                // Reject before copying/parsing section palettes: generation neighbors are common during flight.
                throw new VisualChunkFailure.UnfinishedChunk(sourceNbt.getStringOr("Status", "<missing>"));
            }
            validationNanos = System.nanoTime() - started;
            started = System.nanoTime();
            CompoundTag chunkNbt;
            memoryMetrics.copyStarted();
            try {
                chunkNbt = sourceNbt.copy();
            } finally {
                memoryMetrics.copyFinished();
            }
            copyNanos = System.nanoTime() - started;

            started = System.nanoTime();
            TransformStats transformStats = transformVisualChunkNbt(chunkNbt, lodLevel);
            transformNanos = System.nanoTime() - started;

            started = System.nanoTime();
            SerializableChunkData serialized = SerializableChunkData.parse(
                    context.heightAccessor(), context.containerFactory(), chunkNbt);
            parseNanos = System.nanoTime() - started;
            if (serialized == null) throw new IllegalArgumentException("Chunk NBT has no status");
            if (!expectedPos.equals(serialized.chunkPos())) {
                throw new IllegalArgumentException(
                        "Chunk NBT position " + serialized.chunkPos() + " does not match " + expectedPos);
            }

            started = System.nanoTime();
            LightBuild lightBuild = createDeterministicLightData(
                    expectedPos, context.bottomLightSectionY(), context.topLightSectionYExclusive(),
                    serialized, context.hasSkyLight(), transformStats.oceanBiome());
            lightNanos = System.nanoTime() - started;

            started = System.nanoTime();
            VisualChunkPackets.PacketBuild packetBuild = VisualChunkPackets.create(
                    expectedPos, serialized, lightBuild.data(), context);
            ClientboundLevelChunkWithLightPacket packet = packetBuild.packet();
            int estimatedBytes = VisualChunkPackets.retainedBytes(packet);
            packetNanos = System.nanoTime() - started;
            long totalNanos = System.nanoTime() - totalStarted;
            preparationMetrics.recordSuccess(totalNanos, validationNanos, copyNanos, transformNanos, parseNanos,
                    lightNanos, packetNanos, packetBuild.sectionSerializationNanos(),
                    packetBuild.encodingNanos(), packetBuild.decodeNanos(),
                    packetBuild.sectionPayloadBytes(), packetBuild.bufferGrew(),
                    transformStats.sectionsProcessed(),
                    lightBuild.fastPathedLayers(), lightBuild.scanBytesAvoided());
            return new PreparedVisualChunk(packet, transformStats, lightBuild.stats(), estimatedBytes,
                    packetBuild.composition());
        } catch (RuntimeException error) {
            preparationMetrics.recordFailure(System.nanoTime() - totalStarted);
            throw error;
        }
    }

    private TransformStats transformVisualChunkNbt(CompoundTag chunkNbt, int lodLevel) {
        int dataVersion = chunkNbt.getIntOr("DataVersion", Integer.MAX_VALUE);
        boolean shouldRemap = dataVersion <= LEGACY_BLOCK_REMAP_MAX_DATA_VERSION;
        int remapCount = 0;
        boolean oceanBiome = false;

        ListTag sections = chunkNbt.getListOrEmpty("sections");
        List<VisualSection> sectionList = new ArrayList<>(sections.size());
        int previousY = Integer.MIN_VALUE;
        boolean unsorted = false;
        int highestNonAirY = Integer.MIN_VALUE;

        for (int index = 0; index < sections.size(); index++) {
            CompoundTag section = sections.getCompoundOrEmpty(index);
            int sectionY = section.getByteOr("Y", (byte) 0);
            if (sectionY < previousY) {
                unsorted = true;
            }
            previousY = sectionY;

            CompoundTag blockStates = section.getCompoundOrEmpty("block_states");
            ListTag blockPalette = blockStates.getListOrEmpty("palette");
            boolean hasNonAir = false;
            for (int paletteIndex = 0; paletteIndex < blockPalette.size(); paletteIndex++) {
                net.minecraft.nbt.Tag paletteEntry = blockPalette.get(paletteIndex);
                String name = paletteEntry.asString().orElse("");
                if (name.isEmpty()) {
                    name = blockPalette.getCompoundOrEmpty(paletteIndex).getStringOr("Name", "");
                }
                CompoundTag state;
                if (paletteEntry.getId() == net.minecraft.nbt.Tag.TAG_STRING) {
                    state = new CompoundTag();
                    state.putString("Name", name);
                    blockPalette.set(paletteIndex, state);
                } else {
                    state = blockPalette.getCompoundOrEmpty(paletteIndex);
                }
                if (!isAirBlockId(name)) {
                    hasNonAir = true;
                }
                if (shouldRemap) {
                    String remapped = LEGACY_BLOCK_ID_REMAP.get(name);
                    if (remapped != null) {
                        state.putString("Name", remapped);
                        remapCount++;
                    }
                }
            }
            if (hasNonAir) {
                highestNonAirY = Math.max(highestNonAirY, sectionY);
            }

            CompoundTag biomes = section.getCompoundOrEmpty("biomes");
            ListTag biomePalette = biomes.getListOrEmpty("palette");
            for (int paletteIndex = 0; paletteIndex < biomePalette.size() && !oceanBiome; paletteIndex++) {
                String biomeId = biomePalette.getString(paletteIndex).orElse("");
                if (biomeId.isEmpty()) {
                    biomeId = biomePalette.getCompoundOrEmpty(paletteIndex).getStringOr("Name", "");
                }
                oceanBiome = isWaterDominantBiomeId(biomeId);
            }
            sectionList.add(new VisualSection(section, sectionY, hasNonAir));
        }

        if (unsorted) {
            sectionList.sort(Comparator.comparingInt(VisualSection::sectionY));
        }

        if (lodLevel > 0) {
            int minKeepY = highestNonAirY == Integer.MIN_VALUE
                    ? Integer.MAX_VALUE
                    : highestNonAirY - (config.lod1TopNonAirSections() - 1);
            ListTag filteredSections = new ListTag();
            for (VisualSection section : sectionList) {
                if (section.sectionY() >= minKeepY && section.hasNonAir()) {
                    filteredSections.add(section.tag());
                }
            }
            chunkNbt.put("sections", filteredSections);
        } else if (unsorted) {
            ListTag sortedSections = new ListTag();
            for (VisualSection section : sectionList) {
                sortedSections.add(section.tag());
            }
            chunkNbt.put("sections", sortedSections);
        }

        for (String key : UNUSED_VISUAL_NBT_KEYS) {
            chunkNbt.remove(key);
        }
        return new TransformStats(remapCount, unsorted, oceanBiome, sections.size());
    }

    private static boolean isAirBlockId(String id) {
        return "minecraft:air".equals(id)
                || "minecraft:cave_air".equals(id)
                || "minecraft:void_air".equals(id);
    }

    private static boolean isWaterDominantBiomeId(String biomeId) {
        String lower = biomeId.toLowerCase(Locale.ROOT);
        int separator = lower.indexOf(':');
        String path = separator >= 0 ? lower.substring(separator + 1) : lower;
        return path.contains("ocean")
                || path.equals("river")
                || path.equals("frozen_river")
                || path.equals("beach")
                || path.equals("snowy_beach")
                || path.equals("stony_shore")
                || path.equals("swamp")
                || path.equals("mangrove_swamp");
    }

    private LightBuild createDeterministicLightData(
            ChunkPos pos,
            int bottomLightSectionY,
            int topLightSectionYExclusive,
            SerializableChunkData serialized,
            boolean hasSkyLight,
            boolean oceanBiome) {
        int sectionCount = Math.max(0, topLightSectionYExclusive - bottomLightSectionY);
        BitSet initializedSky = new BitSet(sectionCount);
        BitSet initializedBlock = new BitSet(sectionCount);
        BitSet emptySky = new BitSet(sectionCount);
        BitSet emptyBlock = new BitSet(sectionCount);
        List<byte[]> skyNibbles = new ArrayList<>(sectionCount);
        List<byte[]> blockNibbles = new ArrayList<>(sectionCount);
        SerializableChunkData.SectionData[] dataByIndex = new SerializableChunkData.SectionData[sectionCount];

        for (SerializableChunkData.SectionData sectionData : serialized.sectionData()) {
            int index = sectionData.y() - bottomLightSectionY;
            if (index >= 0 && index < sectionCount) {
                dataByIndex[index] = sectionData;
            }
        }

        int skyNbt = 0;
        int skyFallback = 0;
        int skyWaterFallback = 0;
        int blockNbt = 0;
        int blockFallback = 0;
        int fastPathedLayers = 0;
        long scanBytesAvoided = 0;
        for (int index = 0; index < sectionCount; index++) {
            int sectionY = bottomLightSectionY + index;
            SerializableChunkData.SectionData sectionData = dataByIndex[index];

            if (hasSkyLight) {
                byte[] layer;
                boolean knownZero;
                if (sectionData != null && sectionData.skyLight() != null) {
                    layer = sectionData.skyLight().getData().clone();
                    knownZero = false;
                    skyNbt++;
                } else if (oceanBiome && sectionY * 16 < 64) {
                    if (config.oceanFallbackEnabled()) {
                        layer = oceanFallbackSkyLightNibble;
                    } else {
                        layer = getWaterAwareSkyLightFallback(sectionY);
                        skyWaterFallback++;
                    }
                    knownZero = isKnownZeroFallback(layer);
                    skyFallback++;
                } else {
                    layer = fallbackSkyLightNibble;
                    knownZero = isKnownZeroFallback(layer);
                    skyFallback++;
                }
                if (appendLightLayer(index, layer, knownZero, initializedSky, emptySky, skyNibbles)) {
                    fastPathedLayers++;
                    scanBytesAvoided += layer.length;
                }
            }

            byte[] blockLayer;
            boolean knownZero;
            if (sectionData != null && sectionData.blockLight() != null) {
                blockLayer = sectionData.blockLight().getData().clone();
                knownZero = false;
                blockNbt++;
            } else {
                blockLayer = hasSkyLight ? fallbackBlockLightNibble : fallbackSkyLightNibble;
                knownZero = isKnownZeroFallback(blockLayer);
                blockFallback++;
            }
            if (appendLightLayer(index, blockLayer, knownZero, initializedBlock, emptyBlock, blockNibbles)) {
                fastPathedLayers++;
                scanBytesAvoided += blockLayer.length;
            }
        }

        FriendlyByteBuf lightBuffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            lightBuffer.writeBitSet(initializedSky);
            lightBuffer.writeBitSet(initializedBlock);
            lightBuffer.writeBitSet(emptySky);
            lightBuffer.writeBitSet(emptyBlock);
            lightBuffer.writeCollection(skyNibbles, (buf, bytes) -> buf.writeByteArray(bytes));
            lightBuffer.writeCollection(blockNibbles, (buf, bytes) -> buf.writeByteArray(bytes));
            ClientboundLightUpdatePacketData data = new ClientboundLightUpdatePacketData(
                    lightBuffer, pos.x(), pos.z());
            return new LightBuild(
                    data,
                    new LightStats(skyNbt, skyFallback, skyWaterFallback, blockNbt, blockFallback),
                    fastPathedLayers, scanBytesAvoided);
        } finally {
            lightBuffer.release();
        }
    }

    static BitSet compactEmptyLayers(BitSet initialized, List<byte[]> layers) {
        BitSet empty = new BitSet();
        int index = 0;
        for (int bit = initialized.nextSetBit(0); bit >= 0; bit = initialized.nextSetBit(bit + 1)) {
            byte[] layer = layers.get(index);
            boolean zero = true;
            for (byte value : layer) if (value != 0) { zero = false; break; }
            if (zero) {
                empty.set(bit);
                initialized.clear(bit);
                layers.remove(index);
            } else index++;
        }
        return empty;
    }

    private boolean isKnownZeroFallback(byte[] layer) {
        return layer == fallbackSkyLightNibble && config.fallbackSkyLightLevel() == 0
                || layer == fallbackBlockLightNibble && config.fallbackBlockLightLevel() == 0
                || layer == oceanFallbackSkyLightNibble && config.oceanFallbackSkyLightLevel() == 0;
    }

    /** Returns true when the zero test was skipped because the immutable fallback is known zero. */
    private static boolean appendLightLayer(int sectionIndex, byte[] layer, boolean knownZero,
            BitSet initialized, BitSet empty, List<byte[]> layers) {
        if (knownZero || isAllZero(layer)) {
            empty.set(sectionIndex);
            return knownZero;
        }
        initialized.set(sectionIndex);
        layers.add(layer);
        return false;
    }

    private static boolean isAllZero(byte[] layer) {
        for (byte value : layer) if (value != 0) return false;
        return true;
    }

    private static byte[] createLightLevelNibble(int lightLevel) {
        if (lightLevel < 0 || lightLevel > 15) {
            throw new IllegalArgumentException("Light level must be 0-15, got " + lightLevel);
        }
        byte[] data = new byte[2048];
        byte value = (byte) ((lightLevel << 4) | lightLevel);
        if (value != 0) {
            java.util.Arrays.fill(data, value);
        }
        return data;
    }

    private static byte[] createWaterAwareSkyLightFallback(int sectionY) {
        int sectionBaseY = sectionY * 16;
        DataLayer layer = new DataLayer(0);
        for (int localY = 0; localY < 16; localY++) {
            int depth = Math.max(0, 63 - (sectionBaseY + localY));
            int brightness = Math.max(0, 15 - depth);
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    layer.set(x, localY, z, brightness);
                }
            }
        }
        return layer.getData();
    }

    private static byte[] getWaterAwareSkyLightFallback(int sectionY) {
        return WATER_SKYLIGHT_FALLBACK_CACHE.computeIfAbsent(
                sectionY, VisualChunkPreparer::createWaterAwareSkyLightFallback);
    }

    record LoadContext(
            net.minecraft.core.RegistryAccess registries,
            LevelHeightAccessor heightAccessor,
            PalettedContainerFactory containerFactory,
            int bottomLightSectionY,
            int topLightSectionYExclusive,
            boolean hasSkyLight) {
    }

    record TransformStats(int remapCount, boolean unsortedSections, boolean oceanBiome,
            int sectionsProcessed) {
    }

    record VisualSection(CompoundTag tag, int sectionY, boolean hasNonAir) {
    }

    record LightStats(
            int skyNbt,
            int skyFallback,
            int skyWaterFallback,
            int blockNbt,
            int blockFallback) {
    }

    record LightBuild(ClientboundLightUpdatePacketData data, LightStats stats,
            int fastPathedLayers, long scanBytesAvoided) {
    }

    record PreparedVisualChunk(
            ClientboundLevelChunkWithLightPacket packet,
            TransformStats transformStats,
            LightStats lightStats,
            int estimatedBytes,
            VisualChunkPackets.PacketComposition composition) {
    }

}
