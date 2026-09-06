package com.silver.aipets.velocity;

import com.silver.aipets.common.transport.PetPresenceWireRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Serializes each player's true proxy login/logout events so HTTP completion cannot reorder them. */
public final class PetNetworkPresenceReporter {
    private final PresenceGateway gateway;
    private final Clock clock;
    private final Supplier<UUID> absenceIds;
    private final Duration retryBaseDelay;
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> tails = new ConcurrentHashMap<>();

    public PetNetworkPresenceReporter(PresenceGateway gateway, Clock clock, Supplier<UUID> absenceIds) {
        this(gateway, clock, absenceIds, Duration.ofSeconds(1));
    }

    PetNetworkPresenceReporter(
            PresenceGateway gateway,
            Clock clock,
            Supplier<UUID> absenceIds,
            Duration retryBaseDelay) {
        this.gateway = java.util.Objects.requireNonNull(gateway, "gateway");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
        this.absenceIds = java.util.Objects.requireNonNull(absenceIds, "absenceIds");
        this.retryBaseDelay = java.util.Objects.requireNonNull(retryBaseDelay, "retryBaseDelay");
        if (retryBaseDelay.isNegative()) throw new IllegalArgumentException("retry delay is negative");
    }

    public CompletableFuture<Void> ownerConnected(UUID ownerUuid) {
        return enqueue(new PetPresenceWireRequest(
                ownerUuid, true, Optional.empty(), clock.instant()));
    }

    public CompletableFuture<Void> ownerDisconnected(UUID ownerUuid) {
        return enqueue(new PetPresenceWireRequest(
                ownerUuid, false,
                Optional.of(java.util.Objects.requireNonNull(absenceIds.get(), "absence ID")),
                clock.instant()));
    }

    public int pendingOwners() { return tails.size(); }

    private CompletableFuture<Void> enqueue(PetPresenceWireRequest event) {
        UUID owner = event.ownerUuid();
        CompletableFuture<Void> next = tails.compute(owner, (ignored, previous) -> {
            CompletableFuture<Void> start = previous == null
                    ? CompletableFuture.completedFuture(null)
                    : previous.handle((value, failure) -> null);
            return start.thenCompose(value -> publish(event, 1));
        });
        next.whenComplete((value, failure) -> tails.remove(owner, next));
        return next;
    }

    private CompletableFuture<Void> publish(PetPresenceWireRequest event, int attempt) {
        CompletableFuture<Void> published;
        try {
            published = gateway.publish(event).toCompletableFuture();
        } catch (RuntimeException failure) {
            published = CompletableFuture.failedFuture(failure);
        }
        return published.handle((value, failure) -> {
            if (failure == null) return CompletableFuture.<Void>completedFuture(null);
            if (attempt >= 3) return CompletableFuture.<Void>failedFuture(failure);
            long delayMillis = retryBaseDelay.toMillis() * (1L << (attempt - 1));
            return CompletableFuture.runAsync(
                            () -> { },
                            CompletableFuture.delayedExecutor(delayMillis, TimeUnit.MILLISECONDS))
                    .thenCompose(ignored -> publish(event, attempt + 1));
        }).thenCompose(stage -> stage);
    }

    public void awaitDrain(long timeout, TimeUnit unit) {
        CompletableFuture<?>[] pending = tails.values().toArray(CompletableFuture[]::new);
        if (pending.length == 0) return;
        try {
            CompletableFuture.allOf(pending).get(timeout, unit);
        } catch (Exception ignored) {
            // Proxy shutdown remains bounded; the service is restart-safe and later online wins.
        }
    }
}
