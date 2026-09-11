package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.service.http.PetDialogueHttpHandler;

import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

/**
 * Binds the authenticated wire request to authoritative context and the bounded dialogue core.
 * Provider/model selection remains inside the supplied {@link DialogueService}.
 */
public final class DialogueWireResponder implements PetDialogueHttpHandler.Responder {
    private final DialogueContextLoader contexts;
    private final DialogueService dialogue;
    private final Clock clock;

    public DialogueWireResponder(
            DialogueContextLoader contexts, DialogueService dialogue, Clock clock) {
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.dialogue = Objects.requireNonNull(dialogue, "dialogue");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public PetDialogueWireResult respond(PetDialogueWireRequest request) {
        Objects.requireNonNull(request, "request");
        Optional<DialogueContext> context = contexts.load(
                request.ownerUuid(),
                request.petId(),
                request.backendId().value(),
                request.dimensionId(),
                clock.instant(),
                request.message());
        if (context.isEmpty()) {
            return denied(request, "That pet is not available to this owner.");
        }
        DialogueResult result = dialogue.respond(
                request.requestId(), request.ownerUuid(), context.orElseThrow(), request.message());
        String message = result.playerMessage().strip();
        if (message.isEmpty()) {
            message = "Your pet could not reply right now.";
        }
        return result.status() == DialogueResultStatus.SUCCEEDED
                ? new PetDialogueWireResult(
                        request.requestId(), request.sessionId(), request.petId(),
                        PetDialogueWireResult.Status.SUCCEEDED, message)
                : denied(request, message);
    }

    private static PetDialogueWireResult denied(PetDialogueWireRequest request, String message) {
        return new PetDialogueWireResult(
                request.requestId(), request.sessionId(), request.petId(),
                PetDialogueWireResult.Status.DENIED, message);
    }
}
