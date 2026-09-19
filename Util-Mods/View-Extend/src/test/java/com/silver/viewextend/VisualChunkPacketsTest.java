package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VisualChunkPacketsTest {
    private static VisualChunkPreparer.LoadContext context;
    @BeforeAll static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var biomes = new MappedRegistry<Biome>(Registries.BIOME, Lifecycle.stable());
        biomes.register(Biomes.PLAINS, new Biome.BiomeBuilder().hasPrecipitation(true)
                .temperature(.8f).downfall(.4f)
                .specialEffects(new BiomeSpecialEffects.Builder().waterColor(0x3f76e4).build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY).generationSettings(BiomeGenerationSettings.EMPTY).build(),
                RegistrationInfo.BUILT_IN);
        RegistryAccess access = new RegistryAccess.ImmutableRegistryAccess(List.of(biomes.freeze())).freeze();
        context = new VisualChunkPreparer.LoadContext(access, LevelHeightAccessor.create(-64, 384),
                PalettedContainerFactory.create(access), -5, 21, true);
    }

    private static CompoundTag chunk() {
        CompoundTag nbt = new CompoundTag();
        nbt.putString("Status", "minecraft:full");
        nbt.putInt("xPos", 7); nbt.putInt("zPos", -9);
        nbt.putBoolean("isLightOn", true);
        ListTag sections = new ListTag();
        CompoundTag section = new CompoundTag();
        section.putByte("Y", (byte) 0);
        CompoundTag states = new CompoundTag();
        ListTag palette = new ListTag();
        CompoundTag stone = new CompoundTag(); stone.putString("Name", "minecraft:stone");
        palette.add(stone);
        states.put("palette", palette); section.put("block_states", states);
        CompoundTag biomes = new CompoundTag(); ListTag biomePalette = new ListTag();
        biomePalette.add(StringTag.valueOf("minecraft:plains")); biomes.put("palette", biomePalette); section.put("biomes", biomes);
        section.putByteArray("BlockLight", new byte[2048]);
        sections.add(section); nbt.put("sections", sections);
        return nbt;
    }

    @Test void uniformLightIsSharedWithoutChangingWireOrNonuniformLayers() {
        var preparer = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()));
        var a = preparer.prepare(new ChunkPos(7, -9), 0, chunk(), context);
        var b = preparer.prepare(new ChunkPos(7, -9), 0, chunk(), context);
        assertSame(a.packet().getLightData().getSkyUpdates().getFirst(), b.packet().getLightData().getSkyUpdates().getFirst());
        assertTrue(a.estimatedBytes() < VisualChunkPackets.estimate(a.packet()) / 2);
        byte[] varied = new byte[2048]; varied[17] = 12;
        var layers = new ArrayList<byte[]>(List.of(varied));
        VisualChunkPackets.compactLight(layers);
        assertSame(varied, layers.getFirst());
        assertEquals(12, layers.getFirst()[17]);
    }

    @Test void workerPacketRoundTripsThroughVanilla26_2CodecAndSectionReader() {
        var preparer = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()));
        var prepared = preparer.prepare(new ChunkPos(7, -9), 0, chunk(), context);
        RegistryFriendlyByteBuf wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries());
        try {
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(wire, prepared.packet());
            var packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(wire);
            assertEquals(7, packet.getX()); assertEquals(-9, packet.getZ()); assertFalse(wire.isReadable());
            var sections = packet.getChunkData().getReadBuffer();
            try {
                for (int y = -4; y < 20; y++) {
                    LevelChunkSection section = new LevelChunkSection(context.containerFactory());
                    section.read(sections);
                    assertEquals(y == 0 ? Blocks.STONE : Blocks.AIR, section.getBlockState(0, 0, 0).getBlock());
                }
                assertFalse(sections.isReadable());
            } finally { sections.release(); }
            assertEquals(26, packet.getLightData().getEmptyBlockYMask().cardinality());
            assertEquals(0, packet.getLightData().getBlockUpdates().size());
            assertEquals(26, packet.getLightData().getSkyYMask().cardinality());
        } finally { wire.release(); }
    }

    @Test void unfinishedOrMismatchedChunksCannotBeMarkedAsSuccessful() {
        var preparer = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()));
        var nbt = chunk(); nbt.putString("Status", "minecraft:noise");
        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(new ChunkPos(7, -9), 0, nbt, context));
        assertThrows(IllegalArgumentException.class, () -> preparer.prepare(new ChunkPos(8, -9), 0, chunk(), context));
    }

    @Test void zeroLightCompactionPreservesMaskAndArrayOrder() {
        BitSet initialized = new BitSet(); initialized.set(0); initialized.set(2); initialized.set(4);
        byte[] lit = new byte[2048]; lit[10] = 1;
        List<byte[]> layers = new ArrayList<>(List.of(new byte[2048], lit, new byte[2048]));
        BitSet empty = VisualChunkPreparer.compactEmptyLayers(initialized, layers);
        assertTrue(empty.get(0)); assertTrue(empty.get(4)); assertFalse(empty.get(2));
        assertEquals(1, initialized.cardinality()); assertTrue(initialized.get(2));
        assertEquals(1, layers.size()); assertSame(lit, layers.getFirst());
    }

    @Test void directSectionWriteIsByteEquivalentToTheFormerBufferedPath() {
        CompoundTag nbt = representativeChunk();
        var prepared = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()))
                .prepare(new ChunkPos(7, -9), 0, nbt, context);
        SerializableChunkData parsed = SerializableChunkData.parse(
                context.heightAccessor(), context.containerFactory(), nbt);
        var legacy = legacyBufferedPacket(new ChunkPos(7, -9), parsed,
                prepared.packet().getLightData());
        assertArrayEquals(encodePacket(legacy), encodePacket(prepared.packet()));
    }

    @Test void packetBuilderPresizesForRepresentativeFullHeightPayload() {
        CompoundTag nbt = representativeChunk();
        SerializableChunkData parsed = SerializableChunkData.parse(
                context.heightAccessor(), context.containerFactory(), nbt);
        var prepared = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()))
                .prepare(new ChunkPos(7, -9), 0, nbt, context);
        var build = VisualChunkPackets.create(new ChunkPos(7, -9), parsed,
                prepared.packet().getLightData(), context);
        assertFalse(build.bufferGrew());
        assertTrue(build.sectionPayloadBytes() > 0);
        assertEquals(build.initialCapacity(), build.finalCapacity());
        assertTrue(build.composition().blockStateBytes() > 0);
        assertTrue(build.composition().biomeBytes() > 0);
        assertTrue(build.composition().heightmapBytes() > 0);
        assertTrue(build.composition().skyLightBytes() > 0);
        assertTrue(build.composition().blockLightBytes() > 0);
        assertTrue(build.composition().protocolBytes() > 0);
    }

    @Test void representativePacketBuildBenchmark() {
        CompoundTag nbt = representativeChunk();
        SerializableChunkData parsed = SerializableChunkData.parse(
                context.heightAccessor(), context.containerFactory(), nbt);
        var light = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()))
                .prepare(new ChunkPos(7, -9), 0, nbt, context).packet().getLightData();
        for (int index = 0; index < 250; index++) {
            legacyBufferedPacket(new ChunkPos(7, -9), parsed, light);
            VisualChunkPackets.create(new ChunkPos(7, -9), parsed, light, context);
        }
        int iterations = 2_000;
        long legacyStarted = System.nanoTime();
        int consumed = 0;
        for (int index = 0; index < iterations; index++) {
            consumed += legacyBufferedPacket(new ChunkPos(7, -9), parsed, light).getChunkData().getHeightmaps().size();
        }
        long legacyNanos = System.nanoTime() - legacyStarted;
        long directStarted = System.nanoTime();
        long sectionNanos = 0;
        long encodingNanos = 0;
        long decodeNanos = 0;
        int sectionBytes = 0;
        for (int index = 0; index < iterations; index++) {
            var build = VisualChunkPackets.create(new ChunkPos(7, -9), parsed, light, context);
            consumed += build.packet().getChunkData().getHeightmaps().size();
            sectionNanos += build.sectionSerializationNanos();
            encodingNanos += build.encodingNanos();
            decodeNanos += build.decodeNanos();
            sectionBytes = build.sectionPayloadBytes();
        }
        long directNanos = System.nanoTime() - directStarted;
        assertTrue(consumed > 0);
        System.out.printf(java.util.Locale.ROOT,
                "packet build benchmark: buffered=%.4f ms/chunk direct=%.4f ms/chunk change=%.1f%% section=%.4f encode=%.4f decode=%.4f sectionBytes=%d%n",
                legacyNanos / 1_000_000.0 / iterations,
                directNanos / 1_000_000.0 / iterations,
                100.0 * (directNanos - legacyNanos) / legacyNanos,
                sectionNanos / 1_000_000.0 / iterations,
                encodingNanos / 1_000_000.0 / iterations,
                decodeNanos / 1_000_000.0 / iterations, sectionBytes);
    }


    @Test void knownZeroFallbacksUseTheSameEmptyMasksWithoutScanningEveryNibble() {
        var memory = new PipelineMemoryMetrics();
        var metrics = new PreparationMetrics();
        var preparer = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()), memory, metrics);
        var noLightNbt = chunk();
        noLightNbt.getListOrEmpty("sections").getCompoundOrEmpty(0).remove("BlockLight");
        var prepared = preparer.prepare(new ChunkPos(7, -9), 0, noLightNbt, context);

        assertEquals(26, prepared.packet().getLightData().getEmptyBlockYMask().cardinality());
        assertEquals(0, prepared.packet().getLightData().getBlockUpdates().size());
        var snapshot = metrics.drainSnapshot();
        assertEquals(1, snapshot.successes());
        assertEquals(1, snapshot.sectionsProcessed());
        assertTrue(snapshot.lightLayersFastPathed() >= 25);
        assertTrue(snapshot.lightBytesScanAvoided() >= 25L * 2048L);
        assertEquals(0, memory.snapshot().copyingNbt());
    }

    @Test void preparationMetricsKeepOnlyNumbersAcrossSuccessAndFailure() {
        var metrics = new PreparationMetrics();
        var preparer = new VisualChunkPreparer(ViewExtendConfig.fromProperties(new Properties()),
                new PipelineMemoryMetrics(), metrics);
        preparer.prepare(new ChunkPos(7, -9), 0, chunk(), context);
        var unfinished = chunk(); unfinished.putString("Status", "minecraft:noise");
        assertThrows(VisualChunkFailure.UnfinishedChunk.class,
                () -> preparer.prepare(new ChunkPos(7, -9), 0, unfinished, context));

        var snapshot = metrics.drainSnapshot();
        assertEquals(2, snapshot.attempts());
        assertEquals(1, snapshot.successes());
        assertEquals(1, snapshot.failures());
        assertTrue(snapshot.totalNanos() >= snapshot.successfulNanos());
        assertTrue(snapshot.copyNanos() > 0);
        assertTrue(snapshot.sectionsProcessed() > 0);
        assertTrue(snapshot.sectionCopyBytesEliminated() > 0);
        assertEquals(0, snapshot.packetBufferGrowths());
        assertEquals(0, metrics.drainSnapshot().attempts());
    }

    @Test void workerLoadHasTerminalResultsForSuccessMissingErrorsAndShutdown() throws Exception {
        var config = ViewExtendConfig.fromProperties(new Properties());
        var loader = new VisualChunkLoader(config);
        var pos = new ChunkPos(7, -9);
        try {
            var nbt = chunk();
            var original = nbt.copy();
            var good = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.of(nbt)), pos, 0, context)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(good.prepared()); assertNull(good.error()); assertEquals(original, nbt);
            assertEquals(nbt.sizeInBytes(), good.rawNbtBytes());
            var missing = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()), pos, 0, context)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(missing.missing());
            var failed = loader.load(java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("test")), pos, 0, context)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(failed.error());
            awaitReleased(loader);
            assertEquals(0, loader.memorySnapshot().rawCount());
            assertEquals(0, loader.memorySnapshot().queuedTasks());
            assertEquals(0, loader.memorySnapshot().activeTasks());
        } finally { loader.close(); }
        var rejected = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.of(chunk())), pos, 0, context)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(rejected.error());
        assertEquals(0, loader.memorySnapshot().rawCount());
    }

    @Test void unfinishedGenerationIsClassifiedBeforeSectionDecodeAndFailedTimeIsCounted() throws Exception {
        var nbt = chunk();
        nbt.putString("Status", "minecraft:noise");
        nbt.putString("sections", "not a section list");
        try (var loader = new VisualChunkLoader(ViewExtendConfig.fromProperties(new Properties()))) {
            var result = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.of(nbt)),
                    new ChunkPos(7, -9), 0, context).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(VisualChunkFailure.UNFINISHED, VisualChunkFailure.classify(result.missing(), result.error()));
            assertTrue(result.workerNanos() > 0);
            assertNull(result.prepared());
            nbt.putString("Status", "invalid:status");
            var invalid = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.of(nbt)),
                    new ChunkPos(7, -9), 0, context).get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(VisualChunkFailure.PREPARATION, VisualChunkFailure.classify(invalid.missing(), invalid.error()));
        }
    }

    private static void awaitReleased(VisualChunkLoader loader) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
        while ((loader.memorySnapshot().rawCount() != 0
                || loader.memorySnapshot().queuedTasks() != 0
                || loader.memorySnapshot().activeTasks() != 0)
                && System.nanoTime() < deadline) {
            Thread.sleep(1);
        }
    }

    private static CompoundTag representativeChunk() {
        CompoundTag nbt = chunk();
        ListTag sections = new ListTag();
        sections.add(encodedSection((byte) -4, 48, true));
        sections.add(encodedSection((byte) 0, 6, false));
        sections.add(encodedSection((byte) 19, 1, false));
        nbt.put("sections", sections);
        CompoundTag heightmaps = new CompoundTag();
        for (Heightmap.Types type : net.minecraft.world.level.chunk.status.ChunkStatus.FULL.heightmapsAfter()) {
            long[] values = new long[37]; values[0] = type.ordinal() + 1;
            heightmaps.putLongArray(type.getSerializationKey(), values);
        }
        nbt.put("Heightmaps", heightmaps);
        return nbt;
    }

    private static CompoundTag encodedSection(byte sectionY, int distinctStates, boolean waterHeavy) {
        LevelChunkSection section = new LevelChunkSection(context.containerFactory());
        int written = 0;
        outer: for (var block : BuiltInRegistries.BLOCK) {
            for (var state : block.getStateDefinition().getPossibleStates()) {
                if (state.isAir()) continue;
                int coordinate = written++;
                section.setBlockState(coordinate & 15, coordinate >> 8 & 15, coordinate >> 4 & 15,
                        state, false);
                if (written >= distinctStates) break outer;
            }
        }
        if (waterHeavy) {
            for (int y = 0; y < 8; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                section.setBlockState(x, y, z, Blocks.WATER.defaultBlockState(), false);
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putByte("Y", sectionY);
        tag.store("block_states", context.containerFactory().blockStatesContainerCodec(), section.getStates());
        tag.store("biomes", context.containerFactory().biomeContainerCodec(), section.getBiomes());
        if (sectionY == 0) {
            byte[] blockLight = new byte[2048]; blockLight[31] = 0x21;
            byte[] skyLight = new byte[2048]; java.util.Arrays.fill(skyLight, (byte) 0xff);
            tag.putByteArray("BlockLight", blockLight); tag.putByteArray("SkyLight", skyLight);
        }
        return tag;
    }

    private static ClientboundLevelChunkWithLightPacket legacyBufferedPacket(ChunkPos pos,
            SerializableChunkData chunk, ClientboundLightUpdatePacketData light) {
        LevelChunkSection[] sections = new LevelChunkSection[context.heightAccessor().getSectionsCount()];
        for (var section : chunk.sectionData()) {
            int index = context.heightAccessor().getSectionIndexFromSectionY(section.y());
            if (index >= 0 && index < sections.length) sections[index] = section.chunkSection();
        }
        LevelChunkSection empty = new LevelChunkSection(context.containerFactory());
        FriendlyByteBuf sectionBuffer = new FriendlyByteBuf(Unpooled.buffer());
        RegistryFriendlyByteBuf packetBuffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries());
        try {
            for (LevelChunkSection section : sections) (section == null ? empty : section).write(sectionBuffer);
            packetBuffer.writeInt(pos.x()); packetBuffer.writeInt(pos.z());
            HashMap<Heightmap.Types, long[]> heightmaps = new HashMap<>();
            for (Map.Entry<Heightmap.Types, long[]> entry : chunk.heightmaps().entrySet()) {
                if (entry.getKey().sendToClient()) heightmaps.put(entry.getKey(), entry.getValue());
            }
            ByteBufCodecs.map(HashMap::new, Heightmap.Types.STREAM_CODEC, ByteBufCodecs.LONG_ARRAY)
                    .encode(packetBuffer, heightmaps);
            packetBuffer.writeVarInt(sectionBuffer.readableBytes()); packetBuffer.writeBytes(sectionBuffer);
            packetBuffer.writeVarInt(0); light.write(packetBuffer);
            var packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(packetBuffer);
            VisualChunkPackets.compactLight(packet.getLightData().getSkyUpdates());
            VisualChunkPackets.compactLight(packet.getLightData().getBlockUpdates());
            return packet;
        } finally { sectionBuffer.release(); packetBuffer.release(); }
    }

    private static byte[] encodePacket(ClientboundLevelChunkWithLightPacket packet) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), context.registries());
        try {
            ClientboundLevelChunkWithLightPacket.STREAM_CODEC.encode(buffer, packet);
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), bytes); return bytes;
        } finally { buffer.release(); }
    }

}
