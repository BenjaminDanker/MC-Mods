package com.silver.aipets.common.authority;

import com.silver.aipets.common.domain.Pet;
import java.util.Objects;
import java.util.Optional;

/** Immutable result: rejection always carries the unchanged current aggregate. */
public final class TransitionResult {
    private final Pet pet;
    private final TransitionFailure failure;

    private TransitionResult(Pet pet, TransitionFailure failure) {
        this.pet = Objects.requireNonNull(pet, "pet");
        this.failure = failure;
    }

    public static TransitionResult applied(Pet pet) {
        return new TransitionResult(pet, null);
    }

    public static TransitionResult rejected(Pet current, TransitionFailure failure) {
        return new TransitionResult(current, Objects.requireNonNull(failure, "failure"));
    }

    public boolean applied() {
        return failure == null;
    }

    public Pet pet() {
        return pet;
    }

    public Optional<TransitionFailure> failure() {
        return Optional.ofNullable(failure);
    }

    public TransitionFailure failureOrThrow() {
        if (failure == null) {
            throw new IllegalStateException("Applied transition has no failure");
        }
        return failure;
    }
}
