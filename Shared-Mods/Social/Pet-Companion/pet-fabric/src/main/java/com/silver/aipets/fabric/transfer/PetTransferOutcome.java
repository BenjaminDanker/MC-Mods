package com.silver.aipets.fabric.transfer;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;
import java.util.Optional;

public record PetTransferOutcome(PetTransferStatus status, Optional<Pet> pet, String detail) {
    public PetTransferOutcome {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(detail, "detail");
        if (detail.isBlank() || detail.length() > 240) {
            throw new IllegalArgumentException("detail must contain 1..240 characters");
        }
    }

    public static PetTransferOutcome of(PetTransferStatus status, Pet pet, String detail) {
        return new PetTransferOutcome(status, Optional.ofNullable(pet), detail);
    }
}
