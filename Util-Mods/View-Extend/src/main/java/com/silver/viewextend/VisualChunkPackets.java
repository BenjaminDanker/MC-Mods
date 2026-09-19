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

    static ClientboundLevelChunkWithLightPacket create(ChunkPos pos, SerializableChunkData chunk,
            ClientboundLightUpdatePacketData light, VisualChunkPreparer.LoadContext context) {
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
            packetBuffer.writeInt(pos.x());
            packetBuffer.writeInt(pos.z());
            HashMap<Heightmap.Types, long[]> heightmaps = new HashMap<>();
            for (Map.Entry<Heightmap.Types, long[]> entry : chunk.heightmaps().entrySet()) {
                if (entry.getKey().sendToClient()) heightmaps.put(entry.getKey(), entry.getValue());
            }
            ByteBufCodecs.map(HashMap::new, Heightmap.Types.STREAM_CODEC, ByteBufCodecs.LONG_ARRAY)
                    .encode(packetBuffer, heightmaps);
            packetBuffer.writeVarInt(sectionBuffer.readableBytes());
            packetBuffer.writeBytes(sectionBuffer);
            packetBuffer.writeVarInt(0); // Visual snapshots never instantiate block entities.
            light.write(packetBuffer);
            var packet = ClientboundLevelChunkWithLightPacket.STREAM_CODEC.decode(packetBuffer);
            compactLight(packet.getLightData().getSkyUpdates());
            compactLight(packet.getLightData().getBlockUpdates());
            return packet;
        } finally {
            sectionBuffer.release();
            packetBuffer.release();
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
