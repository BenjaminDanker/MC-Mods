package com.silver.aipets.service.subscription;

import java.net.URI;
import java.util.Objects;

/** Resolves a UUID-bound token and idempotently creates its hosted Checkout Session. */
public final class CheckoutLaunchService {
    private final AccountLinkService links;
    private final StripeCheckoutClient stripe;
    private final String priceId;
    private final URI successUrl;
    private final URI cancelUrl;

    public CheckoutLaunchService(
            AccountLinkService links,
            StripeCheckoutClient stripe,
            String priceId,
            URI publicBaseUri) {
        this.links = Objects.requireNonNull(links, "links");
        this.stripe = Objects.requireNonNull(stripe, "stripe");
        this.priceId = Objects.requireNonNull(priceId, "priceId");
        URI base = Objects.requireNonNull(publicBaseUri, "publicBaseUri");
        successUrl = base.resolve("/checkout/success");
        cancelUrl = base.resolve("/checkout/cancel");
    }

    public StripeCheckoutSession launch(String token) {
        AccountLinkTarget target = links.resolve(token)
                .orElseThrow(() -> new InvalidAccountLinkException("invalid or expired link"));
        StripeCheckoutSession session = stripe.create(
                new StripeCheckoutRequest(
                        target.ownerUuid(), target.tokenHash(), priceId, successUrl, cancelUrl),
                target.tokenHash());
        if (!links.attachCheckout(target, session.sessionId())) {
            throw new InvalidAccountLinkException("link expired while Checkout was created");
        }
        return session;
    }
}
