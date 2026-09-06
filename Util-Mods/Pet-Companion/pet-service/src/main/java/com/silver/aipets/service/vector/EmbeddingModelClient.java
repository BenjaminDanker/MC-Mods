package com.silver.aipets.service.vector;

/** Bounded provider client used for both compact cards and deterministic search text. */
public interface EmbeddingModelClient {
    String model();

    EmbeddingVector embed(String normalizedText);
}
