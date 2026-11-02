package com.silver.aipets.service.adoption;

import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.service.subscription.AccountLinkService;
import com.silver.aipets.service.subscription.StripeCheckoutClient;
import com.silver.aipets.service.subscription.StripeCheckoutState;
import com.silver.aipets.service.subscription.SubscriptionAccess;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Coordinates immediate adoption or a durable, UUID-bound checkout intent. */
public final class PetAdoptionWorkflowService {
    private final PetAdoptionService adoptions;
    private final SubscriptionAccess subscriptionAccess;
    private final PendingAdoptionRepository pending;
    private final AccountLinkService links;
    private final StripeCheckoutClient stripe;
    private final Clock clock;

    public PetAdoptionWorkflowService(
            PetAdoptionService adoptions,
            SubscriptionAccess subscriptionAccess,
            PendingAdoptionRepository pending,
            AccountLinkService links,
            StripeCheckoutClient stripe,
            Clock clock) {
        this.adoptions = Objects.requireNonNull(adoptions, "adoptions");
        this.subscriptionAccess = Objects.requireNonNull(subscriptionAccess, "subscriptionAccess");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.links = Objects.requireNonNull(links, "links");
        this.stripe = Objects.requireNonNull(stripe, "stripe");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PetAdoptionWireResult adopt(PetAdoptionWireRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        pending.expireUnstarted(now);
        AdoptionResult direct = adoptions.adopt(
                request.ownerUuid(), request.species(), request.name());
        if (direct.status() != AdoptionStatus.SUBSCRIPTION_REQUIRED) {
            pending.cancelUnfinished(request.ownerUuid(), now);
            return new PetAdoptionWireResult(
                    PetAdoptionWireStatus.valueOf(direct.status().name()), direct.pet());
        }

        if (subscriptionAccess.canAdopt(request.ownerUuid())) {
            // Entitlement can change between the adoption check and link creation.
            AdoptionResult retry = adoptions.adopt(
                    request.ownerUuid(), request.species(), request.name());
            if (retry.status() != AdoptionStatus.SUBSCRIPTION_REQUIRED) {
                pending.cancelUnfinished(request.ownerUuid(), clock.instant());
                return new PetAdoptionWireResult(
                        PetAdoptionWireStatus.valueOf(retry.status().name()), retry.pet());
            }
        }

        PendingAdoption current = pending.find(request.ownerUuid()).orElse(null);
        if (current != null && current.state() == PendingAdoptionState.WAITING_FOR_ENTITLEMENT) {
            // A completed superseded checkout already paid for the UUID. Replace only the
            // desired pet, and wait for the normal entitlement projection; never make them pay again.
            return waitingForEntitlement(request, current.expiresAt(), now);
        }

        Optional<PendingAdoption> replacing = pending.beginReplacement(
                request.ownerUuid(), now);
        if (replacing.isPresent() && replacing.orElseThrow().checkoutSessionId() != null) {
            ReplacementResolution resolution = resolveCheckout(
                    replacing.orElseThrow().checkoutSessionId());
            if (resolution == ReplacementResolution.INDETERMINATE) {
                return result(PetAdoptionWireStatus.CHECKOUT_CLOSING);
            }
            if (resolution == ReplacementResolution.COMPLETE) {
                return waitingForEntitlement(request, waitingExpiry(replacing.orElseThrow(), now), now);
            }
        }

        // Entitlement can become active while the old Checkout session is being closed.
        AdoptionResult afterReplacement = adoptIfEntitled(request);
        if (afterReplacement != null) {
            pending.cancelUnfinished(request.ownerUuid(), clock.instant());
            return new PetAdoptionWireResult(
                    PetAdoptionWireStatus.valueOf(afterReplacement.status().name()),
                    afterReplacement.pet());
        }

        PendingAdoptionLinkResult link = links.generateForPendingAdoption(
                request.ownerUuid(), UUID.randomUUID(), request.species(), request.name());
        return switch (link.status()) {
            case CREATED -> new PetAdoptionWireResult(
                    PetAdoptionWireStatus.CHECKOUT_REQUIRED,
                    Optional.empty(), link.checkoutUrl(), link.expiresAt());
            case RATE_LIMITED -> result(PetAdoptionWireStatus.CHECKOUT_RATE_LIMITED);
            case CHECKOUT_IN_PROGRESS -> result(PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS);
        };
    }

    private AdoptionResult adoptIfEntitled(PetAdoptionWireRequest request) {
        if (!subscriptionAccess.canAdopt(request.ownerUuid())) return null;
        AdoptionResult result = adoptions.adopt(
                request.ownerUuid(), request.species(), request.name());
        return result.status() == AdoptionStatus.SUBSCRIPTION_REQUIRED ? null : result;
    }

    private PetAdoptionWireResult waitingForEntitlement(
            PetAdoptionWireRequest request, Instant expiresAt, Instant createdAt) {
        UUID intentId = UUID.randomUUID();
        PendingAdoptionLinkStatus status = pending.createWaitingForEntitlement(
                request.ownerUuid(), intentId, request.species(), request.name(),
                createdAt, expiresAt.isAfter(createdAt) ? expiresAt : createdAt.plus(Duration.ofHours(24)));
        if (status != PendingAdoptionLinkStatus.CREATED) {
            return result(status == PendingAdoptionLinkStatus.RATE_LIMITED
                    ? PetAdoptionWireStatus.CHECKOUT_RATE_LIMITED
                    : PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS);
        }
        AdoptionResult entitled = adoptIfEntitled(request);
        if (entitled != null) {
            Optional<PendingAdoption> current = pending.find(request.ownerUuid());
            if (current.isPresent() && current.orElseThrow().intentId().equals(intentId)
                    && entitled.pet().isPresent()) {
                pending.complete(request.ownerUuid(), intentId,
                        entitled.pet().orElseThrow().petId(), clock.instant());
            }
            return new PetAdoptionWireResult(
                    PetAdoptionWireStatus.valueOf(entitled.status().name()), entitled.pet());
        }
        return result(PetAdoptionWireStatus.SUBSCRIPTION_PENDING);
    }

    private Instant waitingExpiry(PendingAdoption oldIntent, Instant now) {
        Instant hardExpiry = oldIntent.hardExpiresAt();
        return hardExpiry != null && hardExpiry.isAfter(now)
                ? hardExpiry : now.plus(Duration.ofHours(24));
    }

    private ReplacementResolution resolveCheckout(String sessionId) {
        StripeCheckoutState observed;
        try {
            observed = stripe.retrieve(sessionId);
        } catch (RuntimeException failure) {
            return ReplacementResolution.INDETERMINATE;
        }
        if (observed == StripeCheckoutState.COMPLETE) return ReplacementResolution.COMPLETE;
        if (observed == StripeCheckoutState.EXPIRED) return ReplacementResolution.CLOSED;

        try {
            StripeCheckoutState expired = stripe.expire(sessionId);
            if (expired == StripeCheckoutState.COMPLETE) return ReplacementResolution.COMPLETE;
            if (expired == StripeCheckoutState.EXPIRED) return ReplacementResolution.CLOSED;
        } catch (RuntimeException failure) {
            return observeAfterExpirationFailure(sessionId);
        }
        return observeAfterExpirationFailure(sessionId);
    }

    private ReplacementResolution observeAfterExpirationFailure(String sessionId) {
        try {
            return switch (stripe.retrieve(sessionId)) {
                case COMPLETE -> ReplacementResolution.COMPLETE;
                case EXPIRED -> ReplacementResolution.CLOSED;
                case OPEN -> ReplacementResolution.INDETERMINATE;
            };
        } catch (RuntimeException failure) {
            return ReplacementResolution.INDETERMINATE;
        }
    }

    private enum ReplacementResolution {
        CLOSED,
        COMPLETE,
        INDETERMINATE
    }

    private static PetAdoptionWireResult result(PetAdoptionWireStatus status) {
        return new PetAdoptionWireResult(status, Optional.empty());
    }
}
