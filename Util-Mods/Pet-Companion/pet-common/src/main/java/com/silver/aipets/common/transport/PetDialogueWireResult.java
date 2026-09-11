package com.silver.aipets.common.transport;

import java.util.Objects;
import java.util.UUID;

public record PetDialogueWireResult(
        UUID requestId,
        UUID sessionId,
        UUID petId,
        Status status,
        String message) {
    public PetDialogueWireResult {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(status, "status");
        message = Objects.requireNonNull(message, "message").strip();
        if (message.isEmpty() || message.length() > 2_000) {
            throw new IllegalArgumentException("message is outside bounds");
        }
    }

    public enum Status { SUCCEEDED, DENIED }
}
