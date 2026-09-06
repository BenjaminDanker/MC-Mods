package com.silver.aipets.service.placement;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.authority.TransitionResult;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.service.persistence.CompareAndSetResult;
import com.silver.aipets.service.persistence.CompareAndSetStatus;
import com.silver.aipets.service.persistence.PetRepository;
import com.silver.aipets.service.persistence.IdempotencyRequest;
import com.silver.aipets.service.persistence.IdempotentMutationResult;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/** Applies pure transition rules and commits them with one repository CAS. */
public final class PetPlacementService {
    private static final Duration DEFAULT_LOCK_DURATION = Duration.ofSeconds(30);
    private static final Duration DEFAULT_RETENTION = Duration.ofDays(7);

    private final PetRepository repository;
    private final Clock clock;
    private final Duration retention;

    public PetPlacementService(PetRepository repository) {
        this(repository, Clock.systemUTC(), DEFAULT_RETENTION);
    }

    public PetPlacementService(PetRepository repository, Clock clock, Duration retention) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.retention = Objects.requireNonNull(retention, "retention");
        if (retention.compareTo(DEFAULT_LOCK_DURATION) <= 0) {
            throw new IllegalArgumentException("retention must exceed the operation lock duration");
        }
    }

    public PetMutationResult place(UUID petId, PetTransitions.Place command) {
        return mutate(petId, pet -> PetTransitions.place(pet, command));
    }

    public IdempotentMutationResult place(
            UUID operationId,
            UUID petId,
            PetTransitions.Place command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.place", operationId, petId, Optional.of(command.ownerUuid()),
                PetMutationFingerprints.forPet(petId, PetMutationFingerprints.place(command)),
                pet -> PetTransitions.place(pet, command));
    }

    public PetMutationResult compensatePlaceFailure(
            UUID petId, PetTransitions.CompensatePlaceFailure command) {
        return mutate(petId, pet -> PetTransitions.compensatePlaceFailure(pet, command));
    }

    public IdempotentMutationResult compensatePlaceFailure(
            UUID operationId,
            UUID petId,
            PetTransitions.CompensatePlaceFailure command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.compensate-place", operationId, petId, Optional.empty(),
                PetMutationFingerprints.forPet(petId, PetMutationFingerprints.compensate(command)),
                pet -> PetTransitions.compensatePlaceFailure(pet, command));
    }

    public PetMutationResult pickup(UUID petId, PetTransitions.Pickup command) {
        return mutate(petId, pet -> PetTransitions.pickup(pet, command));
    }

    public IdempotentMutationResult pickup(
            UUID operationId,
            UUID petId,
            PetTransitions.Pickup command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.pickup", operationId, petId, Optional.of(command.ownerUuid()),
                PetMutationFingerprints.forPet(petId, PetMutationFingerprints.pickup(command)),
                pet -> PetTransitions.pickup(pet, command));
    }

    public PetMutationResult prepareTransfer(UUID petId, PetTransitions.PrepareTransfer command) {
        return mutate(petId, pet -> PetTransitions.prepareTransfer(pet, command));
    }

    public IdempotentMutationResult prepareTransfer(
            UUID operationId, UUID petId, PetTransitions.PrepareTransfer command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.transfer.prepare", operationId, petId, Optional.of(command.ownerUuid()),
                PetMutationFingerprints.forPet(
                        petId, PetMutationFingerprints.prepareTransfer(command)),
                pet -> PetTransitions.prepareTransfer(pet, command));
    }

    public PetMutationResult completeTransfer(UUID petId, PetTransitions.CompleteTransfer command) {
        return mutate(petId, pet -> PetTransitions.completeTransfer(pet, command));
    }

    public IdempotentMutationResult completeTransfer(
            UUID operationId, UUID petId, PetTransitions.CompleteTransfer command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.transfer.complete", operationId, petId, Optional.empty(),
                PetMutationFingerprints.forPet(
                        petId, PetMutationFingerprints.completeTransfer(command)),
                pet -> PetTransitions.completeTransfer(pet, command));
    }

    public PetMutationResult expireTransfer(UUID petId, PetTransitions.ExpireTransfer command) {
        return mutate(petId, pet -> PetTransitions.expireTransfer(pet, command));
    }

    public IdempotentMutationResult expireTransfer(
            UUID operationId, UUID petId, PetTransitions.ExpireTransfer command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.transfer.expire", operationId, petId, Optional.empty(),
                PetMutationFingerprints.forPet(
                        petId, PetMutationFingerprints.expireTransfer(command)),
                pet -> PetTransitions.expireTransfer(pet, command));
    }

    public IdempotentMutationResult adminRecover(
            UUID operationId, UUID petId, PetTransitions.AdminRecover command) {
        Objects.requireNonNull(command, "command");
        return mutateIdempotently(
                "pet.admin.recover", operationId, petId, Optional.empty(),
                PetMutationFingerprints.forPet(
                        petId, PetMutationFingerprints.adminRecover(command)),
                pet -> PetTransitions.adminRecover(pet, command));
    }

    private PetMutationResult mutate(UUID petId, Function<Pet, TransitionResult> transition) {
        Objects.requireNonNull(petId, "petId");
        Pet current = repository.findById(petId).orElse(null);
        if (current == null) {
            return PetMutationResult.notFound();
        }

        TransitionResult proposed = transition.apply(current);
        if (!proposed.applied()) {
            return PetMutationResult.rejected(current, proposed.failureOrThrow());
        }

        CompareAndSetResult committed = repository.compareAndSet(
                petId, current.recordVersion(), proposed.pet());
        if (committed.status() == CompareAndSetStatus.UPDATED) {
            return PetMutationResult.applied(committed.pet().orElseThrow());
        }
        if (committed.status() == CompareAndSetStatus.NOT_FOUND) {
            return PetMutationResult.notFound();
        }
        return PetMutationResult.concurrentModification(committed.pet().orElseThrow());
    }

    private IdempotentMutationResult mutateIdempotently(
            String scope,
            UUID operationId,
            UUID petId,
            Optional<UUID> ownerUuid,
            String fingerprint,
            Function<Pet, TransitionResult> transition) {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(petId, "petId");
        Instant now = clock.instant();
        IdempotencyRequest request = new IdempotencyRequest(
                scope,
                operationId,
                fingerprint,
                petId,
                ownerUuid,
                now,
                now.plus(DEFAULT_LOCK_DURATION),
                now.plus(retention));
        return repository.mutateIdempotently(request, transition);
    }
}
