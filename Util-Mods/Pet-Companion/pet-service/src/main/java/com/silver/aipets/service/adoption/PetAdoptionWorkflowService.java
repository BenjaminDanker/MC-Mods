package com.silver.aipets.service.adoption;

import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.service.subscription.AccountLinkService;
import com.silver.aipets.service.subscription.SubscriptionAccess;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Coordinates immediate adoption or a durable, UUID-bound checkout intent. */
public final class PetAdoptionWorkflowService {
    private final PetAdoptionService adoptions;
    private final SubscriptionAccess subscriptionAccess;
    private final PendingAdoptionRepository pending;
    private final AccountLinkService links;
    private final Clock clock;

    public PetAdoptionWorkflowService(
            PetAdoptionService adoptions,
            SubscriptionAccess subscriptionAccess,
            PendingAdoptionRepository pending,
            AccountLinkService links,
            Clock clock) {
        this.adoptions = Objects.requireNonNull(adoptions, "adoptions");
        this.subscriptionAccess = Objects.requireNonNull(subscriptionAccess, "subscriptionAccess");
        this.pending = Objects.requireNonNull(pending, "pending");
        this.links = Objects.requireNonNull(links, "links");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PetAdoptionWireResult adopt(PetAdoptionWireRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.instant();
        pending.expireUnstarted(now);
        Optional<PendingAdoption> current = pending.find(request.ownerUuid());
        if (current.isPresent() && current.orElseThrow().checkoutStillActive(now)) {
            return result(PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS);
        }

        AdoptionResult direct = adoptions.adopt(
                request.ownerUuid(), request.species(), request.name());
        if (direct.status() != AdoptionStatus.SUBSCRIPTION_REQUIRED) {
            pending.cancelPreCheckout(request.ownerUuid(), now);
            return new PetAdoptionWireResult(
                    PetAdoptionWireStatus.valueOf(direct.status().name()), direct.pet());
        }

        if (subscriptionAccess.canAdopt(request.ownerUuid())) {
            // Entitlement can change between the adoption check and link creation.
            AdoptionResult retry = adoptions.adopt(
                    request.ownerUuid(), request.species(), request.name());
            if (retry.status() != AdoptionStatus.SUBSCRIPTION_REQUIRED) {
                pending.cancelPreCheckout(request.ownerUuid(), clock.instant());
                return new PetAdoptionWireResult(
                        PetAdoptionWireStatus.valueOf(retry.status().name()), retry.pet());
            }
        }

        PendingAdoptionLinkResult link = links.generateForPendingAdoption(
                request.ownerUuid(), java.util.UUID.randomUUID(), request.species(), request.name());
        return switch (link.status()) {
            case CREATED -> new PetAdoptionWireResult(
                    PetAdoptionWireStatus.CHECKOUT_REQUIRED,
                    Optional.empty(), link.checkoutUrl(), link.expiresAt());
            case RATE_LIMITED -> result(PetAdoptionWireStatus.CHECKOUT_RATE_LIMITED);
            case CHECKOUT_IN_PROGRESS -> result(PetAdoptionWireStatus.CHECKOUT_IN_PROGRESS);
        };
    }

    private static PetAdoptionWireResult result(PetAdoptionWireStatus status) {
        return new PetAdoptionWireResult(status, Optional.empty());
    }
}
