package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.service.http.PetDialogueHttpHandler;

import java.util.Objects;

/** Explicit safe fallback while no production model/safety adapter is installed. */
public final class UnavailableDialogueResponder implements PetDialogueHttpHandler.Responder {
    private final String message;

    public UnavailableDialogueResponder(String message) {
        this.message = Objects.requireNonNull(message, "message").strip();
        if (this.message.isEmpty() || this.message.length() > 2_000) {
            throw new IllegalArgumentException("message is outside bounds");
        }
    }

    @Override
    public PetDialogueWireResult respond(PetDialogueWireRequest request) {
        Objects.requireNonNull(request, "request");
        return new PetDialogueWireResult(
                request.requestId(), request.sessionId(), request.petId(),
                PetDialogueWireResult.Status.DENIED, message);
    }
}
