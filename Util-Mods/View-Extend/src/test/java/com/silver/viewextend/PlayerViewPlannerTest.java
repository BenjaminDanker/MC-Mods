package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class PlayerViewPlannerTest {
    private final PlayerViewPlanner planner = new PlayerViewPlanner(ViewExtendConfig.fromProperties(new Properties()));
    private final PlayerViewState state = new PlayerViewState(UUID.randomUUID(), "minecraft:overworld");

    @Test void loginBootstrapEnumeratesNearToFarWithoutMaterializingTheView() {
        planner.updatePlayerView(state, -20, -30, 4, 60);
        int previous = 4;
        for (int i = 0; i < 500; i++) {
            var candidate = planner.peekBestCandidate(state);
            assertNotNull(candidate);
            assertTrue(candidate.distance() >= previous);
            previous = candidate.distance();
            planner.consumeCandidate(state, candidate);
        }
        assertEquals(2, state.demandDescriptorCount());
        assertTrue(state.directDemand.isEmpty());
        assertEquals(-20, state.bootstrapCenterX);
        assertEquals(-30, state.bootstrapCenterZ);
    }

    @Test void flightAndTeleportPruneDepartedRetries() {
        planner.updatePlayerView(state, 0, 0, 4, 32);
        long old = ChunkPos.pack(-32, 0), kept = ChunkPos.pack(0, 0);
        planner.scheduleRetry(state, old, 100);
        planner.scheduleRetry(state, kept, 100);
        planner.updatePlayerView(state, 1, 0, 4, 32);
        assertFalse(state.retryDueByChunk.containsKey(old));
        assertTrue(state.retryDueByChunk.containsKey(kept));
        planner.updatePlayerView(state, 1000, 0, 4, 32);
        assertTrue(state.retryDueByChunk.isEmpty());
        assertEquals(1000, state.bootstrapCenterX);
        assertTrue(state.movementDemand.isEmpty());
        planner.scheduleRetry(state, old, 100);
        assertTrue(state.retryDueByChunk.isEmpty());
    }

    @Test void bootstrapCovers127IncludingNegativeCoordinates() {
        planner.updatePlayerView(state, -17, -23, 10, 127);
        Set<Long> chunks = new HashSet<>();
        for (PlayerViewPlanner.DemandCandidate candidate;
                (candidate = planner.peekBestCandidate(state)) != null;) {
            if (candidate.distance() > 10) assertTrue(chunks.add(candidate.packed()));
            planner.consumeCandidate(state, candidate);
        }
        assertEquals(255 * 255 - 21 * 21, chunks.size());
        assertTrue(chunks.contains(ChunkPos.pack(-6, -12)));
        assertTrue(chunks.contains(ChunkPos.pack(-144, 104)));
    }

    @Test void walkingRepeatedlyKeepsDemandDescriptorBoundedAndBootstrapMonotonic() {
        planner.updatePlayerView(state, 0, 0, 4, 127);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(0, 0), 4));
        for (int i = 0; i < 4000; i++) PlayerViewPlanner.nextBootstrapCandidate(state);
        int radius = state.bootstrapRadius;
        int offset = state.bootstrapOffset;
        for (int x = 1; x <= 40; x++) {
            planner.updatePlayerView(state, x, 0, 4, 127);
            planner.updateVanillaView(state,
                    net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(x, 0), 4));
            assertTrue(state.movementDemand.size() <= 80);
            assertTrue(state.demandDescriptorCount() <= 120);
            assertTrue(state.directDemand.isEmpty());
        }
        assertEquals(radius, state.bootstrapRadius);
        assertEquals(offset, state.bootstrapOffset);
        assertEquals(0, state.bootstrapCenterX);
        assertEquals(0, planner.peekBestCandidate(state).tier());
    }

    @Test void diagonalMovementUsesCompactRectanglesWithoutCoordinateBacklog() {
        planner.updatePlayerView(state, -10, -10, 4, 60);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(-10, -10), 4));
        planner.updatePlayerView(state, -9, -9, 4, 60);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(-9, -9), 4));
        assertTrue(state.movementDemand.size() <= 4);
        assertTrue(state.directDemand.isEmpty());
        assertEquals(0, planner.peekBestCandidate(state).tier());
    }

    @Test void movementLineExposesItsClosestRemainingCoordinateFirst() {
        var line = new PlayerViewState.LineDemand(true, -50, -20, 20, -3, 1);
        int previous = -1;
        int count = 0;
        while (!line.exhausted()) {
            int variable = ChunkPos.getZ(line.peek());
            int distance = Math.abs(variable + 3);
            assertTrue(distance >= previous);
            previous = distance;
            count++;
            line.advance();
        }
        assertEquals(41, count);
    }

    @Test void returningDuringUnloadGraceForcesResendThroughLazyDemand() {
        planner.updatePlayerView(state, 0, 0, 2, 12);
        long chunk = ChunkPos.pack(-12, 0);
        state.markSent(chunk, 0);
        planner.updatePlayerView(state, 4, 0, 2, 12);
        assertTrue(state.sent.contains(chunk));
        planner.updatePlayerView(state, 0, 0, 2, 12);
        assertFalse(state.sent.contains(chunk));
        assertFalse(state.movementDemand.isEmpty());
    }

    @Test void teleportCancelsOldPendingWorkAndRebuildsCoverage() {
        planner.updatePlayerView(state, 0, 0, 2, 12);
        long chunk = ChunkPos.pack(5, 5);
        long request = state.markPending(chunk, 0);
        planner.updatePlayerView(state, 100, 100, 2, 12);
        assertFalse(state.isCurrentRequest(state, chunk, request));
        assertEquals(100, state.bootstrapCenterX);
        assertEquals(13, state.bootstrapRadius);
        assertTrue(state.nearActive);
    }

    @Test void vanillaViewTransitionCreatesImmediateNearDemand() {
        planner.updatePlayerView(state, 1, 0, 10, 32);
        state.bootstrapActive = false;
        var oldView = net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(0, 0), 10);
        var newView = net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(1, 0), 10);
        planner.updateVanillaView(state, oldView);
        planner.updateVanillaView(state, newView);
        assertTrue(state.nearActive);
        assertTrue(state.gapActive);
        PlayerViewPlanner.DemandCandidate gap = null;
        for (int i = 0; i < 100; i++) {
            var candidate = planner.peekBestCandidate(state);
            int x = ChunkPos.getX(candidate.packed());
            int z = ChunkPos.getZ(candidate.packed());
            if (!newView.contains(x, z)) {
                gap = candidate;
                break;
            }
            planner.consumeCandidate(state, candidate);
        }
        assertNotNull(gap);
        assertEquals(10, gap.distance());
        assertEquals(0, gap.tier());
    }

    @Test void distanceChangeRecentersBootstrapCleanly() {
        planner.updatePlayerView(state, 0, 0, 4, 32);
        for (int i = 0; i < 500; i++) PlayerViewPlanner.nextBootstrapCandidate(state);
        planner.updatePlayerView(state, 0, 0, 6, 40);
        assertEquals(23, state.bootstrapRadius);
        assertEquals(40, state.bootstrapTotalDistance);
        assertTrue(state.movementDemand.isEmpty());
    }

    @Test void newlyIntroducedNearWorkBeatsStaleFarCursorPriority() {
        planner.updatePlayerView(state, 0, 0, 4, 60);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(0, 0), 4));
        while (state.bootstrapRadius < 40) PlayerViewPlanner.nextBootstrapCandidate(state);
        planner.updatePlayerView(state, 1, 0, 4, 60);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(1, 0), 4));
        var candidate = planner.peekBestCandidate(state);
        assertTrue(candidate.distance() <= 6);
        assertEquals(0, candidate.tier());
    }

    @Test void dueRetryDoesNotPermanentlyFreezeFartherDemand() {
        planner.updatePlayerView(state, 0, 0, 4, 32);
        planner.scheduleRetry(state, ChunkPos.pack(5, 0), 1);
        planner.ticks = 1;
        planner.drainDueRetries(state);
        var first = planner.peekBestCandidate(state);
        assertNotNull(first);
        planner.consumeCandidate(state, first);
        assertNotNull(planner.peekBestCandidate(state));
    }

    @Test void lateCompletionCannotClearReplacementOrRespawnSession() {
        long chunk = ChunkPos.pack(40, 5);
        long old = state.markPending(chunk, 1);
        long replacement = state.markPending(chunk, 0);
        assertFalse(state.isCurrentRequest(state, chunk, old));
        assertTrue(state.isCurrentRequest(state, chunk, replacement));
        PlayerViewState respawn = new PlayerViewState(state.playerUuid, state.worldKey);
        long respawnRequest = respawn.markPending(chunk, 0);
        assertFalse(respawn.isCurrentRequest(state, chunk, respawnRequest));
        respawn.markSent(chunk, 0);
        assertFalse(respawn.isCurrentRequest(respawn, chunk, respawnRequest));
    }

    @Test void staleRetryDoesNotReappearAfterSuccess() {
        long chunk = ChunkPos.pack(50, 5);
        planner.scheduleRetry(state, chunk, 100);
        planner.scheduleRetry(state, chunk, 1);
        planner.ticks = 1;
        planner.drainDueRetries(state);
        assertEquals(1, state.directDemand.size());
        state.directDemand.clear();
        state.directDemandSet.clear();
        state.directDemandAge.clear();
        state.markSent(chunk, 0);
        planner.ticks = 101;
        planner.drainDueRetries(state);
        assertTrue(state.directDemand.isEmpty());
    }

    @Test void filledOneChunkMoveScalesWithBoundaryNotViewArea() {
        initializeAndDrain(0, 0, 4, 67);
        planner.updatePlayerView(state, 1, 0, 4, 67);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(1, 0), 4));

        assertFalse(state.nearActive, "normal movement must not restart the near cursor");
        assertFalse(state.bootstrapActive, "completed bootstrap must stay completed");
        int candidates = drainDemand(1_000);
        assertTrue(candidates >= 135, "the newly exposed outer boundary must remain represented");
        assertTrue(candidates < 300,
                "one step must be O(radius) boundary work, not an O(radius^2) near rescan");
    }

    @Test void continuousCardinalWalkingForHundredsOfChunksKeepsEachStepIncremental() {
        initializeAndDrain(-20, 7, 4, 67);
        int x = -20;
        int z = 7;
        int maximumCandidates = 0;
        for (int step = 0; step < 400; step++) {
            switch ((step / 100) & 3) {
                case 0 -> x++;
                case 1 -> z++;
                case 2 -> x--;
                default -> z--;
            }
            planner.updatePlayerView(state, x, z, 4, 67);
            planner.updateVanillaView(state,
                    net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(x, z), 4));
            maximumCandidates = Math.max(maximumCandidates, drainDemand(1_000));
            assertTrue(state.demandDescriptorCount() < 300);
        }
        assertTrue(maximumCandidates < 300);
    }

    @Test void rapidBackAndForthDoesNotResetEpochCursors() {
        planner.updatePlayerView(state, 0, 0, 4, 67);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(0, 0), 4));
        for (int i = 0; i < 250; i++) planner.consumeCandidate(state, planner.peekBestCandidate(state));
        int radius = state.bootstrapRadius;
        int offset = state.bootstrapOffset;

        for (int i = 0; i < 100; i++) {
            int x = (i & 1) == 0 ? 1 : 0;
            planner.updatePlayerView(state, x, 0, 4, 67);
            planner.updateVanillaView(state,
                    net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(x, 0), 4));
        }
        assertEquals(radius, state.bootstrapRadius);
        assertEquals(offset, state.bootstrapOffset);
        assertTrue(state.demandDescriptorCount() < 300);
    }

    @Test void movementWhileBootstrapIncompletePreservesMonotonicCursor() {
        planner.updatePlayerView(state, 0, 0, 4, 67);
        for (int i = 0; i < 500; i++) planner.consumeCandidate(state, planner.peekBestCandidate(state));
        int radius = state.bootstrapRadius;
        int offset = state.bootstrapOffset;
        planner.updatePlayerView(state, -1, 1, 4, 67);
        assertEquals(radius, state.bootstrapRadius);
        assertEquals(offset, state.bootstrapOffset);
        assertEquals(0, state.bootstrapCenterX);
        assertEquals(0, state.bootstrapCenterZ);
    }

    @Test void spatiallyDisjointRecenterStartsCleanEpoch() {
        planner.updatePlayerView(state, 0, 0, 4, 67);
        for (int i = 0; i < 500; i++) planner.consumeCandidate(state, planner.peekBestCandidate(state));
        planner.updatePlayerView(state, 500, -500, 4, 67);
        assertEquals(500, state.bootstrapCenterX);
        assertEquals(-500, state.bootstrapCenterZ);
        assertEquals(21, state.bootstrapRadius);
        assertEquals(5, state.nearRadius);
        assertTrue(state.movementDemand.isEmpty());
    }

    @Test void separateWorldStateStartsIndependentBootstrapEpoch() {
        planner.updatePlayerView(state, 20, 30, 4, 67);
        PlayerViewState other = new PlayerViewState(state.playerUuid, "minecraft:the_nether");
        planner.updatePlayerView(other, -40, -50, 4, 67);
        assertEquals(-40, other.bootstrapCenterX);
        assertEquals(-50, other.bootstrapCenterZ);
        assertTrue(other.nearActive);
        assertTrue(other.bootstrapActive);
    }

    @Test void lodTransitionMovementRemainsCompact() {
        Properties properties = new Properties();
        properties.setProperty("lod1-start-distance", "24");
        PlayerViewPlanner lodPlanner = new PlayerViewPlanner(ViewExtendConfig.fromProperties(properties));
        PlayerViewState lodState = new PlayerViewState(UUID.randomUUID(), "minecraft:overworld");
        lodPlanner.updatePlayerView(lodState, 0, 0, 4, 67);
        lodPlanner.updatePlayerView(lodState, 1, 1, 4, 67);
        assertTrue(lodState.movementDemand.size() <= 8);
        assertEquals(0, lodPlanner.peekBestCandidate(lodState).tier());
    }

    @Test void selectionMetricsSeparateDirectAndDescriptorWork() {
        planner.updatePlayerView(state, 0, 0, 4, 32);
        planner.scheduleRetry(state, ChunkPos.pack(5, 0), 1);
        planner.ticks = 1;
        planner.drainDueRetries(state);
        planner.peekBestCandidate(state);
        var metrics = planner.drainSelectionMetrics();
        assertTrue(metrics.comparisons() > 0);
        assertEquals(1, metrics.directRetryComparisons());
        assertTrue(metrics.nearCursorChecks() > 0);
        assertTrue(metrics.bootstrapCursorChecks() > 0);
        assertEquals(0, planner.drainSelectionMetrics().comparisons());
    }

    @Test void dozensOfMovementDescriptorsUseLogarithmicHeadSelection() {
        planner.updatePlayerView(state, 0, 0, 4, 100);
        state.nearActive = false;
        state.bootstrapActive = false;
        state.gapActive = false;
        state.movementDemand.clear();
        for (int x = -24; x < 24; x++) {
            state.movementDemand.addLast(new PlayerViewState.LineDemand(
                    true, x, -20, 20, 0, ++state.nextDemandAge));
        }
        state.descriptorHeadsDirty = true;
        planner.drainSelectionMetrics();

        for (int i = 0; i < 1_000; i++) {
            var candidate = planner.peekBestCandidate(state);
            assertNotNull(candidate);
            assertEquals(PlayerViewPlanner.DemandSource.MOVEMENT, candidate.source());
            planner.consumeCandidate(state, candidate);
        }

        var metrics = planner.drainSelectionMetrics();
        assertEquals(1, metrics.descriptorHeadRebuilds());
        assertEquals(48, metrics.descriptorHeadRekeys());
        assertTrue(metrics.movementDescriptorComparisons() < 15_000,
                "48 descriptors must not require 48 comparisons for every candidate");
        assertTrue(metrics.movementDescriptorComparisons() < 1_000L * 48 / 3);
    }

    @Test void cachedDescriptorHeadsAreRekeyedWhenPlayerCenterChanges() {
        planner.updatePlayerView(state, 0, 0, 4, 67);
        state.nearActive = false;
        state.bootstrapActive = false;
        state.gapActive = false;
        state.movementDemand.clear();
        var west = new PlayerViewState.LineDemand(true, -20, 0, 0, 0, ++state.nextDemandAge);
        var east = new PlayerViewState.LineDemand(true, 20, 0, 0, 0, ++state.nextDemandAge);
        state.movementDemand.add(west);
        state.movementDemand.add(east);
        state.descriptorHeadsDirty = true;
        assertSame(west, planner.peekBestCandidate(state).line());

        planner.updatePlayerView(state, 10, 0, 4, 67);
        state.nearActive = false;
        state.bootstrapActive = false;
        state.gapActive = false;
        state.movementDemand.removeIf(line -> line != west && line != east);
        state.descriptorHeadsDirty = true;
        assertSame(east, planner.peekBestCandidate(state).line());
        assertEquals(10, planner.peekBestCandidate(state).distance());
        assertTrue(planner.drainSelectionMetrics().descriptorHeadRebuilds() >= 2);
    }

    @Test void untouchedLineDescriptorsDeduplicateSafelyWithoutCoordinates() {
        planner.updatePlayerView(state, 0, 0, 4, 67);
        state.movementDemand.clear();
        planner.drainSelectionMetrics();

        planner.addLineIfAbsent(state, true, 30, -5, 0, 0);
        planner.addLineIfAbsent(state, true, 30, -5, 0, 0);
        planner.addLineIfAbsent(state, true, 30, -4, -1, 0);
        planner.addLineIfAbsent(state, true, 30, 1, 5, 0);

        assertEquals(2, state.movementDemand.size());
        PlayerViewState.LineDemand first = state.movementDemand.getFirst();
        assertEquals(-5, first.min);
        assertEquals(0, first.max);
        var metrics = planner.drainSelectionMetrics();
        assertEquals(2, metrics.descriptorDeduplications());
        assertEquals(0, metrics.descriptorMerges());
    }

    @Test void retryAndMovementHeadsCompeteWithUnchangedPriorityRules() {
        planner.updatePlayerView(state, -10, -10, 4, 67);
        planner.updatePlayerView(state, -9, -10, 4, 67);
        planner.scheduleRetry(state, ChunkPos.pack(-14, -10), 1);
        planner.ticks = 1;
        planner.drainDueRetries(state);
        state.nearActive = false;
        state.bootstrapActive = false;
        state.gapActive = false;
        var candidate = planner.peekBestCandidate(state);
        assertEquals(PlayerViewPlanner.DemandSource.DIRECT_RETRY, candidate.source());
        assertEquals(5, candidate.distance());
        assertEquals(0, candidate.tier());
        planner.consumeCandidate(state, candidate);
        assertNotNull(planner.peekBestCandidate(state));
    }

    private void initializeAndDrain(int x, int z, int normalDistance, int totalDistance) {
        planner.updatePlayerView(state, x, z, normalDistance, totalDistance);
        planner.updateVanillaView(state,
                net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(x, z), normalDistance));
        drainDemand((2 * totalDistance + 1) * (2 * totalDistance + 1) + 1_000);
    }

    private int drainDemand(int limit) {
        int count = 0;
        while (state.demandDescriptorCount() > 0 && count < limit) {
            PlayerViewPlanner.DemandCandidate candidate = planner.peekBestCandidate(state);
            assertNotNull(candidate);
            planner.consumeCandidate(state, candidate);
            count++;
        }
        assertTrue(count < limit, "lazy demand failed to drain within the safety bound");
        return count;
    }

    @Test void sentStateEstimateScalesWithPrimitiveBackingTables() {
        planner.updatePlayerView(state, 0, 0, 4, 32);
        for (int i = 0; i < 100; i++) state.markSent(ChunkPos.pack(i, -i), 0);
        for (int i = 0; i < 8; i++) state.markPending(ChunkPos.pack(i, i + 10), 0);
        var estimate = ViewExtendService.estimateStateMemory(state);
        assertEquals(100, estimate.sentEntries());
        assertEquals(100, estimate.sentLodEntries());
        assertEquals(8, estimate.pendingEntries());
        assertTrue(estimate.sentBytes() > 100L * (Long.BYTES + Integer.BYTES));
        assertTrue(estimate.schedulerBytes() > 0);
    }
}
