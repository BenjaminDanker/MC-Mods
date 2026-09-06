package com.silver.aipets.fabric.conversation;

import com.silver.aipets.common.domain.BackendId;

import java.util.Objects;
import java.util.UUID;

/** Correlated request sent only after all local and authoritative submission checks pass. */
public record PetDialogueRequest(
        UUID requestId,
        UUID sessionId,
        UUID ownerUuid,
        UUID petId,
        UUID petEntityUuid,
        BackendId backendId,
        String dimensionId,
        String message) {
    public PetDialogueRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(petEntityUuid, "petEntityUuid");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(message, "message");
        if (dimensionId.isBlank() || message.isBlank()) {
            throw new IllegalArgumentException("dimensionId and message must not be blank");
        }
    }
}
