package com.silver.aipets.service.retention;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record RetentionCleanupJob(
        UUID jobId, String idempotencyKey, Status status, int attemptCount,
        Instant scheduledAt, Instant rawCutoff, Instant notBefore,
        Optional<String> lockedBy, Optional<Instant> lockedUntil,
        Optional<String> lastErrorCategory) {
    public enum Status { PENDING, RUNNING, RETRY, SUCCEEDED, FAILED }

    public RetentionCleanupJob {
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scheduledAt, "scheduledAt");
        Objects.requireNonNull(rawCutoff, "rawCutoff");
        Objects.requireNonNull(notBefore, "notBefore");
        Objects.requireNonNull(lockedBy, "lockedBy");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(lastErrorCategory, "lastErrorCategory");
        if (idempotencyKey.isBlank() || idempotencyKey.length() > 191 || attemptCount < 0
                || lockedBy.isPresent() != lockedUntil.isPresent()
                || rawCutoff.isAfter(scheduledAt)) {
            throw new IllegalArgumentException("Invalid retention cleanup job");
        }
    }

    public static RetentionCleanupJob daily(
            UUID jobId, Instant now, RetentionPolicy policy) {
        LocalDate day = now.atZone(ZoneOffset.UTC).toLocalDate();
        return new RetentionCleanupJob(
                jobId, "event-retention:" + day, Status.PENDING, 0,
                now, now.minus(policy.rawTextRetention()), now,
                Optional.empty(), Optional.empty(), Optional.empty());
    }
}
