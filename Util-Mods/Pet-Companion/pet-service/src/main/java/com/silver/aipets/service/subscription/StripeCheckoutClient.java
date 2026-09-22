package com.silver.aipets.service.subscription;

public interface StripeCheckoutClient {
    StripeCheckoutSession create(
            StripeCheckoutRequest request, String idempotencyKey);

    default void expire(String checkoutSessionId) {
        // Optional for test or alternate providers; Stripe-backed production client supports it.
    }
}
