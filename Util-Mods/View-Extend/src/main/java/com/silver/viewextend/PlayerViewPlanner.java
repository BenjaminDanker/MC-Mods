package com.silver.viewextend;

import java.util.PriorityQueue;
import java.util.function.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import com.silver.viewextend.PlayerViewState.ScheduledChunk;
import static com.silver.viewextend.ViewExtendGeometry.*;

/** Coverage, retries and expiry only. Does not read worlds or send packets. */
final class PlayerViewPlanner {
    private static final int SMALL_MOVE_LIMIT = 8;
    private final ViewExtendConfig config;
    int ticks;

    PlayerViewPlanner(ViewExtendConfig config) { this.config = config; }

    void updateVanillaView(PlayerViewState state, net.minecraft.server.level.ChunkTrackingView current) {
        net.minecraft.server.level.ChunkTrackingView.difference(state.vanillaView, current,
                ignored -> {}, pos -> enqueueCandidate(state, pos.pack()));
        state.vanillaView = current;
    }

    void updatePlayerView(
            PlayerViewState state,
            int centerX,
            int centerZ,
            int normalDistance,
            int totalDistance) {
        if (!state.initialized()) {
            state.centerX = centerX;
            state.centerZ = centerZ;
            state.normalDistance = normalDistance;
            state.totalDistance = totalDistance;
            resetBootstrap(state, centerX, centerZ, normalDistance, totalDistance);
            reconcileTrackedChunks(state, centerX, centerZ, normalDistance, totalDistance);
            return;
        }

        int oldCenterX = state.centerX;
        int oldCenterZ = state.centerZ;
        int oldNormalDistance = state.normalDistance;
        int oldTotalDistance = state.totalDistance;
        if (oldCenterX == centerX
                && oldCenterZ == centerZ
                && oldNormalDistance == normalDistance
                && oldTotalDistance == totalDistance) {
            return;
        }

        // Prune only the departed strip for normal flight, not the whole retry map.
        if (oldTotalDistance == totalDistance && Math.abs(centerX - oldCenterX) <= SMALL_MOVE_LIMIT
                && Math.abs(centerZ - oldCenterZ) <= SMALL_MOVE_LIMIT) {
            forEachSquareDifference(oldCenterX, oldCenterZ, oldTotalDistance,
                    centerX, centerZ, totalDistance, packed -> state.retryDueByChunk.remove(packed));
        } else {
            var retries = state.retryDueByChunk.keySet().iterator();
            while (retries.hasNext()) {
                long packed = retries.nextLong();
                if (!withinDistance(ChunkPos.getX(packed), ChunkPos.getZ(packed), centerX, centerZ, totalDistance)) retries.remove();
            }
        }
        compactScheduledQueueIfNeeded(state.retryQueue, state.retryDueByChunk);
        int deltaX = Math.abs(centerX - oldCenterX);
        int deltaZ = Math.abs(centerZ - oldCenterZ);
        boolean distancesChanged = oldNormalDistance != normalDistance || oldTotalDistance != totalDistance;
        boolean largeMove = deltaX > SMALL_MOVE_LIMIT || deltaZ > SMALL_MOVE_LIMIT;

        state.centerX = centerX;
        state.centerZ = centerZ;
        state.normalDistance = normalDistance;
        state.totalDistance = totalDistance;

        forEachSquareDifference(centerX, centerZ, totalDistance,
                oldCenterX, oldCenterZ, oldTotalDistance, packed -> {
                    state.sent.remove(packed);
                    state.sentLodByChunk.remove(packed);
                });

        if (distancesChanged || largeMove) {
            resetBootstrap(state, centerX, centerZ, normalDistance, totalDistance);
            reconcileTrackedChunks(state, centerX, centerZ, normalDistance, totalDistance);
            return;
        }

        int oldUnloadDistance = oldTotalDistance + config.unloadBufferChunks();
        int newUnloadDistance = totalDistance + config.unloadBufferChunks();

        LongConsumer enqueueIfExtended = packed -> {
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (withinDistance(x, z, centerX, centerZ, totalDistance)) {
                if (state.unloadDueByChunk.remove(packed) != Integer.MIN_VALUE) {
                    // The client may have evicted this during grace; force a replacement packet.
                    state.sent.remove(packed);
                    state.sentLodByChunk.remove(packed);
                }
                enqueueCandidate(state, packed);
            }
        };
        forEachSquareDifference(
                centerX, centerZ, totalDistance,
                oldCenterX, oldCenterZ, oldTotalDistance,
                enqueueIfExtended);
        forEachSquareDifference(
                oldCenterX, oldCenterZ, oldNormalDistance,
                centerX, centerZ, normalDistance,
                enqueueIfExtended);

        int lod0Radius = config.lod1StartDistance() - 1;
        if (lod0Radius < totalDistance) {
            forEachSquareDifference(centerX, centerZ, lod0Radius,
                    oldCenterX, oldCenterZ, lod0Radius, enqueueIfExtended);
        }

        forEachSquareDifference(
                oldCenterX, oldCenterZ, oldUnloadDistance,
                centerX, centerZ, newUnloadDistance,
                packed -> scheduleUnloadIfSent(state, packed));
        forEachSquareDifference(
                centerX, centerZ, newUnloadDistance,
                oldCenterX, oldCenterZ, oldUnloadDistance,
                packed -> state.unloadDueByChunk.remove(packed));

    }

