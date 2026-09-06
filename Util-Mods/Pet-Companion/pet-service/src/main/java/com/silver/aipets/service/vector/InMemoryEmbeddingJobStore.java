package com.silver.aipets.service.vector;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe development adapter modeling idempotency, leases, retry, and terminal state. */
public final class InMemoryEmbeddingJobStore implements EmbeddingJobStore {
    private final Object monitor = new Object();
    private final Map<UUID, EmbeddingJob> byId = new HashMap<>();
    private final Map<String, UUID> idByKey = new HashMap<>();

    @Override
    public EnqueueResult enqueue(EmbeddingJob proposed) {
        Objects.requireNonNull(proposed, "proposed");
        synchronized (monitor) {
            UUID existingId = idByKey.get(proposed.idempotencyKey());
            if (existingId != null) {
                return new EnqueueResult(byId.get(existingId), false);
            }
            if (byId.containsKey(proposed.jobId())) {
                throw new IllegalStateException("Embedding job ID collision");
            }
            byId.put(proposed.jobId(), proposed);
            idByKey.put(proposed.idempotencyKey(), proposed.jobId());
            return new EnqueueResult(proposed, true);
        }
    }

    @Override
    public List<EmbeddingJob> claimDue(
            String workerId, Instant now, Duration leaseDuration, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        synchronized (monitor) {
            List<EmbeddingJob> due = byId.values().stream()
                    .filter(job -> isDue(job, now))
                    .sorted(Comparator.comparing(EmbeddingJob::notBefore)
                            .thenComparing(job -> job.jobId().toString()))
                    .limit(limit)
                    .toList();
            return due.stream().map(job -> {
                EmbeddingJob claimed = copy(job, EmbeddingJobStatus.RUNNING,
                        job.attemptCount() + 1, job.notBefore(), Optional.of(workerId),
                        Optional.of(now.plus(leaseDuration)), job.lastErrorSanitized());
                byId.put(claimed.jobId(), claimed);
                return claimed;
            }).toList();
        }
    }

    @Override
    public void succeeded(UUID jobId, String workerId, Instant completedAt) {
        terminal(jobId, workerId, EmbeddingJobStatus.SUCCEEDED, Optional.empty());
    }

    @Override
    public void retry(
            UUID jobId, String workerId, Instant attemptedAt,
            Instant notBefore, String sanitizedError) {
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        Objects.requireNonNull(notBefore, "notBefore");
        synchronized (monitor) {
            EmbeddingJob current = requireLock(jobId, workerId);
            byId.put(jobId, copy(current, EmbeddingJobStatus.RETRY,
                    current.attemptCount(), notBefore, Optional.empty(), Optional.empty(),
                    Optional.of(sanitizedError)));
        }
    }

    @Override
    public void failed(UUID jobId, String workerId, Instant completedAt, String sanitizedError) {
        terminal(jobId, workerId, EmbeddingJobStatus.FAILED, Optional.of(sanitizedError));
    }

    @Override
    public void canceled(UUID jobId, String workerId, Instant completedAt, String sanitizedReason) {
        terminal(jobId, workerId, EmbeddingJobStatus.CANCELED, Optional.of(sanitizedReason));
    }

    @Override
    public Optional<EmbeddingJob> findByIdempotencyKey(String idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        synchronized (monitor) {
            UUID id = idByKey.get(idempotencyKey);
            return id == null ? Optional.empty() : Optional.of(byId.get(id));
        }
    }

    private void terminal(
            UUID jobId, String workerId, EmbeddingJobStatus status, Optional<String> error) {
        synchronized (monitor) {
            EmbeddingJob current = requireLock(jobId, workerId);
            byId.put(jobId, copy(current, status, current.attemptCount(), current.notBefore(),
                    Optional.empty(), Optional.empty(), error));
        }
    }

    private EmbeddingJob requireLock(UUID jobId, String workerId) {
        EmbeddingJob current = byId.get(jobId);
        if (current == null || current.status() != EmbeddingJobStatus.RUNNING
                || !current.lockedBy().orElse("").equals(workerId)) {
            throw new IllegalStateException("Embedding job is not locked by this worker");
        }
        return current;
    }

    private static boolean isDue(EmbeddingJob job, Instant now) {
        if ((job.status() == EmbeddingJobStatus.PENDING
                || job.status() == EmbeddingJobStatus.RETRY)
                && !job.notBefore().isAfter(now)) {
            return true;
        }
        return job.status() == EmbeddingJobStatus.RUNNING
                && job.lockedUntil().map(deadline -> !deadline.isAfter(now)).orElse(false);
    }

    private static EmbeddingJob copy(
            EmbeddingJob source,
            EmbeddingJobStatus status,
            int attempts,
            Instant notBefore,
            Optional<String> lockedBy,
            Optional<Instant> lockedUntil,
            Optional<String> error) {
        return new EmbeddingJob(
                source.jobId(), source.petId(), source.memoryId(), source.memoryVersion(),
                source.embeddingModel(), source.idempotencyKey(), status, attempts, notBefore,
                lockedBy, lockedUntil, error);
    }
}
