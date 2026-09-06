package com.silver.aipets.service.consolidation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ConsolidationJob(
        UUID jobId,
        UUID petId,
        UUID sleepCycleId,
        String idempotencyKey,
        ConsolidationJobStatus status,
        int attemptCount,
        Instant notBefore,
        Optional<String> lockedBy,
        Optional<Instant> lockedUntil,
        Optional<String> lastErrorCategory) {
    public ConsolidationJob {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(sleepCycleId, "sleepCycleId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(lockedBy, "lockedBy");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(lastErrorCategory, "lastErrorCategory");
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 191 || attemptCount < 0
                || lockedBy.isPresent() != lockedUntil.isPresent()) {
            throw new IllegalArgumentException("Invalid consolidation job");
        }
    }

    public static ConsolidationJob pending(
            UUID jobId, UUID petId, UUID sleepCycleId, Instant at) {
        return new ConsolidationJob(
                jobId, petId, sleepCycleId,
                "sleep-consolidation:" + petId + ':' + sleepCycleId,
                ConsolidationJobStatus.PENDING, 0, at,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
