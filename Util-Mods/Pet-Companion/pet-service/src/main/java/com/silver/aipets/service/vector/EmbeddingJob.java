package com.silver.aipets.service.vector;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record EmbeddingJob(
        UUID jobId,
        UUID petId,
        UUID memoryId,
        long memoryVersion,
        String embeddingModel,
        String idempotencyKey,
        EmbeddingJobStatus status,
        int attemptCount,
        Instant notBefore,
        Optional<String> lockedBy,
        Optional<Instant> lockedUntil,
        Optional<String> lastErrorSanitized) {

    public EmbeddingJob {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        if (memoryVersion < 1) {
            throw new IllegalArgumentException("memoryVersion must be positive");
        }
        embeddingModel = bounded(embeddingModel, "embeddingModel", 191);
        idempotencyKey = bounded(idempotencyKey, "idempotencyKey", 191);
        Objects.requireNonNull(status, "status");
        if (attemptCount < 0) {
            throw new IllegalArgumentException("attemptCount cannot be negative");
        }
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(lockedBy, "lockedBy");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(lastErrorSanitized, "lastErrorSanitized");
        if (lockedBy.isPresent() != lockedUntil.isPresent()) {
            throw new IllegalArgumentException("lock owner and deadline must coexist");
        }
        lockedBy = lockedBy.map(value -> bounded(value, "lockedBy", 191));
        lastErrorSanitized = lastErrorSanitized.map(value -> bounded(value, "lastError", 1_000));
    }

    public static EmbeddingJob pending(
            UUID jobId,
            UUID petId,
            UUID memoryId,
            long memoryVersion,
            String embeddingModel,
            String idempotencyKey,
            Instant notBefore) {
        return new EmbeddingJob(
                jobId, petId, memoryId, memoryVersion, embeddingModel, idempotencyKey,
                EmbeddingJobStatus.PENDING, 0, notBefore,
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return normalized;
    }
}
