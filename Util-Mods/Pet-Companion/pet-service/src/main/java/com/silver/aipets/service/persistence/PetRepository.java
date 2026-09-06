package com.silver.aipets.service.persistence;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.authority.TransitionResult;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import com.silver.aipets.service.recall.RecallCompensationRequest;
import com.silver.aipets.service.recall.RecallOperationResult;
import com.silver.aipets.service.recall.RecallPersistenceRequest;

/** Persistence boundary used by authority services; implementations must make each method atomic. */
public interface PetRepository {
    Optional<Pet> findById(UUID petId);

    Optional<Pet> findByOwner(UUID ownerUuid);

    /** Returns a bounded stable batch of persisted transfer reservations due for recovery. */
    List<Pet> findExpiredTransfers(Instant dueAtInclusive, int limit);

    /** Enforces the unique owner invariant and returns the existing pet on an adoption retry/race. */
    CreatePetResult createIfOwnerAbsent(Pet proposedPet);

    /**
     * Persists {@code replacement} only when the current row still has {@code expectedVersion}.
     * Implementations must never emulate this as a read followed by an unconditional write.
     */
    CompareAndSetResult compareAndSet(UUID petId, long expectedVersion, Pet replacement);

    /** Atomically reserves/replays the key, applies the transition, and stores its exact result. */
    IdempotentMutationResult mutateIdempotently(
            IdempotencyRequest request,
            Function<Pet, TransitionResult> transition);

    /** Atomically checks/consumes a UTC period and commits the recalled placement. */
    RecallOperationResult recall(RecallPersistenceRequest request);

    /** Atomically returns the exact failed recall to held and releases its UTC period. */
    RecallOperationResult compensateRecallFailure(RecallCompensationRequest request);
}
