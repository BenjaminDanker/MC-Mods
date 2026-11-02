package com.silver.aipets.service.retention;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryRetentionCleanupRepository implements RetentionCleanupRepository {
    private final Object monitor = new Object();
    private final Map<UUID, RetentionEvent> events = new LinkedHashMap<>();
    private final Map<UUID, RetentionCleanupJob> jobs = new LinkedHashMap<>();
    private final Map<String, UUID> jobsByKey = new HashMap<>();
    private boolean failNextCleanup;

    public void putEvent(RetentionEvent event) {
        synchronized (monitor) {
            if (events.putIfAbsent(event.eventId(), event) != null) throw new IllegalStateException("Duplicate event");
        }
    }

    public RetentionEvent event(UUID eventId) {
        synchronized (monitor) { return Objects.requireNonNull(events.get(eventId)); }
    }

    public void failNextCleanup() { synchronized (monitor) { failNextCleanup = true; } }

    @Override public EnqueueResult enqueue(RetentionCleanupJob proposed) {
        synchronized (monitor) {
            UUID existing = jobsByKey.get(proposed.idempotencyKey());
            if (existing != null) return new EnqueueResult(jobs.get(existing), false);
            jobs.put(proposed.jobId(), proposed); jobsByKey.put(proposed.idempotencyKey(), proposed.jobId());
            return new EnqueueResult(proposed, true);
        }
    }

    @Override public List<RetentionCleanupJob> claimDue(
            String workerId, Instant now, Duration lease, int limit) {
        Objects.requireNonNull(workerId, "workerId"); Objects.requireNonNull(now, "now");
        Objects.requireNonNull(lease, "lease");
        if (workerId.isBlank() || workerId.length() > 191 || lease.isZero()
                || lease.isNegative() || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Invalid retention claim bounds");
        }
        synchronized (monitor) {
            List<RetentionCleanupJob> due = jobs.values().stream()
                    .filter(job -> !job.notBefore().isAfter(now))
                    .filter(job -> job.status() == RetentionCleanupJob.Status.PENDING
                            || job.status() == RetentionCleanupJob.Status.RETRY
                            || (job.status() == RetentionCleanupJob.Status.RUNNING
                            && !job.lockedUntil().orElseThrow().isAfter(now)))
                    .sorted(Comparator.comparing(RetentionCleanupJob::notBefore)
                            .thenComparing(job -> job.jobId().toString())).limit(limit).toList();
            List<RetentionCleanupJob> result = new ArrayList<>();
            for (RetentionCleanupJob job : due) {
                RetentionCleanupJob claimed = copy(job, RetentionCleanupJob.Status.RUNNING,
                        job.attemptCount() + 1, job.notBefore(), Optional.of(workerId),
                        Optional.of(now.plus(lease)), job.lastErrorCategory());
                jobs.put(job.jobId(), claimed); result.add(claimed);
            }
            return List.copyOf(result);
        }
    }

    @Override public RetentionCleanupResult cleanup(
            RetentionCleanupJob job, String workerId, Instant now, int maximumRows) {
        synchronized (monitor) {
            requireLock(job, workerId);
            if (failNextCleanup) { failNextCleanup = false; throw new IllegalStateException("injected cleanup failure"); }
            List<RetentionEvent> ordered = events.values().stream()
                    .sorted(Comparator.comparing(RetentionEvent::occurredAt)
                            .thenComparing(event -> event.eventId().toString())).toList();
            int expired = 0, redacted = 0;
            for (RetentionEvent event : ordered) {
                boolean expire = event.promptEligible() && event.shortTermExpiresAt()
                        .map(expiry -> !expiry.isAfter(now)).orElse(false);
                boolean redact = !event.occurredAt().isAfter(job.rawCutoff())
                        && (event.rawPlayerText().isPresent() || event.rawPetReply().isPresent());
                if (!expire && !redact) continue;
                if (Math.max(expired, redacted) >= maximumRows) break;
                events.put(event.eventId(), new RetentionEvent(
                        event.eventId(), event.petId(), event.occurredAt(), event.shortTermExpiresAt(),
                        expire ? false : event.promptEligible(), event.summary(),
                        redact ? Optional.empty() : event.rawPlayerText(),
                        redact ? Optional.empty() : event.rawPetReply()));
                if (expire) expired++; if (redact) redacted++;
            }
            jobs.put(job.jobId(), copy(job, RetentionCleanupJob.Status.SUCCEEDED,
                    job.attemptCount(), now, Optional.empty(), Optional.empty(), Optional.empty()));
            return new RetentionCleanupResult(expired, redacted);
        }
    }

    @Override public void retry(RetentionCleanupJob job, String workerId, Instant now, Instant notBefore, String category) {
        synchronized (monitor) { requireLock(job, workerId); jobs.put(job.jobId(), copy(job,
                RetentionCleanupJob.Status.RETRY, job.attemptCount(), notBefore,
                Optional.empty(), Optional.empty(), Optional.of(bounded(category)))); }
    }
    @Override public void failed(RetentionCleanupJob job, String workerId, Instant now, String category) {
        synchronized (monitor) { requireLock(job, workerId); jobs.put(job.jobId(), copy(job,
                RetentionCleanupJob.Status.FAILED, job.attemptCount(), now,
                Optional.empty(), Optional.empty(), Optional.of(bounded(category)))); }
    }
    @Override public Optional<RetentionCleanupJob> findByKey(String key) {
        synchronized (monitor) { UUID id = jobsByKey.get(key); return id == null ? Optional.empty() : Optional.of(jobs.get(id)); }
    }

    private void requireLock(RetentionCleanupJob job, String workerId) {
        RetentionCleanupJob current = jobs.get(job.jobId());
        if (current == null || current.status() != RetentionCleanupJob.Status.RUNNING
                || !current.lockedBy().equals(Optional.of(workerId))) throw new IllegalStateException("Cleanup lease not owned");
    }
    private static RetentionCleanupJob copy(
            RetentionCleanupJob job, RetentionCleanupJob.Status status, int attempts,
            Instant notBefore, Optional<String> by, Optional<Instant> until, Optional<String> error) {
        return new RetentionCleanupJob(job.jobId(), job.idempotencyKey(), status, attempts,
                job.scheduledAt(), job.rawCutoff(), notBefore, by, until, error);
    }
    private static String bounded(String value) {
        String safe = value == null || value.isBlank() ? "RuntimeException" : value;
        return safe.substring(0, Math.min(128, safe.length()));
    }
}
