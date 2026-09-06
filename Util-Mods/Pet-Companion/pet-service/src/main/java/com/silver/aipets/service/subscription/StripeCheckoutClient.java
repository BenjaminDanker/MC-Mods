package com.silver.aipets.service.subscription;

public interface StripeCheckoutClient {
    StripeCheckoutSession create(
            StripeCheckoutRequest request, String idempotencyKey);
}
