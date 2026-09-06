package com.silver.aipets.common.transport;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record PetAdoptionWireResult(PetAdoptionWireStatus status, Optional<Pet> pet) {
    public PetAdoptionWireResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        boolean denied = status == PetAdoptionWireStatus.SUBSCRIPTION_REQUIRED
                || status == PetAdoptionWireStatus.SPECIES_UNAVAILABLE;
        if (denied == pet.isPresent()) {
            throw new IllegalArgumentException("Adoption result pet presence does not match status");
        }
    }
}
