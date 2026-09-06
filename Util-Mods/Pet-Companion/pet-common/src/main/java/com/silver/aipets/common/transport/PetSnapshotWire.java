package com.silver.aipets.common.transport;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;

public record PetSnapshotWire(Pet pet, boolean sleeping, boolean aiAccessEnabled) {
    public PetSnapshotWire {
        Objects.requireNonNull(pet, "pet");
    }

    public PetSnapshotWire(Pet pet, boolean sleeping) {
        this(pet, sleeping, true);
    }
}
