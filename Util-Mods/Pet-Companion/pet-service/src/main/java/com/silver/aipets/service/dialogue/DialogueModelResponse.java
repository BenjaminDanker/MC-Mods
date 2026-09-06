package com.silver.aipets.service.dialogue;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

public record DialogueModelResponse(
        String structuredJson,
        Optional<String> providerId,
        int inputTokens,
        int cachedInputTokens,
        int outputTokens,
        BigDecimal estimatedCost,
        long latencyMillis) {
    public DialogueModelResponse {
        Objects.requireNonNull(structuredJson, "structuredJson");
        Objects.requireNonNull(providerId, "providerId");
        if (inputTokens < 0 || cachedInputTokens < 0 || cachedInputTokens > inputTokens
                || outputTokens < 0 || latencyMillis < 0) {
            throw new IllegalArgumentException("Invalid model usage");
        }
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        if (estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost cannot be negative");
        }
    }
}
