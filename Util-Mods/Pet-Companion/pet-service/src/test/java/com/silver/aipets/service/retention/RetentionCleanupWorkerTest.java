package com.silver.aipets.service.retention;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RetentionCleanupWorkerTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final UUID PET = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void exactBoundariesExpirePromptsAndRedactOnlyRawText() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRetentionCleanupRepository repository = new InMemoryRetentionCleanupRepository();
        UUID old = id(1), recent = id(2), future = id(3);
        repository.putEvent(event(old, NOW.minus(Duration.ofDays(7)), Optional.of(NOW), true));
        repository.putEvent(event(recent, NOW.minus(Duration.ofDays(7)).plusMillis(1), Optional.of(NOW), true));
        repository.putEvent(event(future, NOW.minus(Duration.ofDays(8)), Optional.of(NOW.plusSeconds(1)), true));
        RetentionCleanupWorker worker = worker(repository, clock);

        assertTrue(worker.ensureDailyJob().created());
        assertFalse(worker.ensureDailyJob().created());
        assertEquals(1, worker.processDue("retention-a", 1));

        assertFalse(repository.event(old).promptEligible());
        assertTrue(repository.event(old).rawPlayerText().isEmpty());
        assertTrue(repository.event(old).rawPetReply().isEmpty());
        assertEquals("durable compact summary", repository.event(old).summary());
        assertFalse(repository.event(recent).promptEligible());
        assertTrue(repository.event(recent).rawPlayerText().isPresent());
        assertTrue(repository.event(future).promptEligible());
        assertTrue(repository.event(future).rawPlayerText().isEmpty());
        assertEquals(RetentionCleanupJob.Status.SUCCEEDED,
                repository.findByKey("event-retention:2026-08-31").orElseThrow().status());
    }

    @Test
    void failureRetriesThroughFreshWorkerWithoutDuplicateDailyJob() {
        MutableClock clock = new MutableClock(NOW);
        InMemoryRetentionCleanupRepository repository = new InMemoryRetentionCleanupRepository();
        UUID old = id(10);
        repository.putEvent(event(old, NOW.minus(Duration.ofDays(8)), Optional.empty(), false));
        repository.failNextCleanup();
        RetentionCleanupWorker first = worker(repository, clock);
        first.ensureDailyJob();
        assertEquals(1, first.processDue("retention-a", 1));
        RetentionCleanupJob retry = repository.findByKey("event-retention:2026-08-31").orElseThrow();
        assertEquals(RetentionCleanupJob.Status.RETRY, retry.status());
        assertEquals("IllegalStateException", retry.lastErrorCategory().orElseThrow());
        assertTrue(repository.event(old).rawPlayerText().isPresent());

        clock.advance(Duration.ofMinutes(5));
        RetentionCleanupWorker restarted = worker(repository, clock);
        assertFalse(restarted.ensureDailyJob().created());
        assertEquals(1, restarted.processDue("retention-b", 1));
        assertEquals(RetentionCleanupJob.Status.SUCCEEDED,
                repository.findByKey("event-retention:2026-08-31").orElseThrow().status());
        assertTrue(repository.event(old).rawPlayerText().isEmpty());
    }

    private static RetentionEvent event(
            UUID id, Instant occurred, Optional<Instant> expiry, boolean eligible) {
        return new RetentionEvent(
                id, PET, occurred, expiry, eligible, "durable compact summary",
                Optional.of("private owner dialogue"), Optional.of("private pet reply"));
    }

    private static RetentionCleanupWorker worker(
            InMemoryRetentionCleanupRepository repository, Clock clock) {
        AtomicInteger ids = new AtomicInteger(100);
        return new RetentionCleanupWorker(
                RetentionPolicy.defaults(), repository, clock,
                () -> id(ids.incrementAndGet()));
    }

    private static UUID id(long value) { return new UUID(0, value); }

    private static final class MutableClock extends Clock {
        private Instant current;
        private MutableClock(Instant current) { this.current = current; }
        private void advance(Duration duration) { current = current.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return current; }
    }
}
