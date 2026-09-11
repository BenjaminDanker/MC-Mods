package com.silver.aipets.service.vector;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/** Embedding vector plus provider usage, retained without changing old client implementations. */
public record EmbeddingModelResponse(
        EmbeddingVector embedding,
        Optional<String> providerId,
        int inputTokens,
        BigDecimal estimatedCost,
        long latencyMillis) {
    public EmbeddingModelResponse {
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        if (inputTokens < 0 || estimatedCost.signum() < 0 || latencyMillis < 0) {
            throw new IllegalArgumentException("Invalid embedding usage");
        }
    }
}
