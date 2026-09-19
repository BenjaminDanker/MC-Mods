package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.Lifecycle;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Properties;
import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
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
            var missing = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty()), pos, 0, context)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(missing.missing());
            var failed = loader.load(java.util.concurrent.CompletableFuture.failedFuture(new java.io.IOException("test")), pos, 0, context)
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertNotNull(failed.error());
        } finally { loader.close(); }
        var rejected = loader.load(java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.of(chunk())), pos, 0, context)
                .get(10, java.util.concurrent.TimeUnit.SECONDS);
        assertNotNull(rejected.error());
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
}
