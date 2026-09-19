package com.silver.viewextend;

import java.util.PriorityQueue;
import java.util.function.LongConsumer;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.world.level.ChunkPos;
import com.silver.viewextend.PlayerViewState.ScheduledChunk;
import com.silver.viewextend.PlayerViewState.LineDemand;
import static com.silver.viewextend.ViewExtendGeometry.*;

/** Coverage, retries and expiry only. Does not read worlds or send packets. */
final class PlayerViewPlanner {
    private static final int IMMEDIATE_EXTENSION_CHUNKS = 2;
    private static final int NEAR_EXTENSION_CHUNKS = 16;
    private static final int MAX_DIRECT_DEMAND = 256;
    private static final int MIN_GAP_DESCRIPTOR_LIMIT = 32;
    enum DemandSource { DIRECT_RETRY, NEAR, BOOTSTRAP, MOVEMENT, VANILLA_GAP }
    private long selectionComparisons;
    private long directRetryComparisons;
    private long directRetryPruned;
    private long movementDescriptorComparisons;
    private long nearCursorChecks;
    private long bootstrapCursorChecks;
    private long gapCursorChecks;
    private long descriptorHeadRebuilds;
    private long descriptorHeadRekeys;
    private long descriptorDeduplications;
    private long descriptorMerges;
    private final ViewExtendConfig config;
    int ticks;

    PlayerViewPlanner(ViewExtendConfig config) { this.config = config; }

