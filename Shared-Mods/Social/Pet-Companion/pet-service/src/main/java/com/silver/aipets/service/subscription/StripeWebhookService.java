package com.silver.aipets.service.subscription;

import java.time.Clock;
import java.util.Objects;

/** Verify-before-parse webhook orchestration. */
public final class StripeWebhookService {
    private final StripeSignatureVerifier signatures;
    private final StripeWebhookParser parser;
    private final StripeWebhookRepository repository;
    private final Clock clock;
    private final String configuredPriceId;
    private final int paymentGraceDays;

    public StripeWebhookService(
            StripeSignatureVerifier signatures,
            StripeWebhookParser parser,
            StripeWebhookRepository repository,
            Clock clock,
            String configuredPriceId,
            int paymentGraceDays) {
        this.signatures = Objects.requireNonNull(signatures, "signatures");
        this.parser = Objects.requireNonNull(parser, "parser");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (configuredPriceId == null || !configuredPriceId.startsWith("price_")) {
            throw new IllegalArgumentException("configuredPriceId must be a Stripe Price ID");
        }
        if (paymentGraceDays < 0 || paymentGraceDays > 14) {
            throw new IllegalArgumentException("paymentGraceDays must be in 0-14");
        }
        this.configuredPriceId = configuredPriceId;
        this.paymentGraceDays = paymentGraceDays;
    }

    public StripeWebhookApplyStatus handle(byte[] rawPayload, String signatureHeader) {
        signatures.verify(rawPayload, signatureHeader);
        StripeWebhookEvent event = parser.parse(rawPayload);
        return repository.apply(
                event, clock.instant(), configuredPriceId, paymentGraceDays);
    }
}
