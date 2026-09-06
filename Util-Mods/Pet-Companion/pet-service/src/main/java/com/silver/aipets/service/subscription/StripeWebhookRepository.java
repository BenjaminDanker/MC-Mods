package com.silver.aipets.service.subscription;

import java.time.Instant;

public interface StripeWebhookRepository {
    StripeWebhookApplyStatus apply(
            StripeWebhookEvent event,
            Instant processedAt,
            String configuredPriceId,
            int paymentGraceDays);
}
