package com.silver.aipets.service.subscription;

import java.util.Locale;

public enum SubscriptionStatus {
    ACTIVE,
    TRIALING,
    PAST_DUE,
    CANCELED,
    INACTIVE;

    public static SubscriptionStatus fromStripe(String status) {
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "active" -> ACTIVE;
            case "trialing" -> TRIALING;
            case "past_due", "unpaid" -> PAST_DUE;
            case "canceled" -> CANCELED;
            case "incomplete", "incomplete_expired", "paused" -> INACTIVE;
            default -> throw new IllegalArgumentException("Unsupported Stripe subscription status");
        };
    }
}
