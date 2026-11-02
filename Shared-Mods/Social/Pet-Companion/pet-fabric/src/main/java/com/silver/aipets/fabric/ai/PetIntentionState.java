package com.silver.aipets.fabric.ai;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

/** Bounded execution state. Identity and placement remain in the authority service. */
public record PetIntentionState(PetIntent intent, Reason reason, BlockPos target,
        UUID sourceEventId, UUID memoryId, Instant beganAt, Instant reconsiderAt,
        Instant expiresAt, long version) {
    public enum Reason { OWNER_NEAR, OWNER_MOVING, OWNER_INTERACTION, THREAT,
        CURIOSITY, REST, STANCE, WITNESSED_EVENT, RELEVANT_MEMORY }

    public PetIntentionState {
        Objects.requireNonNull(intent);
        Objects.requireNonNull(reason);
        Objects.requireNonNull(beganAt);
        Objects.requireNonNull(reconsiderAt);
        Objects.requireNonNull(expiresAt);
        if (version < 0 || reconsiderAt.isBefore(beganAt)
                || expiresAt.isBefore(reconsiderAt)
                || Duration.between(beganAt, expiresAt).compareTo(Duration.ofMinutes(3)) > 0)
            throw new IllegalArgumentException("Invalid intention lifetime");
        if (target != null && intent != PetIntent.EXPLORE_NEARBY
                && intent != PetIntent.LOOK_AT_INTEREST)
            throw new IllegalArgumentException("Intent cannot use a local target");
    }

    public boolean active(Instant now) { return now.isBefore(expiresAt); }
    public boolean canReconsider(Instant now) { return !now.isBefore(reconsiderAt); }
    public boolean accepts(long expectedVersion, Instant now) {
        return version == expectedVersion && active(now);
    }
    /** A server restart can resume a quiet local posture, never a stale path or reaction. */
    public boolean resumable(Instant now) {
        return active(now) && target == null && sourceEventId == null
                && (intent == PetIntent.LOOK_AT_OWNER || intent == PetIntent.SIT
                        || intent == PetIntent.REST || intent == PetIntent.PAUSE);
    }
}
