package com.silver.aipets.service.vector;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable job boundary; enqueue is idempotent by EmbeddingJob.idempotencyKey. */
public interface EmbeddingJobStore {
    EnqueueResult enqueue(EmbeddingJob proposed);

    List<EmbeddingJob> claimDue(
            String workerId, Instant now, Duration leaseDuration, int limit);

    void succeeded(UUID jobId, String workerId, Instant completedAt);

    void retry(
            UUID jobId, String workerId, Instant attemptedAt,
            Instant notBefore, String sanitizedError);

    void failed(UUID jobId, String workerId, Instant completedAt, String sanitizedError);

    void canceled(UUID jobId, String workerId, Instant completedAt, String sanitizedReason);

    Optional<EmbeddingJob> findByIdempotencyKey(String idempotencyKey);

    record EnqueueResult(EmbeddingJob job, boolean created) {
        public EnqueueResult {
            java.util.Objects.requireNonNull(job, "job");
        }
    }
}
