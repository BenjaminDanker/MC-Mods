package com.silver.aipets.service.vector;

import java.time.Instant;
import java.util.UUID;

/** Optional sink for charging embedding calls to the shared AI usage ledger. */
@FunctionalInterface
public interface EmbeddingUsageRecorder {
    void record(UUID petId, EmbeddingModelResponse response, Instant at, String status, String errorCategory);

    EmbeddingUsageRecorder NOOP = (petId, response, at, status, errorCategory) -> { };
}
