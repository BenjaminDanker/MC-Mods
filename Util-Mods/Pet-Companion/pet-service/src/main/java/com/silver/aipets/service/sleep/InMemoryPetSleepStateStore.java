package com.silver.aipets.service.sleep;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Thread-safe development/fake-clock sleep store. */
public final class InMemoryPetSleepStateStore implements PetSleepStateStore {
    private final Object monitor = new Object();
    private final Map<UUID, PetSleepState> states = new HashMap<>();
    private final Map<UUID, UUID> petByOwner = new HashMap<>();

    public void put(UUID ownerUuid, PetSleepState state) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(state, "state");
        synchronized (monitor) {
            UUID existingPet = petByOwner.putIfAbsent(ownerUuid, state.petId());
            if (existingPet != null && !existingPet.equals(state.petId())) {
                throw new IllegalStateException("Owner already has another sleep state");
            }
            states.put(state.petId(), state);
        }
    }

    @Override
    public List<UUID> findOwnerUuids() {
        synchronized (monitor) {
            return List.copyOf(petByOwner.keySet());
        }
    }

    @Override
    public Optional<PetSleepState> find(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        synchronized (monitor) {
            return Optional.ofNullable(states.get(petId));
        }
    }

    @Override
    public Optional<PetSleepTransition> updateForOwner(
            UUID ownerUuid, Function<PetSleepState, PetSleepTransition> transition) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(transition, "transition");
        synchronized (monitor) {
            UUID petId = petByOwner.get(ownerUuid);
            return petId == null ? Optional.empty() : Optional.of(updateLocked(petId, transition));
        }
    }

    @Override
    public PetSleepTransition update(
            UUID petId, Function<PetSleepState, PetSleepTransition> transition) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(transition, "transition");
        synchronized (monitor) {
            return updateLocked(petId, transition);
        }
    }

    @Override
    public List<UUID> findDuePetIds(Instant now, Instant logoutDueAtOrBefore, int limit) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(logoutDueAtOrBefore, "logoutDueAtOrBefore");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        synchronized (monitor) {
            List<PetSleepState> ordered = new ArrayList<>(states.values());
            ordered.sort(Comparator.comparing(PetSleepState::updatedAt)
                    .thenComparing(state -> state.petId().toString()));
            return ordered.stream()
                    .filter(state -> isPotentiallyDue(state, now, logoutDueAtOrBefore))
                    .limit(limit)
                    .map(PetSleepState::petId)
                    .toList();
        }
    }

    private PetSleepTransition updateLocked(
            UUID petId, Function<PetSleepState, PetSleepTransition> transition) {
        PetSleepState current = states.get(petId);
        if (current == null) {
            throw new IllegalArgumentException("Unknown pet sleep state");
        }
        PetSleepTransition result = Objects.requireNonNull(transition.apply(current), "result");
        if (!result.state().petId().equals(petId)) {
            throw new IllegalArgumentException("Sleep transition changed pet identity");
        }
        if (result.changed()) {
            states.put(petId, result.state());
        }
        return result;
    }

    static boolean isPotentiallyDue(
            PetSleepState state, Instant now, Instant logoutDueAtOrBefore) {
        if (state.sleeping()) {
            return !now.isBefore(state.sleepEndsAt().orElseThrow());
        }
        if (!state.ownerNetworkOnline() && state.absenceSleepTriggered()) {
            return false;
        }
        return !now.isBefore(state.forcedSleepDueAt())
                || state.ownerLastLogoutAt()
                .map(logout -> !logout.isAfter(logoutDueAtOrBefore))
                .orElse(false);
    }
}
