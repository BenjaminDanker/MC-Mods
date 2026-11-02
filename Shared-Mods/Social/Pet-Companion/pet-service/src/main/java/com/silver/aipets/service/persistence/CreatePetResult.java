package com.silver.aipets.service.persistence;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;

public record CreatePetResult(Pet pet, boolean created) {
    public CreatePetResult {
        Objects.requireNonNull(pet, "pet");
    }

    public static CreatePetResult created(Pet pet) {
        return new CreatePetResult(pet, true);
    }

    public static CreatePetResult existing(Pet pet) {
        return new CreatePetResult(pet, false);
    }
}
