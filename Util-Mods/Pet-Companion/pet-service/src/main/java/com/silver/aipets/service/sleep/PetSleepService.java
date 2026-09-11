package com.silver.aipets.service.sleep;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Coordinates whole-network presence and bounded restart-safe due processing. */
public final class PetSleepService {
    private final PetSleepStateStore store;
    private final PetSleepPolicy policy;
    private final Clock clock;
    private final Consumer<PetSleepTransition> transitionListener;
    private final Supplier<UUID> absenceIds;

    public PetSleepService(PetSleepStateStore store, PetSleepPolicy policy, Clock clock) {
        this(store, policy, clock, transition -> { });
    }

    public PetSleepService(
            PetSleepStateStore store,
            PetSleepPolicy policy,
            Clock clock,
            Consumer<PetSleepTransition> transitionListener) {
        this(store, policy, clock, transitionListener, UUID::randomUUID);
    }

    public PetSleepService(
            PetSleepStateStore store,
            PetSleepPolicy policy,
            Clock clock,
            Consumer<PetSleepTransition> transitionListener,
            Supplier<UUID> absenceIds) {
        this.store = Objects.requireNonNull(store, "store");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transitionListener = Objects.requireNonNull(transitionListener, "transitionListener");
        this.absenceIds = Objects.requireNonNull(absenceIds, "absenceIds");
    }

    public Optional<PetSleepTransition> ownerOnline(UUID ownerUuid) {
        return ownerOnline(ownerUuid, clock.instant());
    }

    public Optional<PetSleepTransition> ownerOnline(UUID ownerUuid, Instant occurredAt) {
        Objects.requireNonNull(occurredAt, "occurredAt");
        Optional<PetSleepTransition> transition = store.updateForOwner(ownerUuid,
                state -> PetSleepTransitions.networkPresence(state, true, null, occurredAt));
        transition.ifPresent(transitionListener);
        return transition;
    }

    public Optional<PetSleepTransition> ownerOffline(UUID ownerUuid, UUID absenceSessionId) {
        return ownerOffline(ownerUuid, absenceSessionId, clock.instant());
    }

    public Optional<PetSleepTransition> ownerOffline(
            UUID ownerUuid, UUID absenceSessionId, Instant occurredAt) {
        Objects.requireNonNull(absenceSessionId, "absenceSessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Optional<PetSleepTransition> transition = store.updateForOwner(ownerUuid,
                state -> PetSleepTransitions.networkPresence(
                        state, false, absenceSessionId, occurredAt));
        transition.ifPresent(transitionListener);
        return transition;
    }

    /** Reconciles persisted presence after a proxy restart without touching sleep state directly. */
    public int reconcileOnlineOwners(Set<UUID> onlineOwnerUuids, Instant occurredAt) {
        Objects.requireNonNull(onlineOwnerUuids, "onlineOwnerUuids");
        Objects.requireNonNull(occurredAt, "occurredAt");
        int changed = 0;
        for (UUID ownerUuid : store.findOwnerUuids()) {
            Optional<PetSleepTransition> transition = onlineOwnerUuids.contains(ownerUuid)
                    ? ownerOnline(ownerUuid, occurredAt)
                    : ownerOffline(ownerUuid, absenceIds.get(), occurredAt);
            if (transition.isPresent() && transition.orElseThrow().changed()) {
                changed++;
            }
        }
        return changed;
    }

    public PetSleepTransition evaluate(UUID petId) {
        Instant now = clock.instant();
        PetSleepTransition transition = store.update(
                petId, state -> PetSleepTransitions.evaluate(state, now, policy));
        transitionListener.accept(transition);
        return transition;
    }

    public List<PetSleepTransition> processDue(int limit) {
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        Instant now = clock.instant();
        List<PetSleepTransition> changed = new ArrayList<>();
        for (UUID petId : store.findDuePetIds(now, now.minus(policy.logoutDelay()), limit)) {
            PetSleepTransition result = store.update(
                    petId, state -> PetSleepTransitions.evaluate(state, now, policy));
            if (result.changed()) {
                changed.add(result);
                transitionListener.accept(result);
            }
        }
        return List.copyOf(changed);
    }
}
