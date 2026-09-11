package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueModelResponse;
import com.silver.aipets.service.dialogue.DialoguePrompt;
import com.silver.aipets.service.dialogue.OpenAiDialogueModelClient;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import com.silver.aipets.service.billing.AiPricing;

/** OpenAI structured-output adapter dedicated to sleep consolidation calls. */
public final class OpenAiConsolidationModelClient implements ConsolidationModelClient {
    private final OpenAiDialogueModelClient delegate;

    public OpenAiConsolidationModelClient(
            URI baseUri, String model, String apiKey, Duration timeout) {
        this(baseUri, model, apiKey, timeout, AiPricing.defaults());
    }

    public OpenAiConsolidationModelClient(
            URI baseUri, String model, String apiKey, Duration timeout, AiPricing pricing) {
        this.delegate = new OpenAiDialogueModelClient(
                baseUri, model, apiKey, timeout, "pet_consolidation", pricing);
    }

    @Override
    public String model() {
        return delegate.model();
    }

    @Override
    public DialogueModelResponse complete(
            UUID requestId, DialoguePrompt prompt, String requiredJsonSchema, Duration timeout) {
        Objects.requireNonNull(requestId, "requestId");
        return delegate.complete(requestId, prompt, requiredJsonSchema, timeout);
    }
}
