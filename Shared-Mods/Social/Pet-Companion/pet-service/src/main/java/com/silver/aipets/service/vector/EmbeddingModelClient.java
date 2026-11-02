package com.silver.aipets.service.vector;

/** Bounded provider client used for both compact cards and deterministic search text. */
public interface EmbeddingModelClient {
    String model();

    EmbeddingVector embed(String normalizedText);

    /** Optional usage-aware path; legacy implementations remain source compatible. */
    default EmbeddingModelResponse embedWithUsage(String normalizedText) {
        return new EmbeddingModelResponse(
                embed(normalizedText), java.util.Optional.empty(), 0,
                java.math.BigDecimal.ZERO, 0);
    }
}
