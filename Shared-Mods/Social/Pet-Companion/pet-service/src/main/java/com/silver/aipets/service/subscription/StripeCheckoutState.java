package com.silver.aipets.service.subscription;

/** Stripe Checkout lifecycle states relevant to replacing a payable session. */
public enum StripeCheckoutState {
    OPEN,
    COMPLETE,
    EXPIRED;

    static StripeCheckoutState fromStripeStatus(String status) {
        return switch (status) {
            case "open" -> OPEN;
            case "complete" -> COMPLETE;
            case "expired" -> EXPIRED;
            default -> throw new IllegalStateException("Stripe returned an unknown Checkout status");
        };
    }
}
