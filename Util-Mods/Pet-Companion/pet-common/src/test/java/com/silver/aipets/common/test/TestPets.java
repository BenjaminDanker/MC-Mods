package com.silver.aipets.common.test;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetPlacement;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import java.time.Instant;
import java.util.UUID;

public final class TestPets {
    public static final UUID PET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    public static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    public static final Instant CREATED_AT = Instant.parse("2026-08-30T12:00:00Z");

    private TestPets() {
    }

    public static Pet held() {
        PetAppearance appearance = PetAppearance.create(
                PetSpecies.CAT,
                "minecraft:tabby",
                0.67,
                987654321L,
                AppearanceRules.defaults());
        return Pet.adopted(
                PET_ID,
                OWNER_ID,
                "Mochi",
                appearance,
                PetTraits.initial(50, 45, 60, 55, 40, CREATED_AT),
                PetMood.initial(60, 20, 10, 15, CREATED_AT),
                CREATED_AT);
    }

    public static Pet withPlacement(
            Pet source, PetPlacement placement, long version, Instant updatedAt) {
        return new Pet(
                source.petId(),
                source.ownerUuid(),
                source.name(),
                source.appearance(),
                source.traits(),
                source.mood(),
                placement,
                version,
                source.createdAt(),
                updatedAt);
    }
}
