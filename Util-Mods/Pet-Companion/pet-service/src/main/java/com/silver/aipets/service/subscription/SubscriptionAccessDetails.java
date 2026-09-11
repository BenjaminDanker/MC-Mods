package com.silver.aipets.service.subscription;

import java.time.Instant;

/** Internal, non-secret subscription projection used to explain billing state to a player. */
public record SubscriptionAccessDetails(
        boolean aiAccessEnabled,
        String status,
        boolean cancelAtPeriodEnd,
        Instant currentPeriodStart,
        Instant currentPeriodEnd) {

    /** Compatibility constructor for callers that only have the old projection. */
    public SubscriptionAccessDetails(
            boolean aiAccessEnabled,
            String status,
            boolean cancelAtPeriodEnd,
            Instant currentPeriodEnd) {
        this(aiAccessEnabled, status, cancelAtPeriodEnd, null, currentPeriodEnd);
    }
}