    void reconcileTrackedChunks(
            PlayerViewState state,
            int centerX,
            int centerZ,
            int normalDistance,
            int totalDistance) {
        int unloadDistance = totalDistance + config.unloadBufferChunks();

        LongIterator sentIterator = state.sent.iterator();
        while (sentIterator.hasNext()) {
            long packed = sentIterator.nextLong();
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (!withinDistance(x, z, centerX, centerZ, unloadDistance)) {
                scheduleUnloadIfSent(state, packed);
            } else {
                state.unloadDueByChunk.remove(packed);
            }
        }

        LongIterator pendingIterator = state.pending.iterator();
        while (pendingIterator.hasNext()) {
            long packed = pendingIterator.nextLong();
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (!withinDistance(x, z, centerX, centerZ, totalDistance)) {
                pendingIterator.remove();
                state.pendingLodByChunk.remove(packed);
                state.pendingRequestIds.remove(packed);
            }
        }
    }

    void resetBootstrap(
            PlayerViewState state,
            int centerX,
            int centerZ,
            int normalDistance,
            int totalDistance) {
        state.bootstrapCenterX = centerX;
        state.bootstrapCenterZ = centerZ;
        state.bootstrapRadius = 1;
        state.bootstrapOffset = 0;
        state.bootstrapTotalDistance = totalDistance;
        state.bootstrapActive = totalDistance > 0;
    }

    static long nextBootstrapCandidate(PlayerViewState state) {
        while (state.bootstrapActive) {
            if (state.bootstrapRadius > state.bootstrapTotalDistance) {
                state.bootstrapActive = false;
                return Long.MIN_VALUE;
            }
            int perimeter = ViewExtendGeometry.ringPerimeter(state.bootstrapRadius);
            if (state.bootstrapOffset >= perimeter) {
                state.bootstrapRadius++;
                state.bootstrapOffset = 0;
                continue;
            }
            return ViewExtendGeometry.ringChunk(
                    state.bootstrapCenterX,
                    state.bootstrapCenterZ,
                    state.bootstrapRadius,
                    state.bootstrapOffset++);
        }
        return Long.MIN_VALUE;
    }

    void enqueueCandidate(PlayerViewState state, long packed) {
        int maxQueued = 2048; // Coordinates are cheap; keep movement strips independent of packet lookahead.
        if (state.candidateQueued.contains(packed)) {
            return;
        }
        if (state.candidateQueue.size() >= maxQueued) {
            state.candidateOverflowed = true;
            return;
        }
        state.candidateQueued.add(packed);
        state.candidateQueue.enqueue(packed);
    }

    void scheduleRetry(PlayerViewState state, long packed, int delayTicks) {
        if (state.initialized() && !withinDistance(ChunkPos.getX(packed), ChunkPos.getZ(packed),
                state.centerX, state.centerZ, state.totalDistance)) return;
        int dueTick = ticks + Math.max(1, delayTicks);
        int previousDue = state.retryDueByChunk.getOrDefault(packed, Integer.MIN_VALUE);
        if (previousDue != Integer.MIN_VALUE && previousDue <= dueTick) {
            return;
        }
        state.retryDueByChunk.put(packed, dueTick);
        state.retryQueue.add(new ScheduledChunk(packed, dueTick));
    }

    void drainDueRetries(PlayerViewState state) {
        while (!state.retryQueue.isEmpty() && state.retryQueue.peek().dueTick() <= ticks) {
            ScheduledChunk scheduled = state.retryQueue.poll();
            if (state.retryDueByChunk.getOrDefault(scheduled.chunkLong(), Integer.MIN_VALUE)
                    != scheduled.dueTick()) {
                continue;
            }
            state.retryDueByChunk.remove(scheduled.chunkLong());
            enqueueCandidate(state, scheduled.chunkLong());
        }
        compactScheduledQueueIfNeeded(state.retryQueue, state.retryDueByChunk);
    }

    void scheduleUnloadIfSent(PlayerViewState state, long packed) {
        if (!state.sent.contains(packed)
                || state.unloadDueByChunk.getOrDefault(packed, Integer.MIN_VALUE) != Integer.MIN_VALUE) {
            return;
        }
        int dueTick = ticks + config.unloadGraceTicks();
        state.unloadDueByChunk.put(packed, dueTick);
        state.unloadQueue.add(new ScheduledChunk(packed, dueTick));
    }

    static void compactScheduledQueueIfNeeded(
            PriorityQueue<ScheduledChunk> queue,
            Long2IntOpenHashMap liveDueTicks) {
        if (queue.size() <= liveDueTicks.size() * 4 + 1024) {
            return;
        }
        queue.removeIf(scheduled -> liveDueTicks.getOrDefault(
                scheduled.chunkLong(), Integer.MIN_VALUE) != scheduled.dueTick());
    }

}