    void updateVanillaView(PlayerViewState state, net.minecraft.server.level.ChunkTrackingView current) {
        // Capture only the boundary chunks that actually leave vanilla ownership. This is
        // O(view perimeter), unlike restarting the possible-gap radial sweep.
        if (state.initialized()
                && state.vanillaView != net.minecraft.server.level.ChunkTrackingView.EMPTY) {
            net.minecraft.server.level.ChunkTrackingView.difference(state.vanillaView, current,
                    ignored -> {}, pos -> {
                        if (withinDistance(pos.x(), pos.z(), state.centerX, state.centerZ,
                                state.totalDistance)) {
                            addGapPointIfAbsent(state, pos.x(), pos.z());
                        }
                    });
        }
        int gapDescriptorLimit = Math.max(MIN_GAP_DESCRIPTOR_LIMIT, state.normalDistance * 8);
        if (state.vanillaGapDemand.size() > gapDescriptorLimit) {
            state.vanillaGapDemand.clear();
            state.descriptorHeadsDirty = true;
            resetGapDemand(state);
        }
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
            resetDemandEpoch(state, centerX, centerZ, normalDistance, totalDistance);
            resetGapDemand(state);
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

        // Prune only the departed strip when the frames overlap, not the whole retry map.
        if (oldTotalDistance == totalDistance
                && squaresOverlap(oldCenterX, oldCenterZ, oldTotalDistance,
                        centerX, centerZ, totalDistance)) {
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
        boolean distancesChanged = oldNormalDistance != normalDistance || oldTotalDistance != totalDistance;

        state.centerX = centerX;
        state.centerZ = centerZ;
        state.normalDistance = normalDistance;
        state.totalDistance = totalDistance;
        state.cachedNearHead = null;
        state.cachedBootstrapHead = null;
        state.cachedGapHead = null;

        forEachSquareDifference(centerX, centerZ, totalDistance,
                oldCenterX, oldCenterZ, oldTotalDistance, packed -> {
                    state.sent.remove(packed);
                    state.sentLodByChunk.remove(packed);
                });

        boolean epochStillUseful = hasMeaningfulOverlap(state.bootstrapCenterX,
                state.bootstrapCenterZ, state.bootstrapTotalDistance,
                centerX, centerZ, totalDistance);
        if (distancesChanged || !epochStillUseful) {
            directRetryPruned += state.directDemand.size();
            state.directDemand.clear();
            state.directDemandSet.clear();
            state.directDemandAge.clear();
            state.movementDemand.clear();
            state.vanillaGapDemand.clear();
            state.descriptorHeadsDirty = true;
            // The following updateVanillaView call installs the new frame without
            // enumerating an unrelated old view after teleport/configuration reset.
            state.vanillaView = net.minecraft.server.level.ChunkTrackingView.EMPTY;
            resetDemandEpoch(state, centerX, centerZ, normalDistance, totalDistance);
            resetGapDemand(state);
            reconcileTrackedChunks(state, centerX, centerZ, normalDistance, totalDistance);
            return;
        }

        // Once every descriptor in an epoch has been admitted, the old player frame is a
        // complete coverage anchor. Advance that anchor without restarting either cursor.
        if (!state.nearActive && !state.bootstrapActive && state.movementDemand.isEmpty()
                && state.vanillaGapDemand.isEmpty()) {
            state.bootstrapCenterX = oldCenterX;
            state.bootstrapCenterZ = oldCenterZ;
            state.bootstrapTotalDistance = oldTotalDistance;
        }

        // The epoch cursors remain anchored and monotonic. Preserve unfinished movement
        // descriptors, discard only descriptors wholly outside the new frame, and append
        // compact differences for the newly exposed near and outer boundaries.
        state.movementDemand.removeIf(descriptor -> descriptor.exhausted()
                || !descriptor.intersectsSquare(centerX, centerZ, totalDistance));
        state.vanillaGapDemand.removeIf(descriptor -> descriptor.exhausted()
                || !descriptor.intersectsSquare(centerX, centerZ, totalDistance));
        // Every cached distance key is relative to the old player center.
        state.descriptorHeadsDirty = true;
        int nearDistance = Math.min(totalDistance, normalDistance + NEAR_EXTENSION_CHUNKS);
        int oldNearDistance = Math.min(oldTotalDistance, oldNormalDistance + NEAR_EXTENSION_CHUNKS);
        addSquareDifferenceDescriptors(state, centerX, centerZ, nearDistance,
                oldCenterX, oldCenterZ, oldNearDistance);
        addSquareDifferenceDescriptors(state, centerX, centerZ, totalDistance,
                oldCenterX, oldCenterZ, oldTotalDistance);

        int oldUnloadDistance = oldTotalDistance + config.unloadBufferChunks();
        int newUnloadDistance = totalDistance + config.unloadBufferChunks();

        LongConsumer restoreIfReturning = packed -> {
            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (withinDistance(x, z, centerX, centerZ, totalDistance)) {
                if (state.unloadDueByChunk.remove(packed) != Integer.MIN_VALUE) {
                    // The client may have evicted this during grace; force a replacement packet.
                    state.sent.remove(packed);
                    state.sentLodByChunk.remove(packed);
                }
            }
        };
        forEachSquareDifference(
                centerX, centerZ, totalDistance,
                oldCenterX, oldCenterZ, oldTotalDistance,
                restoreIfReturning);

        int lod0Radius = config.lod1StartDistance() - 1;
        if (lod0Radius < totalDistance) {
            addSquareDifferenceDescriptors(state, centerX, centerZ, lod0Radius,
                    oldCenterX, oldCenterZ, lod0Radius);
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
        state.bootstrapRadius = Math.max(normalDistance + 1,
                Math.min(totalDistance, normalDistance + NEAR_EXTENSION_CHUNKS) + 1);
        state.bootstrapOffset = 0;
        state.bootstrapTotalDistance = totalDistance;
        state.bootstrapActive = state.bootstrapRadius <= totalDistance;
        state.bootstrapAge = ++state.nextDemandAge;
        state.cachedBootstrapHead = null;
    }

    private void resetDemandEpoch(PlayerViewState state, int centerX, int centerZ,
            int normalDistance, int totalDistance) {
        resetNearDemand(state);
        resetBootstrap(state, centerX, centerZ, normalDistance, totalDistance);
    }

    private static void normalizeBootstrap(PlayerViewState state) {
        while (state.bootstrapActive) {
            if (state.bootstrapRadius > state.bootstrapTotalDistance) {
                state.bootstrapActive = false;
                state.cachedBootstrapHead = null;
                return;
            }
            int perimeter = ViewExtendGeometry.ringPerimeter(state.bootstrapRadius);
            if (state.bootstrapOffset >= perimeter) {
                state.bootstrapRadius++;
                state.bootstrapOffset = 0;
                state.cachedBootstrapHead = null;
                continue;
            }
            return;
        }
    }

    static long nextBootstrapCandidate(PlayerViewState state) {
        normalizeBootstrap(state);
        if (state.bootstrapActive) {
            long packed = ViewExtendGeometry.ringChunk(state.bootstrapCenterX, state.bootstrapCenterZ,
                    state.bootstrapRadius, state.bootstrapOffset++);
            state.cachedBootstrapHead = null;
            normalizeBootstrap(state);
            return packed;
        }
        return Long.MIN_VALUE;
    }

    void enqueueCandidate(PlayerViewState state, long packed) {
        if (state.directDemandSet.contains(packed)) {
            return;
        }
        if (state.directDemand.size() >= MAX_DIRECT_DEMAND) {
            // Recover immediately with a centered lazy sweep rather than waiting for stale work.
            directRetryPruned += state.directDemand.size() + 1L;
            state.directDemand.clear();
            state.directDemandSet.clear();
            state.directDemandAge.clear();
            resetDemandEpoch(state, state.centerX, state.centerZ,
                    state.normalDistance, state.totalDistance);
            return;
        }
        state.directDemandSet.add(packed);
        state.directDemand.add(packed);
        state.directDemandAge.put(packed, ++state.nextDemandAge);
    }

    void resetNearDemand(PlayerViewState state) {
        state.nearCenterX = state.centerX;
        state.nearCenterZ = state.centerZ;
        state.nearRadius = Math.max(1, state.normalDistance + 1);
        state.nearOffset = 0;
        state.nearMaxRadius = Math.min(state.totalDistance, state.normalDistance + NEAR_EXTENSION_CHUNKS);
        state.nearActive = state.nearRadius <= state.nearMaxRadius;
        state.nearAge = ++state.nextDemandAge;
        state.cachedNearHead = null;
    }

    void resetGapDemand(PlayerViewState state) {
        state.gapCenterX = state.centerX;
        state.gapCenterZ = state.centerZ;
        state.gapRadius = firstPossibleVanillaGapRadius(state.normalDistance);
        state.gapOffset = 0;
        state.gapMaxRadius = Math.min(state.normalDistance, state.totalDistance);
        state.gapActive = state.gapRadius <= state.gapMaxRadius;
        state.gapAge = ++state.nextDemandAge;
        state.cachedGapHead = null;
    }

    private static int firstPossibleVanillaGapRadius(int normalDistance) {
        long radiusSquared = (long) normalDistance * normalDistance;
        for (int radius = 1; radius <= normalDistance; radius++) {
            long reduced = Math.max(0, radius - 2);
            if (2L * reduced * reduced >= radiusSquared) return radius;
        }
        return normalDistance + 1;
    }

    private static void normalizeNear(PlayerViewState state) {
        while (state.nearActive) {
            if (state.nearRadius > state.nearMaxRadius) {
                state.nearActive = false;
                state.cachedNearHead = null;
                return;
            }
            int perimeter = ViewExtendGeometry.ringPerimeter(state.nearRadius);
            if (state.nearOffset >= perimeter) {
                state.nearRadius++;
                state.nearOffset = 0;
                state.cachedNearHead = null;
                continue;
            }
            return;
        }
    }

    private static void normalizeGap(PlayerViewState state) {
        while (state.gapActive) {
            if (state.gapRadius > state.gapMaxRadius) {
                state.gapActive = false;
                state.cachedGapHead = null;
                return;
            }
            int perimeter = ViewExtendGeometry.ringPerimeter(state.gapRadius);
            if (state.gapOffset >= perimeter) {
                state.gapRadius++;
                state.gapOffset = 0;
                state.cachedGapHead = null;
                continue;
            }
            return;
        }
    }

    DemandCandidate peekBestCandidate(PlayerViewState state) {
        normalizeNear(state);
        normalizeGap(state);
        normalizeBootstrap(state);
        ensureDescriptorHeads(state);
        DemandCandidate best = null;

        for (int i = 0; i < state.directDemand.size(); i++) {
            long packed = state.directDemand.getLong(i);
            directRetryComparisons++;
            best = better(best, candidate(state, packed, state.directDemandAge.get(packed),
                    DemandSource.DIRECT_RETRY, null));
        }
        if (state.nearActive) {
            nearCursorChecks++;
            if (state.cachedNearHead == null) {
                long packed = ringChunk(state.nearCenterX, state.nearCenterZ,
                        state.nearRadius, state.nearOffset);
                state.cachedNearHead = candidate(state, packed, state.nearAge,
                        DemandSource.NEAR, null);
            }
            best = better(best, state.cachedNearHead);
        }
        if (state.gapActive) {
            gapCursorChecks++;
            if (state.cachedGapHead == null) {
                long packed = ringChunk(state.gapCenterX, state.gapCenterZ,
                        state.gapRadius, state.gapOffset);
                state.cachedGapHead = candidate(state, packed, state.gapAge,
                        DemandSource.VANILLA_GAP, null);
            }
            best = better(best, state.cachedGapHead);
        }
        if (!state.vanillaGapHeads.isEmpty()) {
            gapCursorChecks++;
            best = better(best, state.vanillaGapHeads.peek());
        }
        if (state.bootstrapActive) {
            bootstrapCursorChecks++;
            if (state.cachedBootstrapHead == null) {
                long packed = ringChunk(state.bootstrapCenterX, state.bootstrapCenterZ,
                        state.bootstrapRadius, state.bootstrapOffset);
                state.cachedBootstrapHead = candidate(state, packed, state.bootstrapAge,
                        DemandSource.BOOTSTRAP, null);
            }
            best = better(best, state.cachedBootstrapHead);
        }
        if (!state.movementHeads.isEmpty()) {
            best = better(best, state.movementHeads.peek());
        }
        return best;
    }

    void consumeCandidate(PlayerViewState state, DemandCandidate candidate) {
        switch (candidate.source()) {
            case DIRECT_RETRY -> {
                state.directDemand.rem(candidate.packed());
                state.directDemandSet.remove(candidate.packed());
                state.directDemandAge.remove(candidate.packed());
            }
            case NEAR -> {
                state.nearOffset++;
                state.cachedNearHead = null;
                normalizeNear(state);
            }
            case BOOTSTRAP -> {
                state.bootstrapOffset++;
                state.cachedBootstrapHead = null;
                normalizeBootstrap(state);
            }
            case MOVEMENT -> consumeDescriptorHead(state, candidate, state.movementHeads,
                    state.movementDemand, DemandSource.MOVEMENT);
            case VANILLA_GAP -> {
                if (candidate.line() == null) {
                    state.gapOffset++;
                    state.cachedGapHead = null;
                    normalizeGap(state);
                } else {
                    consumeDescriptorHead(state, candidate, state.vanillaGapHeads,
                            state.vanillaGapDemand, DemandSource.VANILLA_GAP);
                }
            }
            default -> throw new IllegalStateException("Unknown demand source " + candidate.source());
        }
    }

    private void ensureDescriptorHeads(PlayerViewState state) {
        if (state.movementHeads == null) {
            state.movementHeads = new PriorityQueue<>(this::compareMovementHeads);
            state.vanillaGapHeads = new PriorityQueue<>(this::compareGapHeads);
            state.descriptorHeadsDirty = true;
        }
        if (!state.descriptorHeadsDirty) return;

        state.movementHeads.clear();
        state.vanillaGapHeads.clear();
        state.movementDemand.removeIf(LineDemand::exhausted);
        state.vanillaGapDemand.removeIf(LineDemand::exhausted);
        for (LineDemand descriptor : state.movementDemand) {
            state.movementHeads.add(candidate(state, descriptor.peek(), descriptor.age,
                    DemandSource.MOVEMENT, descriptor));
            descriptorHeadRekeys++;
        }
        for (LineDemand descriptor : state.vanillaGapDemand) {
            state.vanillaGapHeads.add(candidate(state, descriptor.peek(), descriptor.age,
                    DemandSource.VANILLA_GAP, descriptor));
            descriptorHeadRekeys++;
        }
        state.descriptorHeadsDirty = false;
        descriptorHeadRebuilds++;
    }

    private int compareMovementHeads(DemandCandidate left, DemandCandidate right) {
        selectionComparisons++;
        movementDescriptorComparisons++;
        return comparePriority(left.tier(), left.distance(), left.age(),
                right.tier(), right.distance(), right.age());
    }

    private int compareGapHeads(DemandCandidate left, DemandCandidate right) {
        selectionComparisons++;
        gapCursorChecks++;
        return comparePriority(left.tier(), left.distance(), left.age(),
                right.tier(), right.distance(), right.age());
    }

    private void consumeDescriptorHead(PlayerViewState state, DemandCandidate selected,
            PriorityQueue<DemandCandidate> heads, java.util.ArrayDeque<LineDemand> descriptors,
            DemandSource source) {
        DemandCandidate indexed = heads.poll();
        if (indexed == null || indexed.line() != selected.line()
                || indexed.packed() != selected.packed()) {
            // Fail closed: the descriptor remains authoritative and the index is rebuilt
            // before the next choice. This should only be reachable after an invariant bug.
            state.descriptorHeadsDirty = true;
        }
        selected.line().advance();
        if (selected.line().exhausted()) {
            descriptors.remove(selected.line());
        } else if (!state.descriptorHeadsDirty) {
            heads.add(candidate(state, selected.line().peek(), selected.line().age,
                    source, selected.line()));
        }
    }

    private static DemandCandidate candidate(PlayerViewState state, long packed, long age,
            DemandSource source, LineDemand line) {
        int distance = Math.max(Math.abs(ChunkPos.getX(packed) - state.centerX),
                Math.abs(ChunkPos.getZ(packed) - state.centerZ));
        return new DemandCandidate(packed, priorityTier(state.normalDistance, distance), distance,
                age, source, line);
    }

    private DemandCandidate better(DemandCandidate left, DemandCandidate right) {
        if (left == null) return right;
        selectionComparisons++;
        int comparison = comparePriority(right.tier(), right.distance(), right.age(),
                left.tier(), left.distance(), left.age());
        return comparison < 0 ? right : left;
    }

    static int priorityTier(int normalDistance, int distance) {
        if (distance <= normalDistance + IMMEDIATE_EXTENSION_CHUNKS) return 0;
        if (distance <= normalDistance + NEAR_EXTENSION_CHUNKS) return 1;
        return 2;
    }

    static int comparePriority(int leftTier, int leftDistance, long leftAge,
            int rightTier, int rightDistance, long rightAge) {
        int comparison = Integer.compare(leftTier, rightTier);
        if (comparison == 0) comparison = Integer.compare(leftDistance, rightDistance);
        if (comparison == 0) comparison = Long.compare(leftAge, rightAge);
        return comparison;
    }

    record DemandCandidate(long packed, int tier, int distance, long age,
            DemandSource source, LineDemand line) {}

    SelectionMetrics drainSelectionMetrics() {
        SelectionMetrics result = new SelectionMetrics(selectionComparisons, directRetryComparisons,
                directRetryPruned,
                movementDescriptorComparisons, nearCursorChecks, bootstrapCursorChecks, gapCursorChecks,
                descriptorHeadRebuilds, descriptorHeadRekeys, descriptorDeduplications, descriptorMerges);
        selectionComparisons = directRetryComparisons = directRetryPruned = movementDescriptorComparisons = 0;
        nearCursorChecks = bootstrapCursorChecks = gapCursorChecks = 0;
        descriptorHeadRebuilds = descriptorHeadRekeys = descriptorDeduplications = descriptorMerges = 0;
        return result;
    }

    record SelectionMetrics(long comparisons, long directRetryComparisons, long directRetryPruned,
            long movementDescriptorComparisons, long nearCursorChecks,
            long bootstrapCursorChecks, long gapCursorChecks, long descriptorHeadRebuilds,
            long descriptorHeadRekeys, long descriptorDeduplications, long descriptorMerges) {}

    private void addSquareDifferenceDescriptors(PlayerViewState state,
            int includedCenterX, int includedCenterZ, int includedRadius,
            int excludedCenterX, int excludedCenterZ, int excludedRadius) {
        int minX = includedCenterX - includedRadius, maxX = includedCenterX + includedRadius;
        int minZ = includedCenterZ - includedRadius, maxZ = includedCenterZ + includedRadius;
        int oldMinX = excludedCenterX - excludedRadius, oldMaxX = excludedCenterX + excludedRadius;
        int oldMinZ = excludedCenterZ - excludedRadius, oldMaxZ = excludedCenterZ + excludedRadius;
        int overlapMinX = Math.max(minX, oldMinX), overlapMaxX = Math.min(maxX, oldMaxX);
        int overlapMinZ = Math.max(minZ, oldMinZ), overlapMaxZ = Math.min(maxZ, oldMaxZ);
        if (includedRadius < 0) return;
        if (overlapMinX > overlapMaxX || overlapMinZ > overlapMaxZ) {
            addVerticalLines(state, minX, maxX, minZ, maxZ, includedCenterZ);
            return;
        }
        addVerticalLines(state, minX, overlapMinX - 1, minZ, maxZ, includedCenterZ);
        addVerticalLines(state, overlapMaxX + 1, maxX, minZ, maxZ, includedCenterZ);
        addHorizontalLines(state, overlapMinX, overlapMaxX, minZ, overlapMinZ - 1, includedCenterX);
        addHorizontalLines(state, overlapMinX, overlapMaxX, overlapMaxZ + 1, maxZ, includedCenterX);
    }

    private void addVerticalLines(PlayerViewState state, int minX, int maxX,
            int minZ, int maxZ, int centerZ) {
        if (minZ > maxZ) return;
        for (int x = minX; x <= maxX; x++) {
            addLineIfAbsent(state, true, x, minZ, maxZ, centerZ);
        }
    }

    private void addHorizontalLines(PlayerViewState state, int minX, int maxX,
            int minZ, int maxZ, int centerX) {
        if (minX > maxX) return;
        for (int z = minZ; z <= maxZ; z++) {
            addLineIfAbsent(state, false, z, minX, maxX, centerX);
        }
    }

    void addLineIfAbsent(PlayerViewState state, boolean vertical, int fixed,
            int min, int max, int center) {
        for (LineDemand descriptor : state.movementDemand) {
            if (!descriptor.started() && !descriptor.exhausted()
                    && descriptor.vertical == vertical && descriptor.fixed == fixed
                    && descriptor.min <= min && descriptor.max >= max) {
                descriptorDeduplications++;
                return;
            }
        }
        state.movementDemand.addLast(new LineDemand(vertical, fixed, min, max, center,
                ++state.nextDemandAge));
        state.descriptorHeadsDirty = true;
    }

    private void addGapPointIfAbsent(PlayerViewState state, int x, int z) {
        for (LineDemand descriptor : state.vanillaGapDemand) {
            if (descriptor.isUntouchedEquivalent(true, x, z, z)) {
                descriptorDeduplications++;
                return;
            }
        }
        state.vanillaGapDemand.addLast(new LineDemand(true, x, z, z, z,
                ++state.nextDemandAge));
        state.descriptorHeadsDirty = true;
    }

    private static boolean squaresOverlap(int leftX, int leftZ, int leftRadius,
            int rightX, int rightZ, int rightRadius) {
        return Math.abs((long) leftX - rightX) <= (long) leftRadius + rightRadius
                && Math.abs((long) leftZ - rightZ) <= (long) leftRadius + rightRadius;
    }

    /** Keep an epoch while at least half of the current desired square overlaps it. */
    static boolean hasMeaningfulOverlap(int epochX, int epochZ, int epochRadius,
            int centerX, int centerZ, int radius) {
        long minX = Math.max((long) epochX - epochRadius, (long) centerX - radius);
        long maxX = Math.min((long) epochX + epochRadius, (long) centerX + radius);
        long minZ = Math.max((long) epochZ - epochRadius, (long) centerZ - radius);
        long maxZ = Math.min((long) epochZ + epochRadius, (long) centerZ + radius);
        if (minX > maxX || minZ > maxZ) return false;
        long overlap = (maxX - minX + 1) * (maxZ - minZ + 1);
        long side = 2L * radius + 1;
        return overlap * 2 >= side * side;
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
