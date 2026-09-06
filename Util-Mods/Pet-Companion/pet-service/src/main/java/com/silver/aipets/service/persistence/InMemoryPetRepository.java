package com.silver.aipets.service.persistence;

import com.silver.aipets.common.authority.TransitionResult;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.placement.PetMutationResult;

import java.util.HashMap;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.service.recall.RecallCompensationRequest;
import com.silver.aipets.service.recall.RecallOperationResult;
import com.silver.aipets.service.recall.RecallPersistenceRequest;

/** Thread-safe test/development adapter; production must use the MariaDB adapter. */
public final class InMemoryPetRepository implements PetRepository {
    private final Object monitor = new Object();
    private final Map<UUID, Pet> byId = new HashMap<>();
    private final Map<UUID, UUID> idByOwner = new HashMap<>();
    private final Map<OperationKey, StoredMutation> mutations = new HashMap<>();
    private final Map<UUID, StoredRecall> recalls = new HashMap<>();
    private final Map<PetPeriod, UUID> consumedRecallPeriods = new HashMap<>();

    @Override
    public Optional<Pet> findById(UUID petId) {
        Objects.requireNonNull(petId, "petId");
        synchronized (monitor) {
            return Optional.ofNullable(byId.get(petId));
        }
    }

    @Override
    public Optional<Pet> findByOwner(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        synchronized (monitor) {
            UUID petId = idByOwner.get(ownerUuid);
            return petId == null ? Optional.empty() : Optional.of(byId.get(petId));
        }
    }

    @Override
    public List<Pet> findExpiredTransfers(Instant dueAtInclusive, int limit) {
        Objects.requireNonNull(dueAtInclusive, "dueAtInclusive");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        synchronized (monitor) {
            return byId.values().stream()
                    .filter(pet -> pet.placement()
                            instanceof com.silver.aipets.common.domain.TransferringPlacement)
                    .filter(pet -> ((com.silver.aipets.common.domain.TransferringPlacement)
                            pet.placement()).transfer().isExpiredAt(dueAtInclusive))
                    .sorted(Comparator
                            .comparing((Pet pet) -> ((com.silver.aipets.common.domain.TransferringPlacement)
                                    pet.placement()).transfer().expiresAt())
                            .thenComparing(Pet::petId))
                    .limit(limit)
                    .toList();
        }
    }

    @Override
    public CreatePetResult createIfOwnerAbsent(Pet proposedPet) {
        Objects.requireNonNull(proposedPet, "proposedPet");
        synchronized (monitor) {
            UUID existingId = idByOwner.get(proposedPet.ownerUuid());
            if (existingId != null) {
                return CreatePetResult.existing(byId.get(existingId));
            }
            Pet idCollision = byId.get(proposedPet.petId());
            if (idCollision != null) {
                throw new IllegalStateException("Pet ID collision for a different owner");
            }
            byId.put(proposedPet.petId(), proposedPet);
            idByOwner.put(proposedPet.ownerUuid(), proposedPet.petId());
            return CreatePetResult.created(proposedPet);
        }
    }

