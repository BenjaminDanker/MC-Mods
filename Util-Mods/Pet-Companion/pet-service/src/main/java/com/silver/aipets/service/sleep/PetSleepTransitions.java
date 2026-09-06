package com.silver.aipets.service.sleep;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure sleep/presence transitions. Placement and AI provider state are deliberately absent. */
public final class PetSleepTransitions {
    private PetSleepTransitions() {
    }

    public static PetSleepTransition networkPresence(
            PetSleepState current,
            boolean online,
            UUID newAbsenceSessionId,
            Instant occurredAt) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (occurredAt.isBefore(current.lastPresenceUpdateAt())) {
            return unchanged(current);
        }
        if (online) {
            if (current.ownerNetworkOnline()
                    && occurredAt.equals(current.lastPresenceUpdateAt())) {
                return unchanged(current);
            }
            return changed(current, true, Optional.empty(), Optional.empty(), false,
                    occurredAt, occurredAt);
        }
        if (!current.ownerNetworkOnline() && current.ownerAbsenceSessionId().isPresent()) {
            // Backend switches and duplicate offline signals must not move the network logout deadline.
            return unchanged(current);
        }
        Objects.requireNonNull(newAbsenceSessionId, "newAbsenceSessionId");
        return changed(current, false, Optional.of(occurredAt), Optional.of(newAbsenceSessionId),
                false, occurredAt, occurredAt);
    }

    public static PetSleepTransition evaluate(
            PetSleepState current, Instant now, PetSleepPolicy policy) {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(policy, "policy");
        if (now.isBefore(current.updatedAt())) {
            return unchanged(current);
        }
        if (current.sleeping()) {
            if (now.isBefore(current.sleepEndsAt().orElseThrow())) {
                return unchanged(current);
            }
            return complete(current, now, policy);
        }

        boolean absenceDue = !current.ownerNetworkOnline()
                && !current.absenceSleepTriggered()
                && current.ownerLastLogoutAt()
                .map(logout -> !now.isBefore(logout.plus(policy.logoutDelay())))
                .orElse(false);
        if (absenceDue) {
            return start(current, now, policy, true);
        }

        // Once an absence cycle has completed, an owner who stays offline remains dormant
        // rather than generating another sleep/consolidation cycle every 24 hours.
        if (!current.ownerNetworkOnline() && current.absenceSleepTriggered()) {
            return unchanged(current);
        }
        if (!now.isBefore(current.forcedSleepDueAt())) {
            return start(current, now, policy, false);
        }
        return unchanged(current);
    }

    private static PetSleepTransition start(
            PetSleepState current, Instant now, PetSleepPolicy policy, boolean fromAbsence) {
        PetSleepState next = new PetSleepState(
                current.petId(), true, Optional.of(now), Optional.of(now.plus(policy.sleepDuration())),
                current.lastSleepCompletedAt(), current.forcedSleepDueAt(),
                current.ownerNetworkOnline(), current.ownerLastLogoutAt(),
                current.ownerAbsenceSessionId(), current.absenceSleepTriggered() || fromAbsence,
                current.lastPresenceUpdateAt(), now);
        return new PetSleepTransition(next, fromAbsence
                ? PetSleepEvent.SLEEP_STARTED_AFTER_LOGOUT
                : PetSleepEvent.SLEEP_STARTED_FORCED);
    }

    private static PetSleepTransition complete(
            PetSleepState current, Instant now, PetSleepPolicy policy) {
        PetSleepState next = new PetSleepState(
                current.petId(), false, Optional.empty(), Optional.empty(), Optional.of(now),
                now.plus(policy.maximumAwake()), current.ownerNetworkOnline(),
                current.ownerLastLogoutAt(), current.ownerAbsenceSessionId(),
                current.absenceSleepTriggered(), current.lastPresenceUpdateAt(), now);
        return new PetSleepTransition(next, PetSleepEvent.SLEEP_COMPLETED);
    }

    private static PetSleepTransition changed(
            PetSleepState current,
            boolean online,
            Optional<Instant> logoutAt,
            Optional<UUID> absenceSessionId,
            boolean absenceTriggered,
            Instant presenceAt,
            Instant updatedAt) {
        return new PetSleepTransition(new PetSleepState(
                current.petId(), current.sleeping(), current.sleepStartedAt(), current.sleepEndsAt(),
                current.lastSleepCompletedAt(), current.forcedSleepDueAt(), online, logoutAt,
                absenceSessionId, absenceTriggered, presenceAt, updatedAt),
                PetSleepEvent.PRESENCE_UPDATED);
    }

    private static PetSleepTransition unchanged(PetSleepState current) {
        return new PetSleepTransition(current, PetSleepEvent.UNCHANGED);
    }
}
