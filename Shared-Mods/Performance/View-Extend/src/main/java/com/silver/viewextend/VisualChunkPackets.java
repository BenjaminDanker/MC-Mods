package com.silver.viewextend;

import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import net.minecraft.world.level.levelgen.Heightmap;

/** Encodes detached sections once on a worker, with no LevelChunk or ServerLevel construction.
 * The immutable vanilla packet can then be shared by every observer in this server registry.
 * Wire layout is the 26.2 ClientboundLevelChunkPacketData layout, decoded by its vanilla codec.
 */
final class VisualChunkPackets {
    private VisualChunkPackets() {}

    static PacketBuild create(ChunkPos pos, SerializableChunkData chunk,
            ClientboundLightUpdatePacketData light, VisualChunkPreparer.LoadContext context) {
        LevelChunkSection[] sections = new LevelChunkSection[context.heightAccessor().getSectionsCount()];
        for (var section : chunk.sectionData()) {
            int index = context.heightAccessor().getSectionIndexFromSectionY(section.y());
            if (index >= 0 && index < sections.length) sections[index] = section.chunkSection();
        }
        LevelChunkSection empty = new LevelChunkSection(context.containerFactory());
        int sectionPayloadBytes = 0;
        int blockStateBytes = 0;
        int biomeBytes = 0;
        int sectionOverheadBytes = 0;
        for (LevelChunkSection section : sections) {
            LevelChunkSection actual = section == null ? empty : section;
            int states = actual.getStates().getSerializedSize();
            int biomes = actual.getBiomes().getSerializedSize();
            blockStateBytes += states;
            biomeBytes += biomes;
            sectionOverheadBytes += 4; // non-empty and fluid counts
            sectionPayloadBytes += 4 + states + biomes;
        }
        HashMap<Heightmap.Types, long[]> heightmaps = new HashMap<>();
        for (Map.Entry<Heightmap.Types, long[]> entry : chunk.heightmaps().entrySet()) {
            if (entry.getKey().sendToClient()) heightmaps.put(entry.getKey(), entry.getValue());
        }
        int initialCapacity = estimateTemporaryBufferBytes(sectionPayloadBytes, heightmaps, light);
        RegistryFriendlyByteBuf packetBuffer = new RegistryFriendlyByteBuf(
                Unpooled.buffer(initialCapacity), context.registries());
        try {
            long started = System.nanoTime();
            packetBuffer.writeInt(pos.x());
            packetBuffer.writeInt(pos.z());
            ByteBufCodecs.map(HashMap::new, Heightmap.Types.STREAM_CODEC, ByteBufCodecs.LONG_ARRAY)
                    .encode(packetBuffer, heightmaps);
            packetBuffer.writeVarInt(sectionPayloadBytes);
            long encodingNanos = System.nanoTime() - started;

            started = System.nanoTime();
            int sectionStart = packetBuffer.writerIndex();
            for (LevelChunkSection section : sections) (section == null ? empty : section).write(packetBuffer);
            int writtenSectionBytes = packetBuffer.writerIndex() - sectionStart;
            if (writtenSectionBytes != sectionPayloadBytes) {
                throw new IllegalStateException("Section size changed while encoding: expected "
                        + sectionPayloadBytes + " bytes, wrote " + writtenSectionBytes);
            }
            long sectionSerializationNanos = System.nanoTime() - started;

            started = System.nanoTime();
            packetBuffer.writeVarInt(0); // Visual snapshots never instantiate block entities.
            light.write(packetBuffer);
            encodingNanos += System.nanoTime() - started;
            int finalCapacity = packetBuffer.capacity();
            PacketComposition composition = new PacketComposition(blockStateBytes, biomeBytes,
                    heightmapBytes(heightmaps), skyLightArrayBytes(light.getSkyUpdates()),
                    blockLightArrayBytes(light.getBlockUpdates()), lightMaskAndListBytes(light),
                    8 + varIntBytes(sectionPayloadBytes) + 1 + sectionOverheadBytes);
            if (composition.totalBytes() != packetBuffer.writerIndex()) {
                throw new IllegalStateException("Packet accounting mismatch: expected "
                        + composition.totalBytes() + " bytes, wrote " + packetBuffer.writerIndex());
            }

            started = System.nanoTime();
            var packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(packetBuffer);
            long decodeNanos = System.nanoTime() - started;
            compactLight(packet.getLightData().getSkyUpdates());
            compactLight(packet.getLightData().getBlockUpdates());
            return new PacketBuild(packet, sectionSerializationNanos, encodingNanos, decodeNanos,
                    sectionPayloadBytes, initialCapacity, finalCapacity, composition);
        } finally {
            packetBuffer.release();
        }
    }

    private static int estimateTemporaryBufferBytes(int sectionBytes,
            Map<Heightmap.Types, long[]> heightmaps, ClientboundLightUpdatePacketData light) {
        long bytes = 8L + varIntBytes(heightmaps.size());
        for (long[] values : heightmaps.values()) {
            bytes += 1L + varIntBytes(values.length) + (long) values.length * Long.BYTES;
        }
        bytes += varIntBytes(sectionBytes) + sectionBytes + 1L;
        bytes += bitSetBytes(light.getSkyYMask()) + bitSetBytes(light.getBlockYMask())
                + bitSetBytes(light.getEmptySkyYMask()) + bitSetBytes(light.getEmptyBlockYMask());
        bytes += lightListBytes(light.getSkyUpdates()) + lightListBytes(light.getBlockUpdates());
        // Small guard for codec representation changes while still avoiding normal buffer growth.
        return (int) Math.min(Integer.MAX_VALUE - 8L, Math.max(256L, bytes + 32L));
    }

