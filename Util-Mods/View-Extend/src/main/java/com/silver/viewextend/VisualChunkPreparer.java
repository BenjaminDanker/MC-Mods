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
    private final byte[] fallbackSkyLightNibble;
    private final byte[] fallbackBlockLightNibble;
    private final byte[] oceanFallbackSkyLightNibble;

    VisualChunkPreparer(ViewExtendConfig config) {
        this.config = config;
        fallbackSkyLightNibble = createLightLevelNibble(config.fallbackSkyLightLevel());
        fallbackBlockLightNibble = createLightLevelNibble(config.fallbackBlockLightLevel());
        oceanFallbackSkyLightNibble = createLightLevelNibble(config.oceanFallbackSkyLightLevel());
    }

    PreparedVisualChunk prepare(
            ChunkPos expectedPos,
            int lodLevel,
            CompoundTag sourceNbt,
            LoadContext context) {
        var status = sourceNbt.read("Status", net.minecraft.world.level.chunk.status.ChunkStatus.CODEC)
                .orElseThrow(() -> new IllegalArgumentException("Missing or invalid chunk Status: " + sourceNbt.getStringOr("Status", "<missing>")));
        if (status != net.minecraft.world.level.chunk.status.ChunkStatus.FULL) {
            // Reject before copying/parsing section palettes: generation neighbors are common during flight.
            throw new VisualChunkFailure.UnfinishedChunk(sourceNbt.getStringOr("Status", "<missing>"));
        }
        CompoundTag chunkNbt = sourceNbt.copy();
        TransformStats transformStats = transformVisualChunkNbt(chunkNbt, lodLevel);
        SerializableChunkData serialized = SerializableChunkData.parse(
                context.heightAccessor(), context.containerFactory(), chunkNbt);
        if (serialized == null) {
            throw new IllegalArgumentException("Chunk NBT has no status");
        }
        if (!expectedPos.equals(serialized.chunkPos())) {
            throw new IllegalArgumentException(
                    "Chunk NBT position " + serialized.chunkPos() + " does not match " + expectedPos);
        }

        LightBuild lightBuild = createDeterministicLightData(
                expectedPos,
                context.bottomLightSectionY(),
                context.topLightSectionYExclusive(),
                serialized,
                context.hasSkyLight(),
                transformStats.oceanBiome());
        ClientboundLevelChunkWithLightPacket packet = VisualChunkPackets.create(expectedPos, serialized, lightBuild.data(), context);
        int estimatedBytes = VisualChunkPackets.retainedBytes(packet);
        return new PreparedVisualChunk(
                packet, transformStats, lightBuild.stats(), estimatedBytes);
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
        return new TransformStats(remapCount, unsorted, oceanBiome);
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
        for (int index = 0; index < sectionCount; index++) {
            int sectionY = bottomLightSectionY + index;
            SerializableChunkData.SectionData sectionData = dataByIndex[index];

            if (hasSkyLight) {
                initializedSky.set(index);
                if (sectionData != null && sectionData.skyLight() != null) {
                    skyNibbles.add(sectionData.skyLight().getData().clone());
                    skyNbt++;
                } else if (oceanBiome && sectionY * 16 < 64) {
                    if (config.oceanFallbackEnabled()) {
                        skyNibbles.add(oceanFallbackSkyLightNibble);
                    } else {
                        skyNibbles.add(getWaterAwareSkyLightFallback(sectionY));
                        skyWaterFallback++;
                    }
                    skyFallback++;
                } else {
                    skyNibbles.add(fallbackSkyLightNibble);
                    skyFallback++;
                }
            }

            initializedBlock.set(index);
            if (sectionData != null && sectionData.blockLight() != null) {
                blockNibbles.add(sectionData.blockLight().getData().clone());
                blockNbt++;
            } else {
                blockNibbles.add(hasSkyLight ? fallbackBlockLightNibble : fallbackSkyLightNibble);
                blockFallback++;
            }
        }

        BitSet emptySky = compactEmptyLayers(initializedSky, skyNibbles);
        BitSet emptyBlock = compactEmptyLayers(initializedBlock, blockNibbles);
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
                    new LightStats(skyNbt, skyFallback, skyWaterFallback, blockNbt, blockFallback));
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

    record TransformStats(int remapCount, boolean unsortedSections, boolean oceanBiome) {
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

    record LightBuild(ClientboundLightUpdatePacketData data, LightStats stats) {
    }

    record PreparedVisualChunk(
            ClientboundLevelChunkWithLightPacket packet,
            TransformStats transformStats,
            LightStats lightStats,
            int estimatedBytes) {
    }

}
