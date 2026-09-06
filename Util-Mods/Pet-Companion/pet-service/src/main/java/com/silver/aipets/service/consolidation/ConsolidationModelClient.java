package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialoguePrompt;

import java.time.Duration;
import java.util.UUID;

public interface ConsolidationModelClient {
    String model();

    DialogueModelResponse complete(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout);
}
