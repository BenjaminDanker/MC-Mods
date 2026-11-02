package com.silver.aipets.common.transport;

import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.Pet;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Result of a monthly recall workflow operation. */
public record PetRecallWireResult(
        PetRecallWireStatus status,
        Optional<Pet> pet,
        Optional<TransitionFailure> failure,
        Optional<Instant> nextAvailableAt) {
    public PetRecallWireResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        failure = Objects.requireNonNull(failure, "failure");
        nextAvailableAt = Objects.requireNonNull(nextAvailableAt, "nextAvailableAt");
        if (status == PetRecallWireStatus.NOT_FOUND && pet.isPresent()) {
            throw new IllegalArgumentException("NOT_FOUND cannot carry a pet");
        }
        if (status != PetRecallWireStatus.NOT_FOUND && pet.isEmpty()) {
            throw new IllegalArgumentException(status + " must carry a pet");
        }
        if (status == PetRecallWireStatus.REJECTED && failure.isEmpty()) {
            throw new IllegalArgumentException("REJECTED requires a transition failure");
        }
        if (status != PetRecallWireStatus.REJECTED && failure.isPresent()) {
            throw new IllegalArgumentException(status + " cannot carry a transition failure");
        }
        if (status == PetRecallWireStatus.UNAVAILABLE && nextAvailableAt.isEmpty()) {
            throw new IllegalArgumentException("UNAVAILABLE requires next availability");
        }
    }
}
