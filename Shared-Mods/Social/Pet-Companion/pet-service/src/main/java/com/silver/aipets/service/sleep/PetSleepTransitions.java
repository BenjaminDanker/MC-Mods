package com.silver.aipets.service.sleep;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Pure, placement-independent sleep and fatigue transitions. */
public final class PetSleepTransitions {
    private PetSleepTransitions() { }

    public static PetSleepTransition networkPresence(PetSleepState current, boolean online,
            UUID newAbsenceSessionId, Instant at, PetSleepPolicy policy) {
        Objects.requireNonNull(current); Objects.requireNonNull(at); Objects.requireNonNull(policy);
        if (at.isBefore(current.lastPresenceUpdateAt()) || at.isBefore(current.updatedAt()))
            return unchanged(current);
        if (!online && !current.ownerNetworkOnline() && current.ownerAbsenceSessionId().isPresent())
            return unchanged(current);
        if (online && current.ownerNetworkOnline() && at.equals(current.lastPresenceUpdateAt()))
            return unchanged(current);
        double fatigue = accrued(current, at, policy);
        Optional<Instant> logout = online ? Optional.empty() : Optional.of(at);
        Optional<UUID> session = online ? Optional.empty() : Optional.of(Objects.requireNonNull(newAbsenceSessionId));
        Instant due = current.sleeping() ? current.forcedSleepDueAt()
                : dueAt(current.awakeSince(), at, fatigue, online, policy);
        PetSleepState next = new PetSleepState(current.petId(), current.sleeping(),
                current.sleepStartedAt(), current.sleepEndsAt(), current.lastSleepCompletedAt(),
                due, online, logout, session, false, at, at, fatigue, at, current.awakeSince());
        return new PetSleepTransition(next, PetSleepEvent.PRESENCE_UPDATED);
    }

    public static PetSleepTransition activity(PetSleepState current, Instant at,
            int points, PetSleepPolicy policy) {
        if (points < 0 || points > 20) throw new IllegalArgumentException("activity points out of range");
        if (current.sleeping() || !at.isAfter(current.fatigueUpdatedAt())
                || at.isBefore(current.updatedAt())) return unchanged(current);
        double fatigue = Math.min(100, accrued(current, at, policy) + points);
        Instant due = dueAt(current.awakeSince(), at, fatigue, current.ownerNetworkOnline(), policy);
        PetSleepState next = new PetSleepState(current.petId(), false, Optional.empty(),
                Optional.empty(), current.lastSleepCompletedAt(), due,
                current.ownerNetworkOnline(), current.ownerLastLogoutAt(),
                current.ownerAbsenceSessionId(), current.absenceSleepTriggered(),
                current.lastPresenceUpdateAt(), at, fatigue, at, current.awakeSince());
        return new PetSleepTransition(next, PetSleepEvent.FATIGUE_UPDATED);
    }

    public static PetSleepTransition evaluate(PetSleepState current, Instant now, PetSleepPolicy policy) {
        Objects.requireNonNull(current); Objects.requireNonNull(now); Objects.requireNonNull(policy);
        if (now.isBefore(current.updatedAt())) return unchanged(current);
        if (current.sleeping()) {
            if (now.isBefore(current.sleepEndsAt().orElseThrow())) return unchanged(current);
            double recovered = Math.max(0, current.fatigue() -
                    (Duration.between(current.sleepStartedAt().orElseThrow(),
                            current.sleepEndsAt().orElseThrow()).compareTo(policy.longSleepDuration()) >= 0
                            ? policy.longSleepRecovery() : policy.napRecovery()));
            PetSleepState next = new PetSleepState(current.petId(), false, Optional.empty(),
                    Optional.empty(), Optional.of(now),
                    dueAt(now, now, recovered, current.ownerNetworkOnline(), policy),
                    current.ownerNetworkOnline(), current.ownerLastLogoutAt(),
                    current.ownerAbsenceSessionId(), current.absenceSleepTriggered(),
                    current.lastPresenceUpdateAt(), now, recovered, now, now);
            return new PetSleepTransition(next, PetSleepEvent.SLEEP_COMPLETED);
        }
        boolean absenceDue = !current.ownerNetworkOnline() && !current.absenceSleepTriggered()
                && current.ownerLastLogoutAt().map(logout ->
                        !now.isBefore(logout.plus(policy.logoutDelay()))).orElse(false);
        if (!absenceDue && now.isBefore(current.forcedSleepDueAt())) return unchanged(current);
        double fatigue = accrued(current, now, policy);
        boolean longSleep = fatigue >= policy.longSleepThreshold()
                || Duration.between(current.awakeSince(), now).compareTo(Duration.ofHours(12)) >= 0;
        Duration duration = longSleep ? policy.longSleepDuration() : policy.napDuration();
        PetSleepState next = new PetSleepState(current.petId(), true, Optional.of(now),
                Optional.of(now.plus(duration)), current.lastSleepCompletedAt(), current.forcedSleepDueAt(),
                current.ownerNetworkOnline(), current.ownerLastLogoutAt(),
                current.ownerAbsenceSessionId(), current.absenceSleepTriggered() || absenceDue,
                current.lastPresenceUpdateAt(), now, fatigue, now, current.awakeSince());
        return new PetSleepTransition(next, absenceDue
                ? PetSleepEvent.SLEEP_STARTED_AFTER_LOGOUT : PetSleepEvent.SLEEP_STARTED_FORCED);
    }

    static Instant dueAt(Instant awakeSince, Instant now, double fatigue,
            boolean online, PetSleepPolicy policy) {
        Instant threshold = now.plus(policy.maximumAwake());
        int rate = online ? policy.onlineFatiguePerHour() : policy.offlineFatiguePerHour();
        if (rate > 0) {
            long millis = (long) Math.ceil(Math.max(0, policy.napThreshold() - fatigue)
                    * 3_600_000.0 / rate);
            threshold = now.plusMillis(millis);
        }
        Instant due = threshold.isBefore(awakeSince.plus(policy.maximumAwake()))
                ? threshold : awakeSince.plus(policy.maximumAwake());
        Instant earliest = awakeSince.plus(policy.minimumAwake());
        return due.isBefore(earliest) ? earliest : due;
    }

    private static double accrued(PetSleepState state, Instant now, PetSleepPolicy policy) {
        if (state.sleeping()) return state.fatigue();
        long millis = Math.max(0, Duration.between(state.fatigueUpdatedAt(), now).toMillis());
        int rate = state.ownerNetworkOnline() ? policy.onlineFatiguePerHour()
                : policy.offlineFatiguePerHour();
        return Math.min(100, state.fatigue() + millis * rate / 3_600_000.0);
    }

    private static PetSleepTransition unchanged(PetSleepState current) {
        return new PetSleepTransition(current, PetSleepEvent.UNCHANGED);
    }
}
