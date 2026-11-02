package com.silver.aipets.fabric.ai;

import java.time.Instant;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetIntentionStateTest {
    private static final Instant NOW = Instant.parse("2026-09-27T00:00:00Z");

    @Test void continuesUntilReconsiderationAndRejectsStaleVersion() {
        var watching = new PetIntentionState(PetIntent.LOOK_AT_OWNER,
                PetIntentionState.Reason.OWNER_NEAR, null, null, null,
                NOW, NOW.plusSeconds(15), NOW.plusSeconds(40), 7);
        assertTrue(watching.active(NOW.plusSeconds(10)));
        assertFalse(watching.canReconsider(NOW.plusSeconds(10)));
        assertTrue(watching.accepts(7, NOW.plusSeconds(10)));
        assertFalse(watching.accepts(6, NOW.plusSeconds(10)));
        assertFalse(watching.accepts(7, NOW.plusSeconds(40)));
        assertTrue(watching.resumable(NOW.plusSeconds(10)));
    }

    @Test void localPathsAndEventReactionsDoNotResumeAcrossAbsenceOrTransfer() {
        var path = new PetIntentionState(PetIntent.EXPLORE_NEARBY,
                PetIntentionState.Reason.CURIOSITY, new BlockPos(3, 64, 2),
                null, null, NOW, NOW.plusSeconds(5), NOW.plusSeconds(8), 2);
        var event = new PetIntentionState(PetIntent.LOOK_AT_OWNER,
                PetIntentionState.Reason.WITNESSED_EVENT, null, UUID.randomUUID(),
                null, NOW, NOW.plusSeconds(5), NOW.plusSeconds(20), 3);
        assertFalse(path.resumable(NOW.plusSeconds(1)));
        assertFalse(event.resumable(NOW.plusSeconds(1)));
        assertFalse(path.active(NOW.plusSeconds(9)));
        assertThrows(IllegalArgumentException.class, () -> new PetIntentionState(
                PetIntent.LOOK_AT_OWNER, PetIntentionState.Reason.STANCE,
                new BlockPos(100, 64, 100), null, null, NOW, NOW, NOW.plusSeconds(5), 1));
    }
}
