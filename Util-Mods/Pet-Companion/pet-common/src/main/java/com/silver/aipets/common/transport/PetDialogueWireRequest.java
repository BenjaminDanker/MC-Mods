package com.silver.aipets.common.transport;

import com.silver.aipets.common.domain.BackendId;

import java.util.Objects;
import java.util.UUID;

public record PetDialogueWireRequest(
        UUID requestId,
        UUID sessionId,
        UUID ownerUuid,
        UUID petId,
        BackendId backendId,
        String dimensionId,
        String message) {
    public PetDialogueWireRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(backendId, "backendId");
        dimensionId = bounded(dimensionId, 191, "dimensionId");
        message = bounded(message, 500, "message");
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " is outside bounds");
        }
        return normalized;
    }
}
