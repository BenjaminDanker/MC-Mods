package com.silver.structurereset;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PetEscortTest {
    /** Shaped like a Pet-Companion tameable after its mixin. */
    public static class FakePet {
        private final UUID owner;
        private final boolean pet;

        FakePet(UUID owner, boolean pet) {
            this.owner = owner;
            this.pet = pet;
        }

        public boolean aipets$isPet() {
            return pet;
        }

        public UUID aipets$getOwnerUuid() {
            return owner;
        }
    }

    @Test
    void recognisesOnlyTheOwnersPets() {
        UUID owner = UUID.randomUUID();
        assertTrue(PetEscort.isOwnedPet(new FakePet(owner, true), owner));
        assertFalse(PetEscort.isOwnedPet(new FakePet(UUID.randomUUID(), true), owner));
        assertFalse(PetEscort.isOwnedPet(new FakePet(owner, false), owner));
        assertFalse(PetEscort.isOwnedPet(new Object(), owner));
        assertFalse(PetEscort.isPet("a plain entity"));
    }
}
