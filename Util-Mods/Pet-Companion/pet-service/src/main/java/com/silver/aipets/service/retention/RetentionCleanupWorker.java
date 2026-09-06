package com.silver.aipets.service.retention;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class RetentionCleanupWorker {
    private final RetentionPolicy policy;
    private final RetentionCleanupRepository repository;
    private final Clock clock;
    private final Supplier<UUID> jobIds;

    public RetentionCleanupWorker(
            RetentionPolicy policy, RetentionCleanupRepository repository,
            Clock clock, Supplier<UUID> jobIds) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.jobIds = Objects.requireNonNull(jobIds, "jobIds");
    }

    public RetentionCleanupRepository.EnqueueResult ensureDailyJob() {
        return repository.enqueue(RetentionCleanupJob.daily(
                Objects.requireNonNull(jobIds.get(), "jobIds returned null"), clock.instant(), policy));
    }

    public int processDue(String workerId, int limit) {
        Objects.requireNonNull(workerId, "workerId");
        if (workerId.isBlank() || workerId.length() > 191 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid retention worker request");
        }
        Instant now = clock.instant();
        List<RetentionCleanupJob> jobs = repository.claimDue(
                workerId, now, policy.leaseDuration(), limit);
        for (RetentionCleanupJob job : jobs) {
            try {
                repository.cleanup(job, workerId, now, policy.maximumRowsPerRun());
            } catch (RuntimeException failure) {
                String category = sanitize(failure);
                if (job.attemptCount() >= policy.maximumAttempts()) {
                    repository.failed(job, workerId, now, category);
                } else {
                    repository.retry(job, workerId, now, now.plus(policy.retryDelay()), category);
                }
            }
        }
        return jobs.size();
    }

    private static String sanitize(RuntimeException failure) {
        String type = failure.getClass().getSimpleName();
        return type.isBlank() ? "RuntimeException" : type.substring(0, Math.min(type.length(), 128));
    }
}
