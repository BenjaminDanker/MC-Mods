package com.silver.aipets.fabric.conversation;

import java.util.concurrent.CompletionStage;

/** Async central-service boundary. Implementations must never run model work on the server tick. */
@FunctionalInterface
public interface PetDialogueGateway {
    CompletionStage<PetDialogueResponse> submit(PetDialogueRequest request);
}
