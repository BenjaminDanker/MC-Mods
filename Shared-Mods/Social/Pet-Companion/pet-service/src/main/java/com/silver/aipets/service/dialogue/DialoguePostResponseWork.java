package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Required asynchronous work chained after the durable immediate dialogue commit. */
@FunctionalInterface
public interface DialoguePostResponseWork {
    CompletionStage<Void> afterCommit(PetDialogueWireRequest request, PetDialogueWireResult result);

    static DialoguePostResponseWork none() {
        return (request, result) -> CompletableFuture.completedFuture(null);
    }
}
