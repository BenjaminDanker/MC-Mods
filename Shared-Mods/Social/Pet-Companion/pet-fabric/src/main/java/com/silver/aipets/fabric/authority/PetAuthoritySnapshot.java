package com.silver.aipets.fabric.authority;

import com.silver.aipets.common.domain.Pet;

import java.util.Objects;

/** State required to materialize a pet without treating entity NBT as authority. */
public record PetAuthoritySnapshot(Pet pet, boolean sleeping, boolean aiAccessEnabled) {
    public PetAuthoritySnapshot {
        Objects.requireNonNull(pet, "pet");
    }

    public PetAuthoritySnapshot(Pet pet, boolean sleeping) {
        this(pet, sleeping, true);
    }
}
