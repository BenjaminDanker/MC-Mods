package com.silver.aipets.service.dialogue;

import java.time.Duration;
import java.util.UUID;

/** Configurable provider boundary; implementation owns structured-output/schema transport. */
public interface DialogueModelClient {
    String model();

    DialogueModelResponse complete(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout);

    /** The implementation remains non-streaming; callers may cancel while awaiting moderation. */
    default java.util.concurrent.CompletableFuture<DialogueModelResponse> completeAsync(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout,
            java.util.concurrent.Executor executor) {
        return java.util.concurrent.CompletableFuture.supplyAsync(
                () -> complete(requestId, prompt, requiredJsonSchema, timeout), executor);
    }
}
