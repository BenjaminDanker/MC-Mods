package com.silver.aipets.fabric.placement;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record PetPickupOutcome(PetPickupStatus status, Optional<Pet> pet, String detail) {
    public PetPickupOutcome {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        detail = Objects.requireNonNull(detail, "detail");
    }

    public static PetPickupOutcome of(PetPickupStatus status, Pet pet, String detail) {
        return new PetPickupOutcome(status, Optional.ofNullable(pet), detail);
    }
}
