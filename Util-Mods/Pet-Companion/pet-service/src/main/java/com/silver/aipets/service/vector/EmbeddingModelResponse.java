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
        long latencyMillis,
        String provider) {
    /** A response from the primary provider (OpenAI). */
    public EmbeddingModelResponse(EmbeddingVector embedding, Optional<String> providerId, int inputTokens,
            BigDecimal estimatedCost, long latencyMillis) {
        this(embedding, providerId, inputTokens, estimatedCost, latencyMillis, "openai");
    }

    public boolean servedByFallback() { return !provider.equals("openai"); }

    /** The {@code ai_usage.model} value: marked when the fallback provider served the call. */
    public String usageModel() {
        String model = embedding.model();
        String name = servedByFallback()
                ? com.silver.aipets.service.provider.AiFallbackReporter.LEDGER_PREFIX + model : model;
        return name.length() <= 191 ? name : name.substring(0, 191);
    }

    public EmbeddingModelResponse {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(embedding, "embedding");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(estimatedCost, "estimatedCost");
        if (inputTokens < 0 || estimatedCost.signum() < 0 || latencyMillis < 0) {
            throw new IllegalArgumentException("Invalid embedding usage");
        }
    }
}
