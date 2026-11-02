package com.silver.aipets.service.subscription;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Persisted subscription projection used by the pure webhook reducer. */
public record SubscriptionState(
        UUID ownerUuid,
        String customerId,
        String subscriptionId,
        String priceId,
        SubscriptionStatus status,
        boolean aiAccessEnabled,
        Instant currentPeriodStart,
        Instant currentPeriodEnd,
        boolean cancelAtPeriodEnd,
        Instant graceEndsAt,
        Instant lastStripeEventAt) {
    public SubscriptionState {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(status, "status");
    }
}
