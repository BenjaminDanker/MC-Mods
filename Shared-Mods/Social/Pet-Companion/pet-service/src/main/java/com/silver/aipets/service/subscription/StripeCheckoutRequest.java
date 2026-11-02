package com.silver.aipets.service.subscription;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/** Trusted server-side values used to create one hosted subscription Checkout Session. */
public record StripeCheckoutRequest(
        UUID ownerUuid,
        String accountLinkHash,
        String priceId,
        URI successUrl,
        URI cancelUrl) {
    public StripeCheckoutRequest {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(accountLinkHash, "accountLinkHash");
        Objects.requireNonNull(priceId, "priceId");
        Objects.requireNonNull(successUrl, "successUrl");
        Objects.requireNonNull(cancelUrl, "cancelUrl");
        if (!accountLinkHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("accountLinkHash is invalid");
        }
        if (!priceId.startsWith("price_") || priceId.length() > 255) {
            throw new IllegalArgumentException("priceId is invalid");
        }
    }
}
