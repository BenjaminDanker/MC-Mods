package com.silver.aipets.service.dialogue;

import java.time.Duration;
import java.util.UUID;

/** Configurable provider boundary; implementation owns structured-output/schema transport. */
public interface DialogueModelClient {
    String model();

    DialogueModelResponse complete(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout);
}
