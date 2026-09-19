package com.silver.viewextend;

import java.util.UUID;
import java.util.PriorityQueue;
import java.util.Comparator;
import it.unimi.dsi.fastutil.longs.*;

/** Server-thread-owned delivery ledger for one client world/session. */
final class PlayerViewState {
    final UUID playerUuid;
    long nextRequestId;
    net.minecraft.server.level.ChunkTrackingView vanillaView = net.minecraft.server.level.ChunkTrackingView.EMPTY;
    final PlayerWorkShare share = new PlayerWorkShare();
    int lookahead;
    int candidateSelectionCounter;
    int sendsThisTick;
    boolean visualBatchOpen;
    boolean refreshEntities;
    final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap pendingRequestIds = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    final String worldKey;
    final LongOpenHashSet sent = new LongOpenHashSet();
    final LongOpenHashSet pending = new LongOpenHashSet();
    final Long2IntOpenHashMap sentLodByChunk = new Long2IntOpenHashMap();
    final Long2IntOpenHashMap pendingLodByChunk = new Long2IntOpenHashMap();
    final LongArrayFIFOQueue candidateQueue = new LongArrayFIFOQueue();
    final LongOpenHashSet candidateQueued = new LongOpenHashSet();
    final Long2IntOpenHashMap retryDueByChunk = new Long2IntOpenHashMap();
    final PriorityQueue<ScheduledChunk> retryQueue =
            new PriorityQueue<>(Comparator.comparingInt(ScheduledChunk::dueTick));
    final Long2IntOpenHashMap unloadDueByChunk = new Long2IntOpenHashMap();
    final PriorityQueue<ScheduledChunk> unloadQueue =
            new PriorityQueue<>(Comparator.comparingInt(ScheduledChunk::dueTick));

    int clientChunkLoadDistance = Integer.MIN_VALUE;
    int centerX = Integer.MIN_VALUE;
    int centerZ = Integer.MIN_VALUE;
    int normalDistance;
    int totalDistance;
    int bootstrapCenterX;
    int bootstrapCenterZ;
    int bootstrapRadius;
    int bootstrapOffset;
    int bootstrapTotalDistance;
    boolean bootstrapActive;
    boolean candidateOverflowed;

    PlayerViewState(UUID playerUuid, String worldKey) {
        this.playerUuid = playerUuid;
        this.worldKey = worldKey;
        this.retryDueByChunk.defaultReturnValue(Integer.MIN_VALUE);
        this.unloadDueByChunk.defaultReturnValue(Integer.MIN_VALUE);
    }

    boolean initialized() {
        return centerX != Integer.MIN_VALUE;
    }

    record ScheduledChunk(long chunkLong, int dueTick) {}

    long markPending(long packed, int lodLevel) {
        pending.add(packed);
        pendingLodByChunk.put(packed, lodLevel);
        long requestId = ++nextRequestId;
        pendingRequestIds.put(packed, requestId);
        return requestId;
    }

    void clearPending(long packed) {
        pending.remove(packed);
        pendingLodByChunk.remove(packed);
        pendingRequestIds.remove(packed);
    }

    void markSent(long packed, int lodLevel) {
        clearPending(packed);
        sent.add(packed);
        sentLodByChunk.put(packed, lodLevel);
        unloadDueByChunk.remove(packed);
        retryDueByChunk.remove(packed);
    }

    boolean isCurrentRequest(PlayerViewState session, long packed, long requestId) {
        return session == this && pendingRequestIds.get(packed) == requestId && pending.contains(packed);
    }
}
