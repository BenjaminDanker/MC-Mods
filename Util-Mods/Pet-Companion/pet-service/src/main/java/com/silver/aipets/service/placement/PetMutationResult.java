package com.silver.aipets.service.placement;

import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record PetMutationResult(
        PetMutationStatus status,
        Optional<Pet> pet,
        Optional<TransitionFailure> failure) {
    public PetMutationResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        failure = Objects.requireNonNull(failure, "failure");
        if (status == PetMutationStatus.NOT_FOUND && (pet.isPresent() || failure.isPresent())) {
            throw new IllegalArgumentException("NOT_FOUND cannot carry pet/failure data");
        }
        if (status != PetMutationStatus.NOT_FOUND && pet.isEmpty()) {
            throw new IllegalArgumentException(status + " must carry a pet");
        }
        if (status == PetMutationStatus.REJECTED && failure.isEmpty()) {
            throw new IllegalArgumentException("REJECTED must carry a transition failure");
        }
        if (status != PetMutationStatus.REJECTED && failure.isPresent()) {
            throw new IllegalArgumentException(status + " cannot carry a transition failure");
        }
    }

    public static PetMutationResult applied(Pet pet) {
        return new PetMutationResult(
                PetMutationStatus.APPLIED, Optional.of(pet), Optional.empty());
    }

    public static PetMutationResult rejected(Pet pet, TransitionFailure failure) {
        return new PetMutationResult(
                PetMutationStatus.REJECTED, Optional.of(pet), Optional.of(failure));
    }

    public static PetMutationResult notFound() {
        return new PetMutationResult(
                PetMutationStatus.NOT_FOUND, Optional.empty(), Optional.empty());
    }

    public static PetMutationResult concurrentModification(Pet current) {
        return new PetMutationResult(
                PetMutationStatus.CONCURRENT_MODIFICATION,
                Optional.of(current),
                Optional.empty());
    }
}
