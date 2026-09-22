package com.silver.aipets.service.subscription;

import java.net.URI;
import java.util.Objects;
import com.silver.aipets.service.adoption.CheckoutLaunchClaim;

/** Resolves a UUID-bound token and idempotently creates its hosted Checkout Session. */
public final class CheckoutLaunchService {
    private final AccountLinkService links;
    private final StripeCheckoutClient stripe;
    private final String priceId;
    private final URI successUrl;
    private final URI cancelUrl;
    private final SubscriptionAccess subscriptionAccess;

    public CheckoutLaunchService(
            AccountLinkService links,
            StripeCheckoutClient stripe,
            String priceId,
            URI publicBaseUri) {
        this(links, stripe, priceId, publicBaseUri, ownerUuid -> false);
    }

    public CheckoutLaunchService(
            AccountLinkService links,
            StripeCheckoutClient stripe,
            String priceId,
            URI publicBaseUri,
            SubscriptionAccess subscriptionAccess) {
        this.links = Objects.requireNonNull(links, "links");
        this.stripe = Objects.requireNonNull(stripe, "stripe");
        this.priceId = Objects.requireNonNull(priceId, "priceId");
        this.subscriptionAccess = Objects.requireNonNull(subscriptionAccess, "subscriptionAccess");
        URI base = Objects.requireNonNull(publicBaseUri, "publicBaseUri");
        successUrl = base.resolve("/checkout/success");
        cancelUrl = base.resolve("/checkout/cancel");
    }

    public StripeCheckoutSession launch(String token) {
        AccountLinkTarget target = links.resolve(token)
                .orElseThrow(() -> new InvalidAccountLinkException("invalid or expired link"));
        if (subscriptionAccess.canAdopt(target.ownerUuid())) {
            throw new ActiveSubscriptionException("owner already has an active subscription");
        }
        CheckoutLaunchClaim claim = links.claimCheckoutStart(target.tokenHash());
        if (claim == CheckoutLaunchClaim.INVALID) {
            throw new InvalidAccountLinkException("invalid or expired link");
        }
        final StripeCheckoutSession session;
        try {
            session = stripe.create(
                    new StripeCheckoutRequest(
                            target.ownerUuid(), target.tokenHash(), priceId, successUrl, cancelUrl),
                    target.tokenHash());
        } catch (RuntimeException failure) {
            if (claim == CheckoutLaunchClaim.CLAIMED) links.releaseCheckoutStart(target.tokenHash());
            throw failure;
        }
        if (!links.attachCheckout(target, session.sessionId())) {
            try {
                stripe.expire(session.sessionId());
            } catch (RuntimeException ignored) {
                // Binding is authoritative; an already-completed/expired Stripe session is harmless here.
            }
            if (claim == CheckoutLaunchClaim.CLAIMED) links.releaseCheckoutStart(target.tokenHash());
            throw new InvalidAccountLinkException("link expired while Checkout was created");
        }
        return session;
    }
}
