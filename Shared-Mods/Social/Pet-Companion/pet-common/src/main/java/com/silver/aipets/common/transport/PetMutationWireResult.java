package com.silver.aipets.common.transport;

import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

/** Transport-neutral authoritative mutation result. */
public record PetMutationWireResult(
        PetMutationWireStatus status,
        Optional<Pet> pet,
        Optional<TransitionFailure> failure) {
    public PetMutationWireResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        failure = Objects.requireNonNull(failure, "failure");
        if (status == PetMutationWireStatus.NOT_FOUND && (pet.isPresent() || failure.isPresent())) {
            throw new IllegalArgumentException("NOT_FOUND cannot carry pet/failure data");
        }
        if (status != PetMutationWireStatus.NOT_FOUND && pet.isEmpty()) {
            throw new IllegalArgumentException(status + " must carry a pet");
        }
        if (status == PetMutationWireStatus.REJECTED && failure.isEmpty()) {
            throw new IllegalArgumentException("REJECTED must carry a failure");
        }
        if (status != PetMutationWireStatus.REJECTED && failure.isPresent()) {
            throw new IllegalArgumentException(status + " cannot carry a failure");
        }
    }
}
