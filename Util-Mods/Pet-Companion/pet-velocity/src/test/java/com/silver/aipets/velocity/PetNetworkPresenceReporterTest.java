package com.silver.aipets.velocity;

import com.silver.aipets.common.transport.PetPresenceWireRequest;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetNetworkPresenceReporterTest {
    @Test
    void serializesOnlineBeforeOfflineAndCreatesOneAbsenceSession() {
        UUID owner = UUID.fromString("10000000-0000-0000-0000-000000000001");
        UUID absence = UUID.fromString("30000000-0000-0000-0000-000000000001");
        List<PetPresenceWireRequest> published = new ArrayList<>();
        CompletableFuture<Void> firstCompletion = new CompletableFuture<>();
        PresenceGateway gateway = request -> {
            published.add(request);
            return published.size() == 1 ? firstCompletion : CompletableFuture.completedFuture(null);
        };
        PetNetworkPresenceReporter reporter = new PetNetworkPresenceReporter(
                gateway,
                Clock.fixed(Instant.parse("2026-08-31T12:00:00Z"), ZoneOffset.UTC),
                () -> absence);

        CompletableFuture<Void> online = reporter.ownerConnected(owner);
        CompletableFuture<Void> offline = reporter.ownerDisconnected(owner);
        assertEquals(1, published.size());
        assertTrue(published.getFirst().online());
        assertFalse(offline.isDone());

        firstCompletion.complete(null);
        offline.join();
        online.join();
        assertEquals(2, published.size());
        assertFalse(published.get(1).online());
        assertEquals(absence, published.get(1).absenceSessionId().orElseThrow());
        assertEquals(0, reporter.pendingOwners());

        AtomicInteger attempts = new AtomicInteger();
        PetNetworkPresenceReporter retrying = new PetNetworkPresenceReporter(
                request -> attempts.incrementAndGet() < 3
                        ? CompletableFuture.failedFuture(new IllegalStateException("temporary"))
                        : CompletableFuture.completedFuture(null),
                Clock.systemUTC(), () -> absence, Duration.ofMillis(1));
        retrying.ownerDisconnected(owner).join();
        assertEquals(3, attempts.get());
        assertEquals(0, retrying.pendingOwners());
    }
}
