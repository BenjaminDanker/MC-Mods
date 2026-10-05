package com.silver.aipets.fabric.conversation;

import java.util.Objects;
import java.util.UUID;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetDialogueAttention;

/** Service response repeats routing identities so a late/misrouted result is safely ignored. */
public record PetDialogueResponse(
        UUID requestId,
        UUID sessionId,
        UUID petId,
        Status status,
        String message,
        PetDialogueAction action,
        PetDialogueAttention attention) {
    public PetDialogueResponse(UUID requestId, UUID sessionId, UUID petId, Status status, String message) {
        this(requestId, sessionId, petId, status, message,
                PetDialogueAction.NONE, PetDialogueAttention.QUIET);
    }
    public PetDialogueResponse {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(attention, "attention");
        Objects.requireNonNull(message, "message");
        if (message.isBlank()) throw new IllegalArgumentException("message must not be blank");
    }

    public enum Status {
        SUCCEEDED,
        DENIED
    }
}
