package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record AdoptionResult(AdoptionStatus status, Optional<Pet> pet) {
    public AdoptionResult {
        Objects.requireNonNull(status, "status");
        pet = Objects.requireNonNull(pet, "pet");
        boolean denied = status == AdoptionStatus.SUBSCRIPTION_REQUIRED
                || status == AdoptionStatus.SPECIES_UNAVAILABLE;
        if (denied && pet.isPresent()) {
            throw new IllegalArgumentException("Denied adoption cannot carry a pet");
        }
        if (!denied && pet.isEmpty()) {
            throw new IllegalArgumentException(status + " must carry a pet");
        }
    }

    public static AdoptionResult created(Pet pet) {
        return new AdoptionResult(AdoptionStatus.CREATED, Optional.of(pet));
    }

    public static AdoptionResult existing(Pet pet) {
        return new AdoptionResult(AdoptionStatus.EXISTING, Optional.of(pet));
    }

    public static AdoptionResult subscriptionRequired() {
        return new AdoptionResult(AdoptionStatus.SUBSCRIPTION_REQUIRED, Optional.empty());
    }

    public static AdoptionResult speciesUnavailable() {
        return new AdoptionResult(AdoptionStatus.SPECIES_UNAVAILABLE, Optional.empty());
    }
}
