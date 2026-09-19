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
        planner.scheduleRetry(state, old, 100);
        assertTrue(state.retryDueByChunk.isEmpty());
    }

    @Test void bootstrapCovers127IncludingRoundedVanillaCorners() {
        planner.updatePlayerView(state, 0, 0, 10, 127);
        Set<Long> chunks = new HashSet<>();
        for (long pos; (pos = PlayerViewPlanner.nextBootstrapCandidate(state)) != Long.MIN_VALUE;) {
            assertTrue(chunks.add(pos));
        }
        assertEquals(255 * 255 - 1, chunks.size());
        assertTrue(chunks.contains(ChunkPos.pack(10, 10)));
        assertTrue(chunks.contains(ChunkPos.pack(-127, 127)));
    }

    @Test void ordinaryMovementDoesNotRestartBootstrapAndStarveOuterRings() {
        planner.updatePlayerView(state, 0, 0, 10, 127);
        for (int i = 0; i < 4000; i++) PlayerViewPlanner.nextBootstrapCandidate(state);
        int radius = state.bootstrapRadius;
        int offset = state.bootstrapOffset;
        for (int x = 1; x < 30; x++) planner.updatePlayerView(state, x, 0, 10, 127);
        assertEquals(radius, state.bootstrapRadius);
        assertEquals(offset, state.bootstrapOffset);
        assertTrue(state.candidateOverflowed); // overflow causes a later recovery sweep, not lost work
    }

    @Test void returningDuringUnloadGraceForcesResend() {
        planner.updatePlayerView(state, 0, 0, 2, 12);
        long chunk = ChunkPos.pack(-12, 0);
        state.markSent(chunk, 0);
        planner.updatePlayerView(state, 4, 0, 2, 12);
        assertTrue(state.sent.contains(chunk));
        planner.updatePlayerView(state, 0, 0, 2, 12);
        assertFalse(state.sent.contains(chunk));
        assertTrue(state.candidateQueued.contains(chunk));
    }

    @Test void teleportCancelsOldPendingWorkAndRebuildsCoverage() {
        planner.updatePlayerView(state, 0, 0, 2, 12);
        long chunk = ChunkPos.pack(5, 5);
        long request = state.markPending(chunk, 0);
        planner.updatePlayerView(state, 100, 100, 2, 12);
        assertFalse(state.isCurrentRequest(state, chunk, request));
        assertEquals(100, state.bootstrapCenterX);
        assertEquals(1, state.bootstrapRadius);
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

    @Test void staleRetryDoesNotEnqueueAfterSuccess() {
        long chunk = ChunkPos.pack(50, 5);
        planner.scheduleRetry(state, chunk, 100);
        planner.scheduleRetry(state, chunk, 1);
        planner.ticks = 1;
        planner.drainDueRetries(state);
        assertEquals(1, state.candidateQueue.size());
        state.candidateQueue.clear();
        state.candidateQueued.clear();
        state.markSent(chunk, 0);
        planner.ticks = 101;
        planner.drainDueRetries(state);
        assertTrue(state.candidateQueue.isEmpty());
    }

    @Test void roundedVanillaBoundarySchedulesChunksEvenIfVanillaNeverSentThem() {
        var oldView = net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(0, 0), 10);
        var newView = net.minecraft.server.level.ChunkTrackingView.of(new ChunkPos(1, 0), 10);
        planner.updateVanillaView(state, oldView);
        planner.updateVanillaView(state, newView);
        int count = 0;
        for (int x = -9; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            if (oldView.contains(x, z) && !newView.contains(x, z)) {
                count++;
                assertTrue(state.candidateQueued.contains(ChunkPos.pack(x, z)));
            }
        }
        assertTrue(count > 0, "Must test a curved boundary inside the overlap of both squares");
    }
}
