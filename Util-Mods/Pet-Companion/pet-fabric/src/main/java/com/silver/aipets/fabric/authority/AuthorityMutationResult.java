package com.silver.aipets.fabric.authority;

import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

/** Transport-neutral response from the central authority boundary. */
public record AuthorityMutationResult(
        AuthorityMutationStatus status,
        Optional<Pet> pet,
        Optional<TransitionFailure> failure) {
    public AuthorityMutationResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        failure = Objects.requireNonNull(failure, "failure");
        if (status == AuthorityMutationStatus.NOT_FOUND && (pet.isPresent() || failure.isPresent())) {
            throw new IllegalArgumentException("NOT_FOUND cannot carry pet/failure data");
        }
        if (status != AuthorityMutationStatus.NOT_FOUND && pet.isEmpty()) {
            throw new IllegalArgumentException(status + " must carry a pet");
        }
        if (status == AuthorityMutationStatus.REJECTED && failure.isEmpty()) {
            throw new IllegalArgumentException("REJECTED must carry a transition failure");
        }
        if (status != AuthorityMutationStatus.REJECTED && failure.isPresent()) {
            throw new IllegalArgumentException(status + " cannot carry a transition failure");
        }
    }

    public static AuthorityMutationResult applied(Pet pet) {
        return new AuthorityMutationResult(
                AuthorityMutationStatus.APPLIED,
                Optional.of(Objects.requireNonNull(pet, "pet")),
                Optional.empty());
    }

    public static AuthorityMutationResult rejected(Pet pet, TransitionFailure failure) {
        return new AuthorityMutationResult(
                AuthorityMutationStatus.REJECTED,
                Optional.of(Objects.requireNonNull(pet, "pet")),
                Optional.of(Objects.requireNonNull(failure, "failure")));
    }

    public static AuthorityMutationResult notFound() {
        return new AuthorityMutationResult(
                AuthorityMutationStatus.NOT_FOUND,
                Optional.empty(),
                Optional.empty());
    }

    public static AuthorityMutationResult concurrentModification(Pet current) {
        return new AuthorityMutationResult(
                AuthorityMutationStatus.CONCURRENT_MODIFICATION,
                Optional.of(Objects.requireNonNull(current, "current")),
                Optional.empty());
    }
}
