package com.silver.aipets.service.subscription;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Trusted, normalized subset of a signature-verified Stripe event. */
public record StripeWebhookEvent(
        String eventId,
        String eventType,
        StripeWebhookKind kind,
        Instant createdAt,
        String payloadSha256,
        Optional<UUID> ownerUuid,
        Optional<String> accountLinkHash,
        Optional<String> checkoutSessionId,
        Optional<String> customerId,
        Optional<String> subscriptionId,
        Optional<String> priceId,
        Optional<SubscriptionStatus> subscriptionStatus,
        Optional<Instant> currentPeriodStart,
        Optional<Instant> currentPeriodEnd,
        boolean cancelAtPeriodEnd) {
    public StripeWebhookEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(payloadSha256, "payloadSha256");
        ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        accountLinkHash = Objects.requireNonNull(accountLinkHash, "accountLinkHash");
        checkoutSessionId = Objects.requireNonNull(checkoutSessionId, "checkoutSessionId");
        customerId = Objects.requireNonNull(customerId, "customerId");
        subscriptionId = Objects.requireNonNull(subscriptionId, "subscriptionId");
        priceId = Objects.requireNonNull(priceId, "priceId");
        subscriptionStatus = Objects.requireNonNull(subscriptionStatus, "subscriptionStatus");
        currentPeriodStart = Objects.requireNonNull(currentPeriodStart, "currentPeriodStart");
        currentPeriodEnd = Objects.requireNonNull(currentPeriodEnd, "currentPeriodEnd");
        if (eventId.length() > 255 || eventType.length() > 128
                || payloadSha256.length() != 64) {
            throw new IllegalArgumentException("Stripe event fields exceed persistence bounds");
        }
        if (accountLinkHash.isPresent()
                && !accountLinkHash.orElseThrow().matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Stripe account-link hash is invalid");
        }
        if (checkoutSessionId.isPresent()
                && (!checkoutSessionId.orElseThrow().startsWith("cs_")
                    || checkoutSessionId.orElseThrow().length() > 255)) {
            throw new IllegalArgumentException("Stripe Checkout session ID is invalid");
        }
        if (kind.changesEntitlement() && subscriptionStatus.isEmpty()) {
            throw new IllegalArgumentException("Entitlement event requires a status");
        }
    }
}