    @Override
    public CompareAndSetResult compareAndSet(UUID petId, long expectedVersion, Pet replacement) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(replacement, "replacement");
        if (expectedVersion < 0) {
            throw new IllegalArgumentException("expectedVersion must be non-negative");
        }
        synchronized (monitor) {
            Pet current = byId.get(petId);
            if (current == null) {
                return CompareAndSetResult.notFound();
            }
            if (current.recordVersion() != expectedVersion) {
                return CompareAndSetResult.versionMismatch(current);
            }
            validateReplacement(current, replacement);
            byId.put(petId, replacement);
            return CompareAndSetResult.updated(replacement);
        }
    }

    @Override
    public IdempotentMutationResult mutateIdempotently(
            IdempotencyRequest request,
            Function<Pet, TransitionResult> transition) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(transition, "transition");
        synchronized (monitor) {
            OperationKey key = new OperationKey(request.scope(), request.operationId());
            StoredMutation stored = mutations.get(key);
            if (stored != null) {
                return stored.fingerprint().equals(request.requestFingerprint())
                        ? IdempotentMutationResult.replayed(stored.result())
                        : IdempotentMutationResult.keyConflict();
            }

            Pet current = byId.get(request.petId());
            PetMutationResult result;
            if (current == null) {
                result = PetMutationResult.notFound();
            } else {
                TransitionResult proposed = transition.apply(current);
                if (!proposed.applied()) {
                    result = PetMutationResult.rejected(current, proposed.failureOrThrow());
                } else {
                    Pet replacement = proposed.pet();
                    validateReplacement(current, replacement);
                    byId.put(request.petId(), replacement);
                    result = PetMutationResult.applied(replacement);
                }
            }
            mutations.put(key, new StoredMutation(request.requestFingerprint(), result));
            return IdempotentMutationResult.executed(result);
        }
    }

    @Override
    public RecallOperationResult recall(RecallPersistenceRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (monitor) {
            StoredRecall stored = recalls.get(request.operationId());
            if (stored != null) {
                return stored.fingerprint().equals(request.requestFingerprint())
                        ? RecallOperationResult.replayed(stored.result())
                        : RecallOperationResult.keyConflict();
            }
            Pet current = byId.get(request.petId());
            if (current == null) {
                PetRecallWireResult result = new PetRecallWireResult(
                        PetRecallWireStatus.NOT_FOUND, Optional.empty(), Optional.empty(), Optional.empty());
                recalls.put(request.operationId(), new StoredRecall(
                        request.requestFingerprint(), null, request.petId(), request.periodKey(), result));
                return RecallOperationResult.executed(result);
            }
            PetPeriod period = new PetPeriod(request.petId(), request.periodKey());
            if (consumedRecallPeriods.containsKey(period)) {
                PetRecallWireResult result = new PetRecallWireResult(
                        PetRecallWireStatus.UNAVAILABLE,
                        Optional.of(current), Optional.empty(), Optional.of(request.nextAvailableAt()));
                recalls.put(request.operationId(), new StoredRecall(
                        request.requestFingerprint(), null, request.petId(), request.periodKey(), result));
                return RecallOperationResult.executed(result);
            }
            TransitionResult proposed = PetTransitions.recall(current, request.command());
            PetRecallWireResult result;
            if (!proposed.applied()) {
                result = new PetRecallWireResult(
                        PetRecallWireStatus.REJECTED, Optional.of(current),
                        Optional.of(proposed.failureOrThrow()), Optional.empty());
            } else {
                Pet replacement = proposed.pet();
                validateReplacement(current, replacement);
                byId.put(request.petId(), replacement);
                consumedRecallPeriods.put(period, request.operationId());
                result = new PetRecallWireResult(
                        PetRecallWireStatus.APPLIED, Optional.of(replacement),
                        Optional.empty(), Optional.of(request.nextAvailableAt()));
            }
            recalls.put(request.operationId(), new StoredRecall(
                    request.requestFingerprint(), null, request.petId(), request.periodKey(), result));
            return RecallOperationResult.executed(result);
        }
    }

    @Override
    public RecallOperationResult compensateRecallFailure(RecallCompensationRequest request) {
        Objects.requireNonNull(request, "request");
        synchronized (monitor) {
            StoredRecall stored = recalls.get(request.recallOperationId());
            if (stored == null || !stored.petId().equals(request.petId())) {
                return RecallOperationResult.executed(new PetRecallWireResult(
                        PetRecallWireStatus.NOT_FOUND, Optional.empty(), Optional.empty(), Optional.empty()));
            }
            if (stored.result().status() == PetRecallWireStatus.COMPENSATED) {
                return stored.compensationFingerprint().equals(request.requestFingerprint())
                        ? RecallOperationResult.replayed(stored.result())
                        : RecallOperationResult.keyConflict();
            }
            if (stored.result().status() != PetRecallWireStatus.APPLIED) {
                return RecallOperationResult.replayed(stored.result());
            }
            Pet current = byId.get(request.petId());
            TransitionResult proposed = PetTransitions.compensateRecallFailure(
                    current, request.command());
            if (!proposed.applied()) {
                PetRecallWireResult rejected = new PetRecallWireResult(
                        PetRecallWireStatus.REJECTED, Optional.of(current),
                        Optional.of(proposed.failureOrThrow()), Optional.empty());
                return RecallOperationResult.executed(rejected);
            }
            Pet replacement = proposed.pet();
            validateReplacement(current, replacement);
            byId.put(request.petId(), replacement);
            consumedRecallPeriods.remove(new PetPeriod(request.petId(), stored.periodKey()));
            PetRecallWireResult compensated = new PetRecallWireResult(
                    PetRecallWireStatus.COMPENSATED, Optional.of(replacement),
                    Optional.empty(), Optional.empty());
            recalls.put(request.recallOperationId(), new StoredRecall(
                    stored.fingerprint(), request.requestFingerprint(),
                    stored.petId(), stored.periodKey(), compensated));
            return RecallOperationResult.executed(compensated);
        }
    }

    private static void validateReplacement(Pet current, Pet replacement) {
        if (!current.petId().equals(replacement.petId())
                || !current.ownerUuid().equals(replacement.ownerUuid())) {
            throw new IllegalArgumentException("CAS cannot replace pet or owner identity");
        }
        if (!current.createdAt().equals(replacement.createdAt())) {
            throw new IllegalArgumentException("CAS cannot replace creation time");
        }
        if (replacement.recordVersion() != current.recordVersion() + 1) {
            throw new IllegalArgumentException("CAS replacement must increment recordVersion exactly once");
        }
    }

    private record OperationKey(String scope, UUID operationId) {
    }

    private record StoredMutation(String fingerprint, PetMutationResult result) {
    }

    private record PetPeriod(UUID petId, String periodKey) {
    }

    private record StoredRecall(
            String fingerprint,
            String compensationFingerprint,
            UUID petId,
            String periodKey,
            PetRecallWireResult result) {
    }
}
