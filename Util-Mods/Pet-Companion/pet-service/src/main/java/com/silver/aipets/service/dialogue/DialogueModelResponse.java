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
        long latencyMillis,
        String finishReason,
        int configuredCompletionLimit,
        Optional<String> providerRequestId) {
    public DialogueModelResponse(String structuredJson, Optional<String> providerId,
            int inputTokens, int cachedInputTokens, int outputTokens, BigDecimal estimatedCost,
            long latencyMillis) {
        this(structuredJson, providerId, inputTokens, cachedInputTokens, outputTokens,
                estimatedCost, latencyMillis, "stop", 2000, providerId);
    }
    /** Chat Completions must finish normally before structured content is considered. */
    public boolean completedNormally() { return finishReason.equals("stop"); }

    public DialogueModelResponse {
        Objects.requireNonNull(structuredJson, "structuredJson");
        Objects.requireNonNull(providerId, "providerId");
        if (inputTokens < 0 || cachedInputTokens < 0 || cachedInputTokens > inputTokens
                || outputTokens < 0 || latencyMillis < 0) {
            throw new IllegalArgumentException("Invalid model usage");
        }
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        Objects.requireNonNull(finishReason, "finishReason");
        Objects.requireNonNull(providerRequestId, "providerRequestId");
        if (configuredCompletionLimit < 1) {
            throw new IllegalArgumentException("configuredCompletionLimit must be positive");
        }
        if (estimatedCost.signum() < 0) {
            throw new IllegalArgumentException("estimatedCost cannot be negative");
        }
    }
}
