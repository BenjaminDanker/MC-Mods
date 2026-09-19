package com.silver.viewextend;

import java.util.UUID;
import java.util.PriorityQueue;
import java.util.Comparator;
import java.util.ArrayDeque;
import it.unimi.dsi.fastutil.longs.*;

/** Server-thread-owned delivery ledger for one client world/session. */
final class PlayerViewState {
    final UUID playerUuid;
    long nextRequestId;
    net.minecraft.server.level.ChunkTrackingView vanillaView = net.minecraft.server.level.ChunkTrackingView.EMPTY;
    final PlayerWorkShare share = new PlayerWorkShare();
    int lookahead;
    int sendsThisTick;
    boolean visualBatchOpen;
    boolean refreshEntities;
    final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap pendingRequestIds = new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    final String worldKey;
    final LongOpenHashSet sent = new LongOpenHashSet();
    final LongOpenHashSet pending = new LongOpenHashSet();
    final Long2IntOpenHashMap sentLodByChunk = new Long2IntOpenHashMap();
    final Long2IntOpenHashMap pendingLodByChunk = new Long2IntOpenHashMap();
    // Desired terrain stays lazy. Only sparse one-shot repair/retry demand stores coordinates.
    final LongArrayList directDemand = new LongArrayList();
    final LongOpenHashSet directDemandSet = new LongOpenHashSet();
    final Long2LongOpenHashMap directDemandAge = new Long2LongOpenHashMap();
    final ArrayDeque<LineDemand> movementDemand = new ArrayDeque<>();
    final ArrayDeque<LineDemand> vanillaGapDemand = new ArrayDeque<>();
    PriorityQueue<PlayerViewPlanner.DemandCandidate> movementHeads;
    PriorityQueue<PlayerViewPlanner.DemandCandidate> vanillaGapHeads;
    boolean descriptorHeadsDirty = true;
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
    long bootstrapAge;
    int nearCenterX;
    int nearCenterZ;
    int nearRadius;
    int nearOffset;
    int nearMaxRadius;
    boolean nearActive;
    long nearAge;
    int gapCenterX;
    int gapCenterZ;
    int gapRadius;
    int gapOffset;
    int gapMaxRadius;
    boolean gapActive;
    long gapAge;
    PlayerViewPlanner.DemandCandidate cachedNearHead;
    PlayerViewPlanner.DemandCandidate cachedBootstrapHead;
    PlayerViewPlanner.DemandCandidate cachedGapHead;
    long nextDemandAge;
    long readyFairnessSequence;

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

    /** Compact lazy line used for newly exposed movement strips. */
    static final class LineDemand {
        final boolean vertical;
        final int fixed;
        final int min;
        final int max;
        final int center;
        final long age;
        private int current;
        private int lower;
        private int upper;
        private boolean exhausted;
        private boolean started;

        LineDemand(boolean vertical, int fixed, int min, int max, int center, long age) {
            this.vertical = vertical;
            this.fixed = fixed;
            this.min = min;
            this.max = max;
            this.center = center;
            this.age = age;
            this.current = Math.max(min, Math.min(max, center));
            this.lower = current - 1;
            this.upper = current + 1;
        }

        boolean exhausted() { return exhausted; }
        boolean started() { return started; }
        boolean intersectsSquare(int centerX, int centerZ, int radius) {
            int fixedCenter = vertical ? centerX : centerZ;
            int variableCenter = vertical ? centerZ : centerX;
            return Math.abs(fixed - fixedCenter) <= radius
                    && max >= variableCenter - radius && min <= variableCenter + radius;
        }
        boolean isUntouchedEquivalent(boolean otherVertical, int otherFixed, int otherMin, int otherMax) {
            return !started && !exhausted && vertical == otherVertical && fixed == otherFixed
                    && min == otherMin && max == otherMax;
        }
        long peek() {
            return vertical
                    ? net.minecraft.world.level.ChunkPos.pack(fixed, current)
                    : net.minecraft.world.level.ChunkPos.pack(current, fixed);
        }

        void advance() {
            started = true;
            boolean hasLower = lower >= min;
            boolean hasUpper = upper <= max;
            if (!hasLower && !hasUpper) {
                exhausted = true;
                return;
            }
            if (!hasUpper || (hasLower
                    && Math.abs(lower - center) <= Math.abs(upper - center))) {
                current = lower--;
            } else {
                current = upper++;
            }
        }
    }

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

    int demandDescriptorCount() {
        return movementDemand.size() + vanillaGapDemand.size() + directDemand.size()
                + (nearActive ? 1 : 0) + (gapActive ? 1 : 0) + (bootstrapActive ? 1 : 0);
    }
}
