package com.silver.aipets.service.sleep;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Atomic persistence boundary for restart-safe sleep evaluation. */
public interface PetSleepStateStore {
    List<UUID> findOwnerUuids();

    Optional<PetSleepState> find(UUID petId);

    Optional<PetSleepTransition> updateForOwner(
            UUID ownerUuid, Function<PetSleepState, PetSleepTransition> transition);

    PetSleepTransition update(
            UUID petId, Function<PetSleepState, PetSleepTransition> transition);

    List<UUID> findDuePetIds(Instant now, Instant logoutDueAtOrBefore, int limit);
}
