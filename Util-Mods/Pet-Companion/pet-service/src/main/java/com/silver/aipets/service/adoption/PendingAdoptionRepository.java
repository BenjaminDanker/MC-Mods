package com.silver.aipets.service.adoption;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.silver.aipets.common.domain.PetSpecies;

public interface PendingAdoptionRepository {
    Optional<PendingAdoption> find(UUID ownerUuid);

    void cancelPreCheckout(UUID ownerUuid, Instant cancelledAt);

    PendingAdoptionLinkStatus createWithLink(
            UUID ownerUuid,
            UUID intentId,
            PetSpecies species,
            String name,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt,
            Instant generationWindowStart,
            int maximumGenerations);

    PendingAdoptionLinkStatus rotatePreCheckoutLink(
            UUID ownerUuid,
            UUID intentId,
            String tokenHash,
            Instant createdAt,
            Instant linkExpiresAt,
            Instant generationWindowStart,
            int maximumGenerations);

    CheckoutLaunchClaim claimCheckoutStart(
            String tokenHash, Instant now, Instant staleClaimBefore);

    void releaseCheckoutStart(String tokenHash, Instant claimedAt);

    void expireUnstarted(Instant now);

    List<PendingAdoption> findCheckoutCompleted(int limit);

    boolean complete(UUID ownerUuid, UUID intentId, UUID petId, Instant completedAt);

    Optional<PendingAdoptionNotice> findNotification(UUID ownerUuid);

    boolean acknowledgeNotification(UUID ownerUuid, UUID intentId, Instant acknowledgedAt);
}
