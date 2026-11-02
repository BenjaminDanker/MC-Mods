package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.PetSpecies;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PendingAdoption(
        UUID ownerUuid,
        UUID intentId,
        PetSpecies species,
        String name,
        PendingAdoptionState state,
        Instant createdAt,
        Instant expiresAt,
        Instant checkoutStartedAt,
        Instant hardExpiresAt,
        String accountLinkHash,
        String checkoutSessionId,
        Instant checkoutLaunchClaimedAt,
        Instant checkoutCompletedAt,
        UUID completedPetId,
        Instant completedAt,
        boolean notificationPending) {
    public PendingAdoption {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
    }

    public boolean checkoutStillActive(Instant now) {
        return state == PendingAdoptionState.CHECKOUT_STARTED
                && hardExpiresAt != null && hardExpiresAt.isAfter(now);
    }
}
