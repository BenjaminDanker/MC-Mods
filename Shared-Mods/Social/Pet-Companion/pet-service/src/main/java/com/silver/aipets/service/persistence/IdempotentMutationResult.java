package com.silver.aipets.service.persistence;

import com.silver.aipets.service.placement.PetMutationResult;

import java.util.Objects;
import java.util.Optional;

/** Outcome of reserving/replaying a durable operation key. */
public record IdempotentMutationResult(
        IdempotentMutationDisposition disposition,
        Optional<PetMutationResult> mutation) {
    public IdempotentMutationResult {
        Objects.requireNonNull(disposition, "disposition");
        mutation = Objects.requireNonNull(mutation, "mutation");
        boolean completed = disposition == IdempotentMutationDisposition.EXECUTED
                || disposition == IdempotentMutationDisposition.REPLAYED;
        if (completed != mutation.isPresent()) {
            throw new IllegalArgumentException("Only completed idempotency outcomes carry a mutation result");
        }
    }

    public static IdempotentMutationResult executed(PetMutationResult result) {
        return new IdempotentMutationResult(
                IdempotentMutationDisposition.EXECUTED, Optional.of(result));
    }

    public static IdempotentMutationResult replayed(PetMutationResult result) {
        return new IdempotentMutationResult(
                IdempotentMutationDisposition.REPLAYED, Optional.of(result));
    }

    public static IdempotentMutationResult keyConflict() {
        return new IdempotentMutationResult(
                IdempotentMutationDisposition.KEY_CONFLICT, Optional.empty());
    }

    public static IdempotentMutationResult inProgress() {
        return new IdempotentMutationResult(
                IdempotentMutationDisposition.IN_PROGRESS, Optional.empty());
    }
}