    private static long bitSetBytes(java.util.BitSet bits) {
        int words = (bits.length() + 63) >>> 6;
        return varIntBytes(words) + (long) words * Long.BYTES;
    }

    private static long lightListBytes(java.util.List<byte[]> layers) {
        long bytes = varIntBytes(layers.size());
        for (byte[] layer : layers) bytes += varIntBytes(layer.length) + layer.length;
        return bytes;
    }

    private static int heightmapBytes(Map<Heightmap.Types, long[]> heightmaps) {
        long bytes = varIntBytes(heightmaps.size());
        for (long[] values : heightmaps.values()) {
            bytes += 1L + varIntBytes(values.length) + (long) values.length * Long.BYTES;
        }
        return checkedBytes(bytes);
    }

    private static int skyLightArrayBytes(java.util.List<byte[]> layers) { return arrayBytes(layers); }
    private static int blockLightArrayBytes(java.util.List<byte[]> layers) { return arrayBytes(layers); }

    private static int arrayBytes(java.util.List<byte[]> layers) {
        long bytes = 0;
        for (byte[] layer : layers) bytes += layer.length;
        return checkedBytes(bytes);
    }

    private static int lightMaskAndListBytes(ClientboundLightUpdatePacketData light) {
        long bytes = bitSetBytes(light.getSkyYMask()) + bitSetBytes(light.getBlockYMask())
                + bitSetBytes(light.getEmptySkyYMask()) + bitSetBytes(light.getEmptyBlockYMask())
                + varIntBytes(light.getSkyUpdates().size()) + varIntBytes(light.getBlockUpdates().size());
        for (byte[] layer : light.getSkyUpdates()) bytes += varIntBytes(layer.length);
        for (byte[] layer : light.getBlockUpdates()) bytes += varIntBytes(layer.length);
        return checkedBytes(bytes);
    }

    private static int checkedBytes(long bytes) {
        if (bytes > Integer.MAX_VALUE) throw new IllegalArgumentException("Packet field too large: " + bytes);
        return (int) bytes;
    }

    private static int varIntBytes(int value) {
        int bytes = 1;
        while ((value & ~0x7f) != 0) { value >>>= 7; bytes++; }
        return bytes;
    }

    record PacketBuild(ClientboundLevelChunkWithLightPacket packet,
            long sectionSerializationNanos, long encodingNanos, long decodeNanos,
            int sectionPayloadBytes, int initialCapacity, int finalCapacity,
            PacketComposition composition) {
        boolean bufferGrew() { return finalCapacity > initialCapacity; }
    }

    /** Exact packet-codec payload categories, before packet-ID and compression framing. */
    record PacketComposition(int blockStateBytes, int biomeBytes, int heightmapBytes,
            int skyLightBytes, int blockLightBytes, int lightMaskBytes, int protocolBytes) {
        int totalBytes() {
            return blockStateBytes + biomeBytes + heightmapBytes + skyLightBytes + blockLightBytes
                    + lightMaskBytes + protocolBytes;
        }
    }
    // Published packets are immutable. Only the sixteen uniform nibble layers are interned.
    private static final byte[][] UNIFORM_LIGHT = new byte[16][2048];
    static {
        for (int i = 0; i < 16; i++) java.util.Arrays.fill(UNIFORM_LIGHT[i], (byte) (i * 17));
    }
    static void compactLight(java.util.List<byte[]> updates) {
        for (int i = 0; i < updates.size(); i++) {
            byte[] layer = updates.get(i);
            if (layer.length == 2048) {
                byte[] shared = UNIFORM_LIGHT[layer[0] & 15];
                if (java.util.Arrays.equals(layer, shared)) updates.set(i, shared);
            }
        }
    }
    static int retainedBytes(ClientboundLevelChunkWithLightPacket packet) {
        int bytes = estimate(packet) + 512; // packet, keys, maps, lists and masks
        for (var updates : java.util.List.of(packet.getLightData().getSkyUpdates(), packet.getLightData().getBlockUpdates())) {
            for (byte[] layer : updates) {
                bytes += 32;
                if (layer.length == 2048 && layer == UNIFORM_LIGHT[layer[0] & 15]) bytes -= layer.length;
            }
        }
        return bytes;
    }
    static int estimate(ClientboundLevelChunkWithLightPacket packet) {
        FriendlyByteBuf chunk = packet.getChunkData().getReadBuffer();
        int bytes;
        try { bytes = chunk.readableBytes() + 512; }
        finally { chunk.release(); }
        for (long[] heights : packet.getChunkData().getHeightmaps().values()) bytes += heights.length * Long.BYTES + 32;
        ClientboundLightUpdatePacketData light = packet.getLightData();
        for (byte[] update : light.getSkyUpdates()) {
            bytes += update.length + 3;
        }
        for (byte[] update : light.getBlockUpdates()) {
            bytes += update.length + 3;
        }
        return bytes;
    }

}
