package com.silver.aipets.service.subscription;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Deterministic provider-event reducer; stale-event filtering remains repository-owned. */
public final class SubscriptionStateReducer {
    private SubscriptionStateReducer() {
    }

    public static SubscriptionState bindCheckout(
            SubscriptionState previous, StripeWebhookEvent event) {
        Objects.requireNonNull(previous, "previous");
        if (event.kind() != StripeWebhookKind.CHECKOUT_COMPLETED) {
            throw new IllegalArgumentException("event must be checkout.session.completed");
        }
        return new SubscriptionState(
                previous.ownerUuid(),
                event.customerId().orElse(previous.customerId()),
                event.subscriptionId().orElse(previous.subscriptionId()),
                event.priceId().orElse(previous.priceId()),
                previous.status(),
                previous.aiAccessEnabled(),
                previous.currentPeriodStart(),
                previous.currentPeriodEnd(),
                previous.cancelAtPeriodEnd(),
                previous.graceEndsAt(),
                previous.lastStripeEventAt());
    }

    public static SubscriptionState applyEntitlement(
            SubscriptionState previous,
            StripeWebhookEvent event,
            Instant processedAt,
            String configuredPriceId,
            int paymentGraceDays) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(processedAt, "processedAt");
        if (!event.kind().changesEntitlement()) {
            throw new IllegalArgumentException("event does not change entitlement");
        }
        SubscriptionStatus status = event.subscriptionStatus().orElseThrow();
        String customerId = event.customerId().orElse(previous.customerId());
        String subscriptionId = event.subscriptionId().orElse(previous.subscriptionId());
        String priceId = event.priceId().orElse(previous.priceId());
        Instant periodStart = event.currentPeriodStart().orElse(previous.currentPeriodStart());
        Instant periodEnd = event.currentPeriodEnd().orElse(previous.currentPeriodEnd());
        boolean cancelAtPeriodEnd = switch (event.kind()) {
            case SUBSCRIPTION_CREATED, SUBSCRIPTION_UPDATED, SUBSCRIPTION_DELETED ->
                    event.cancelAtPeriodEnd();
            default -> previous.cancelAtPeriodEnd();
        };
        Instant graceEndsAt = switch (status) {
            case PAST_DUE -> previous.status() == SubscriptionStatus.PAST_DUE
                    && previous.graceEndsAt() != null
                            ? previous.graceEndsAt()
                            : event.createdAt().plus(paymentGraceDays, ChronoUnit.DAYS);
            case ACTIVE, TRIALING, CANCELED, INACTIVE -> null;
        };
        boolean accessEnabled = SubscriptionEntitlementPolicy.allows(
                status,
                configuredPriceId.equals(priceId),
                cancelAtPeriodEnd,
                periodEnd,
                graceEndsAt,
                processedAt);
        return new SubscriptionState(
                previous.ownerUuid(), customerId, subscriptionId, priceId, status,
                accessEnabled, periodStart, periodEnd, cancelAtPeriodEnd, graceEndsAt,
                event.createdAt());
    }
}
