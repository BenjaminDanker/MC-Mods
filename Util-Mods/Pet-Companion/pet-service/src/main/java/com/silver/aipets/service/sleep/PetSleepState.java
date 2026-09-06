package com.silver.aipets.service.sleep;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authoritative, restart-safe sleep and whole-network presence state for one pet. */
public record PetSleepState(
        UUID petId,
        boolean sleeping,
        Optional<Instant> sleepStartedAt,
        Optional<Instant> sleepEndsAt,
        Optional<Instant> lastSleepCompletedAt,
        Instant forcedSleepDueAt,
        boolean ownerNetworkOnline,
        Optional<Instant> ownerLastLogoutAt,
        Optional<UUID> ownerAbsenceSessionId,
        boolean absenceSleepTriggered,
        Instant lastPresenceUpdateAt,
        Instant updatedAt) {

    public PetSleepState {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(sleepStartedAt, "sleepStartedAt");
        Objects.requireNonNull(sleepEndsAt, "sleepEndsAt");
        Objects.requireNonNull(lastSleepCompletedAt, "lastSleepCompletedAt");
        Objects.requireNonNull(forcedSleepDueAt, "forcedSleepDueAt");
        Objects.requireNonNull(ownerLastLogoutAt, "ownerLastLogoutAt");
        Objects.requireNonNull(ownerAbsenceSessionId, "ownerAbsenceSessionId");
        Objects.requireNonNull(lastPresenceUpdateAt, "lastPresenceUpdateAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (sleeping != (sleepStartedAt.isPresent() && sleepEndsAt.isPresent())) {
            throw new IllegalArgumentException("sleeping requires both sleep timestamps and awake requires neither");
        }
        if (sleeping && !sleepEndsAt.orElseThrow().isAfter(sleepStartedAt.orElseThrow())) {
            throw new IllegalArgumentException("sleepEndsAt must be after sleepStartedAt");
        }
        if (ownerNetworkOnline
                && (ownerLastLogoutAt.isPresent() || ownerAbsenceSessionId.isPresent()
                || absenceSleepTriggered)) {
            throw new IllegalArgumentException("online presence cannot retain an absence session");
        }
        if (!ownerNetworkOnline
                && (ownerLastLogoutAt.isPresent() != ownerAbsenceSessionId.isPresent())) {
            throw new IllegalArgumentException("offline logout timestamp and absence session must coexist");
        }
        if (absenceSleepTriggered && ownerAbsenceSessionId.isEmpty()) {
            throw new IllegalArgumentException("triggered absence sleep requires an absence session");
        }
    }

    public static PetSleepState initial(UUID petId, Instant createdAt, PetSleepPolicy policy) {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(policy, "policy");
        return new PetSleepState(
                petId, false, Optional.empty(), Optional.empty(), Optional.empty(),
                createdAt.plus(policy.maximumAwake()), false, Optional.empty(), Optional.empty(),
                false, createdAt, createdAt);
    }
}
