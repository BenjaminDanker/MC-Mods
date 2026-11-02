package com.silver.aipets.service.subscription;

public interface StripeCheckoutClient {
    StripeCheckoutSession create(
            StripeCheckoutRequest request, String idempotencyKey);

    default StripeCheckoutState retrieve(String checkoutSessionId) {
        throw new UnsupportedOperationException("Checkout retrieval is not configured");
    }

    default StripeCheckoutState expire(String checkoutSessionId) {
        throw new UnsupportedOperationException("Checkout expiration is not configured");
    }
}
