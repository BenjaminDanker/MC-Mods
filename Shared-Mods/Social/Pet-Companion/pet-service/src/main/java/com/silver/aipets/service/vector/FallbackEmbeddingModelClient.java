package com.silver.aipets.service.vector;

import com.silver.aipets.service.provider.AiFallbackReporter;

import java.util.Objects;
import java.util.Optional;

/**
 * Tries the primary embedding provider and, if it is unavailable, retries once on a fallback that
 * serves the same model, so vectors stay interchangeable in the existing index. Every fallback is
 * reported through {@link AiFallbackReporter}; a billed fallback call is marked in {@code ai_usage}.
 */
public final class FallbackEmbeddingModelClient implements EmbeddingModelClient {
    private final EmbeddingModelClient primary;
    private final EmbeddingModelClient fallback;
    private final AiFallbackReporter reporter;

    public FallbackEmbeddingModelClient(
            EmbeddingModelClient primary, EmbeddingModelClient fallback, AiFallbackReporter reporter) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.reporter = Objects.requireNonNull(reporter, "reporter");
        if (!primary.model().equals(fallback.model())) {
            throw new IllegalArgumentException("Fallback embeddings must carry the primary model name");
        }
    }

    @Override
    public String model() {
        return primary.model();
    }

    @Override
    public EmbeddingVector embed(String normalizedText) {
        return embedWithUsage(normalizedText).embedding();
    }

    @Override
    public EmbeddingModelResponse embedWithUsage(String normalizedText) {
        try {
            return primary.embedWithUsage(normalizedText);
        } catch (VectorStoreUnavailableException primaryFailure) {
            String reason = primaryFailure.getClass().getSimpleName() + ": "
                    + (primaryFailure.getMessage() == null ? "" : primaryFailure.getMessage());
            try {
                EmbeddingModelResponse response = fallback.embedWithUsage(normalizedText);
                reporter.fallbackUsed(AiFallbackReporter.Operation.EMBEDDING, primary.model(), reason, Optional.empty());
                return response;
            } catch (VectorStoreUnavailableException fallbackFailure) {
                reporter.fallbackFailed(AiFallbackReporter.Operation.EMBEDDING, primary.model(),
                        reason + " | fallback: " + fallbackFailure.getMessage(), Optional.empty());
                primaryFailure.addSuppressed(fallbackFailure);
                throw primaryFailure;
            }
        }
    }
}
