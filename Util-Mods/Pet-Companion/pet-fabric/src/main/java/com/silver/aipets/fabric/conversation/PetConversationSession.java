package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable view of a short-lived, owner- and placed-entity-bound interaction session. */
public record PetConversationSession(
        UUID sessionId,
        UUID ownerUuid,
        UUID petId,
        UUID petEntityUuid,
        BackendId backendId,
        String dimensionId,
        Instant expiresAt,
        State state,
        Optional<UUID> correlationId) {
    public PetConversationSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(petEntityUuid, "petEntityUuid");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(correlationId, "correlationId");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        if ((state == State.SUBMITTING) != correlationId.isPresent()) {
            throw new IllegalArgumentException("Only a submitting session has a correlation ID");
        }
    }

    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    public enum State {
        OPEN,
        SUBMITTING
    }
}
