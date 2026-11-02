package com.silver.aipets.service.subscription;

import java.time.Instant;
import java.util.Objects;

/** Pure policy used when converging trusted provider state into the access flag. */
public final class SubscriptionEntitlementPolicy {
    private SubscriptionEntitlementPolicy() {
    }

    public static boolean allows(
            SubscriptionStatus status,
            boolean configuredPrice,
            boolean cancelAtPeriodEnd,
            Instant currentPeriodEnd,
            Instant graceEndsAt,
            Instant now) {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(now, "now");
        if (!configuredPrice) return false;
        return switch (status) {
            case ACTIVE, TRIALING -> !cancelAtPeriodEnd
                    || currentPeriodEnd == null
                    || currentPeriodEnd.isAfter(now);
            case PAST_DUE -> graceEndsAt != null && graceEndsAt.isAfter(now);
            case CANCELED, INACTIVE -> false;
        };
    }
}
