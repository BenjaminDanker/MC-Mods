package com.silver.aipets.service.persistence;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record CompareAndSetResult(CompareAndSetStatus status, Optional<Pet> pet) {
    public CompareAndSetResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        if (status == CompareAndSetStatus.NOT_FOUND && pet.isPresent()) {
            throw new IllegalArgumentException("NOT_FOUND cannot carry a pet");
        }
        if (status != CompareAndSetStatus.NOT_FOUND && pet.isEmpty()) {
            throw new IllegalArgumentException(status + " must carry the current pet");
        }
    }

    public static CompareAndSetResult updated(Pet pet) {
        return new CompareAndSetResult(CompareAndSetStatus.UPDATED, Optional.of(pet));
    }

    public static CompareAndSetResult notFound() {
        return new CompareAndSetResult(CompareAndSetStatus.NOT_FOUND, Optional.empty());
    }

    public static CompareAndSetResult versionMismatch(Pet current) {
        return new CompareAndSetResult(CompareAndSetStatus.VERSION_MISMATCH, Optional.of(current));
    }
}
