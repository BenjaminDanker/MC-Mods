package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.LongTermMemoryStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/** Idempotent compact-card embedding worker with bounded leases and exponential retry. */
public final class MemoryEmbeddingWorker {
    private final LongTermMemoryStore memories;
    private final EmbeddingJobStore jobs;
    private final EmbeddingModelClient model;
    private final VectorMemoryRepository vectors;
    private final Clock clock;
    private final Supplier<UUID> jobIds;
    private final int maximumAttempts;
    private final Duration leaseDuration;
    private final Duration initialRetryDelay;

    public MemoryEmbeddingWorker(
            LongTermMemoryStore memories,
            EmbeddingJobStore jobs,
            EmbeddingModelClient model,
            VectorMemoryRepository vectors,
            Clock clock,
            Supplier<UUID> jobIds,
            int maximumAttempts,
            Duration leaseDuration,
            Duration initialRetryDelay) {
        this.memories = Objects.requireNonNull(memories, "memories");
        this.jobs = Objects.requireNonNull(jobs, "jobs");
        this.model = Objects.requireNonNull(model, "model");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jobIds = Objects.requireNonNull(jobIds, "jobIds");
        if (maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalArgumentException("maximumAttempts must be between 1 and 20");
        }
        this.maximumAttempts = maximumAttempts;
        this.leaseDuration = positive(leaseDuration, "leaseDuration");
        this.initialRetryDelay = positive(initialRetryDelay, "initialRetryDelay");
    }

    public EmbeddingJobStore.EnqueueResult enqueue(LongTermMemoryCard card) {
        Objects.requireNonNull(card, "card");
        if (!card.active()) {
            throw new IllegalArgumentException("Cannot enqueue an inactive memory card");
        }
        String key = EmbeddingJobKeys.forMemory(
                card.petId(), card.memoryId(), card.version(), model.model());
        return jobs.enqueue(EmbeddingJob.pending(
                Objects.requireNonNull(jobIds.get(), "jobIds returned null"),
                card.petId(), card.memoryId(), card.version(), model.model(), key, clock.instant()));
    }

    public int processDue(String workerId, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        Instant now = clock.instant();
        List<EmbeddingJob> claimed = jobs.claimDue(workerId, now, leaseDuration, limit);
        for (EmbeddingJob job : claimed) {
            processOne(workerId, job, now);
        }
        return claimed.size();
    }

    private void processOne(String workerId, EmbeddingJob job, Instant now) {
        LongTermMemoryCard card = memories.findActive(job.petId(), job.memoryId()).orElse(null);
        if (card == null || card.version() != job.memoryVersion()
                || !job.embeddingModel().equals(model.model())) {
            jobs.canceled(job.jobId(), workerId, now, "memory missing, inactive, changed, or model superseded");
            return;
        }
        try {
            // The model sees only the compact relational card, never raw event/dialogue history.
            EmbeddingVector embedding = model.embed(card.text());
            if (!embedding.model().equals(model.model())) {
                throw new IllegalStateException("Embedding client returned an unexpected model");
            }
            String reference = vectors.upsert(new VectorMemoryDocument(card, embedding));
            if (reference == null || reference.isBlank() || reference.length() > 512) {
                throw new IllegalStateException("Vector repository returned an invalid reference");
            }
            memories.markEmbeddingReady(
                    card.petId(), card.memoryId(), card.version(), model.model(), reference, now);
            jobs.succeeded(job.jobId(), workerId, now);
        } catch (RuntimeException failure) {
            String sanitized = sanitize(failure);
            if (job.attemptCount() >= maximumAttempts) {
                memories.markEmbeddingFailed(
                        card.petId(), card.memoryId(), card.version(), now);
                jobs.failed(job.jobId(), workerId, now, sanitized);
            } else {
                long multiplier = 1L << Math.min(Math.max(0, job.attemptCount() - 1), 10);
                Duration delay = initialRetryDelay.multipliedBy(multiplier);
                jobs.retry(job.jobId(), workerId, now, now.plus(delay), sanitized);
            }
        }
    }

    private static Duration positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String sanitize(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        // Provider exception messages may echo request text, credentials, or response bodies.
        // Persist only a bounded exception category; detailed diagnostics belong in safe metrics.
        return type.isBlank() ? "RuntimeException" : type.substring(0, Math.min(type.length(), 128));
    }
}
