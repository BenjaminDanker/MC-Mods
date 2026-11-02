package com.silver.aipets.fabric.recall;

import com.silver.aipets.common.domain.Pet;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PetRecallOutcome(
        PetRecallStatus status,
        Optional<Pet> pet,
        Optional<Instant> nextAvailableAt,
        String detail) {
    public PetRecallOutcome {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        nextAvailableAt = Objects.requireNonNull(nextAvailableAt, "nextAvailableAt");
        Objects.requireNonNull(detail, "detail");
    }

    public static PetRecallOutcome of(
            PetRecallStatus status, Pet pet, Instant nextAvailableAt, String detail) {
        return new PetRecallOutcome(
                status, Optional.ofNullable(pet), Optional.ofNullable(nextAvailableAt), detail);
    }
}
