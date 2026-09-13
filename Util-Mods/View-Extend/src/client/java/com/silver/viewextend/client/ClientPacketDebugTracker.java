package com.silver.viewextend.client;

import com.silver.viewextend.ViewExtendMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.world.level.ChunkPos;

public final class ClientPacketDebugTracker {
    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty("viewextend.clientDebug", "true"));
    private static final int LOG_INTERVAL_TICKS = Integer.getInteger("viewextend.clientDebugIntervalTicks", 100);
    private static final long MISSING_LIGHT_UPDATE_AGE_MS = Long.getLong("viewextend.clientDebugMissingAgeMs", 2000L);

    private static final Map<Long, ChunkDebugState> STATES = new HashMap<>();
    private static long totalChunkDataPackets;
    private static long totalLightUpdatePackets;

    private static int windowChunkDataPackets;
    private static int windowLightUpdatePackets;
    private static int windowChunkDataSkyInitedBits;
    private static int windowChunkDataBlockInitedBits;
    private static int windowLightUpdateSkyInitedBits;
    private static int windowLightUpdateBlockInitedBits;
    private static int windowChunkDataSkyNibbles;
    private static int windowChunkDataBlockNibbles;
    private static int windowLightUpdateSkyNibbles;
    private static int windowLightUpdateBlockNibbles;
    private static long lastLogMillis;

    private ClientPacketDebugTracker() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static void onChunkData(ClientboundLevelChunkWithLightPacket packet) {
        if (!ENABLED) {
            return;
        }

        long packed = new ChunkPos(packet.getX(), packet.getZ()).pack();
        ChunkDebugState state = STATES.computeIfAbsent(packed, ignored -> new ChunkDebugState());
        state.chunkDataCount++;
        state.lastChunkDataMillis = System.currentTimeMillis();

        ClientboundLightUpdatePacketData light = packet.getLightData();
        int skyBits = light.getSkyYMask().cardinality();
        int blockBits = light.getBlockYMask().cardinality();
        int skyNibbleCount = light.getSkyUpdates().size();
        int blockNibbleCount = light.getBlockUpdates().size();
        if (skyBits > 0 || blockBits > 0 || skyNibbleCount > 0 || blockNibbleCount > 0) {
            state.chunkDataIncludedLight = true;
        }

        totalChunkDataPackets++;

        windowChunkDataPackets++;
        windowChunkDataSkyInitedBits += skyBits;
        windowChunkDataBlockInitedBits += blockBits;
        windowChunkDataSkyNibbles += skyNibbleCount;
        windowChunkDataBlockNibbles += blockNibbleCount;
    }

    public static void onLightUpdate(ClientboundLightUpdatePacket packet) {
        if (!ENABLED) {
            return;
        }

        long packed = new ChunkPos(packet.getX(), packet.getZ()).pack();
        ChunkDebugState state = STATES.computeIfAbsent(packed, ignored -> new ChunkDebugState());
        state.lightUpdateCount++;

        ClientboundLightUpdatePacketData light = packet.getLightData();
        int skyBits = light.getSkyYMask().cardinality();
        int blockBits = light.getBlockYMask().cardinality();
        int skyNibbleCount = light.getSkyUpdates().size();
        int blockNibbleCount = light.getBlockUpdates().size();

        totalLightUpdatePackets++;

        windowLightUpdatePackets++;
        windowLightUpdateSkyInitedBits += skyBits;
        windowLightUpdateBlockInitedBits += blockBits;
        windowLightUpdateSkyNibbles += skyNibbleCount;
        windowLightUpdateBlockNibbles += blockNibbleCount;
    }

    public static void onClientTick() {
        if (!ENABLED) {
            return;
        }

        long now = System.currentTimeMillis();
        long intervalMs = Math.max(250L, LOG_INTERVAL_TICKS * 50L);
        if (now - lastLogMillis < intervalMs) {
            return;
        }
        lastLogMillis = now;

        int missingLightUpdateChunks = 0;
        for (ChunkDebugState state : STATES.values()) {
            if (state.chunkDataCount > 0 && state.lightUpdateCount == 0
                    && !state.chunkDataIncludedLight
                    && now - state.lastChunkDataMillis >= MISSING_LIGHT_UPDATE_AGE_MS) {
                missingLightUpdateChunks++;
            }
        }

        ViewExtendMod.LOGGER.info(
                "[ViewExtend ClientDebug] windowChunkData={} windowLightUpdate={} windowChunkDataBits(sky/block)={}/{} windowLightUpdateBits(sky/block)={}/{} windowChunkDataNibbles(sky/block)={}/{} windowLightUpdateNibbles(sky/block)={}/{} trackedChunks={} missingLightUpdateChunks={} totals(chunkData/lightUpdate)={}/{}",
                windowChunkDataPackets,
                windowLightUpdatePackets,
                windowChunkDataSkyInitedBits,
                windowChunkDataBlockInitedBits,
                windowLightUpdateSkyInitedBits,
                windowLightUpdateBlockInitedBits,
                windowChunkDataSkyNibbles,
                windowChunkDataBlockNibbles,
                windowLightUpdateSkyNibbles,
                windowLightUpdateBlockNibbles,
                STATES.size(),
                missingLightUpdateChunks,
                totalChunkDataPackets,
                totalLightUpdatePackets);

        windowChunkDataPackets = 0;
        windowLightUpdatePackets = 0;
        windowChunkDataSkyInitedBits = 0;
        windowChunkDataBlockInitedBits = 0;
        windowLightUpdateSkyInitedBits = 0;
        windowLightUpdateBlockInitedBits = 0;
        windowChunkDataSkyNibbles = 0;
        windowChunkDataBlockNibbles = 0;
        windowLightUpdateSkyNibbles = 0;
        windowLightUpdateBlockNibbles = 0;
    }

    private static final class ChunkDebugState {
        private long lastChunkDataMillis;
        private int chunkDataCount;
        private int lightUpdateCount;
        private boolean chunkDataIncludedLight;
    }
}
