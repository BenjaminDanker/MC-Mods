package com.silver.aipets.fabric.conversation;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Explicit startup fallback until the central dialogue HTTP adapter is selected.
 * Staging mode is deterministic and never calls an external model or exports chat.
 */
public final class ConfiguredPetDialogueGateway implements PetDialogueGateway {
    public enum Mode {
        DISABLED,
        STAGING
    }

    private final Mode mode;

    public ConfiguredPetDialogueGateway(Mode mode) {
        this.mode = Objects.requireNonNull(mode, "mode");
    }

    @Override
    public CompletionStage<PetDialogueResponse> submit(PetDialogueRequest request) {
        Objects.requireNonNull(request, "request");
        if (mode == Mode.STAGING) {
            String input = request.message();
            String reply = "Staging pet reply: I heard you say \""
                    + input.substring(0, Math.min(input.length(), 240)) + "\".";
            return CompletableFuture.completedFuture(new PetDialogueResponse(
                    request.requestId(), request.sessionId(), request.petId(),
                    PetDialogueResponse.Status.SUCCEEDED, reply));
        }
        return CompletableFuture.completedFuture(new PetDialogueResponse(
                request.requestId(), request.sessionId(), request.petId(),
                PetDialogueResponse.Status.DENIED,
                "Pet dialogue transport is not configured yet."));
    }
}
