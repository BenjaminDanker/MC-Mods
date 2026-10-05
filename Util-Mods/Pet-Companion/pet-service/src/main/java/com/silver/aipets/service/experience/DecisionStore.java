package com.silver.aipets.service.experience;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.dialogue.DialogueModelResponse;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Durable decision window and usage boundary, independent of the provider. */
public interface DecisionStore {
    Optional<PetDecisionPlanner.Decision> current(UUID petId, Instant now);
    /** Only a retry of the source event may receive its original words. */
    default Optional<PetDecisionPlanner.Decision> forEvent(UUID petId, UUID eventId, Instant now) {
        return Optional.empty();
    }
    void save(UUID petId, UUID eventId, PetDecisionPlanner.Decision decision,
            Instant now, Instant nextModelAt);
    /** An older model call must not replace a newer source event. */
    default boolean saveIfNewer(UUID petId, UUID eventId,
            PetDecisionPlanner.Decision decision, Instant now, Instant nextModelAt) {
        save(petId, eventId, decision, now, nextModelAt);
        return true;
    }
    /** The source entity and world must still own this intention after model latency. */
    default boolean saveForPlacement(Pet pet, UUID eventId,
            PetDecisionPlanner.Decision decision, Instant now, Instant nextModelAt) {
        return saveIfNewer(pet.petId(), eventId, decision, now, nextModelAt);
    }
    void usage(Pet pet, UUID requestId, String model, DialogueModelResponse result,
            String status, Instant now);
}
