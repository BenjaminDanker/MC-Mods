package com.silver.aipets.service.subscription;

import java.net.URI;
import java.util.Objects;

public record StripeCheckoutSession(String sessionId, URI checkoutUrl) {
    public StripeCheckoutSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(checkoutUrl, "checkoutUrl");
        if (!sessionId.startsWith("cs_") || sessionId.length() > 255) {
            throw new IllegalArgumentException("Stripe Checkout session ID is invalid");
        }
        if (!"https".equalsIgnoreCase(checkoutUrl.getScheme())
                || !"checkout.stripe.com".equalsIgnoreCase(checkoutUrl.getHost())) {
            throw new IllegalArgumentException("Stripe Checkout returned an unsafe redirect URL");
        }
    }
}
