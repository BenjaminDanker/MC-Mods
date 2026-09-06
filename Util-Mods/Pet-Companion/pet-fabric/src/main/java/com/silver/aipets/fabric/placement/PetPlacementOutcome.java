package com.silver.aipets.fabric.placement;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record PetPlacementOutcome(
        PetPlacementStatus status,
        Optional<Pet> pet,
        String detail) {
    public PetPlacementOutcome {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static PetPlacementOutcome of(PetPlacementStatus status, Pet pet, String detail) {
        return new PetPlacementOutcome(status, Optional.ofNullable(pet), detail);
    }
}
