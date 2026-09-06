package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.PetSpecies;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetRandomizerTest {
    private static final Instant ADOPTED_AT = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void generatesOnlyAllowedCatAppearancesAndBoundedTraits() {
        PetRandomizer randomizer = randomizer(42L);
        Set<String> observedVariants = new HashSet<>();

        for (int index = 0; index < 2_000; index++) {
            AdoptionProfile profile = randomizer.generate(PetSpecies.CAT, ADOPTED_AT);
            observedVariants.add(profile.appearance().variantId().value());
            assertTrue(AppearanceRules.defaults().scaleRange(PetSpecies.CAT)
                    .contains(profile.appearance().scale()));
            assertTemperamentBounds(profile);
            assertRelationshipDefaults(profile);
        }

        assertTrue(AppearanceCatalog.vanilla12110().variantsFor(PetSpecies.CAT).stream()
                .map(id -> id.value())
                .allMatch(observedVariants::contains));
    }

    @Test
    void generatesOnlyAllowedDogAppearancesAndBoundedTraits() {
        PetRandomizer randomizer = randomizer(84L);
        Set<String> observedVariants = new HashSet<>();

        for (int index = 0; index < 2_000; index++) {
            AdoptionProfile profile = randomizer.generate(PetSpecies.DOG, ADOPTED_AT);
            observedVariants.add(profile.appearance().variantId().value());
            assertTrue(AppearanceRules.defaults().scaleRange(PetSpecies.DOG)
                    .contains(profile.appearance().scale()));
            assertTemperamentBounds(profile);
            assertRelationshipDefaults(profile);
        }

        assertTrue(AppearanceCatalog.vanilla12110().variantsFor(PetSpecies.DOG).stream()
                .map(id -> id.value())
                .allMatch(observedVariants::contains));
    }

    @Test
    void fixedRandomSeedProducesTheSameFirstAdoptionProfile() {
        AdoptionProfile first = randomizer(12345L).generate(PetSpecies.CAT, ADOPTED_AT);
        AdoptionProfile second = randomizer(12345L).generate(PetSpecies.CAT, ADOPTED_AT);

        assertEquals(first, second);
    }

    private static PetRandomizer randomizer(long seed) {
        return new PetRandomizer(
                AppearanceRules.defaults(),
                AppearanceCatalog.vanilla12110(),
                InitialMood.defaults(),
                new Random(seed));
    }

    private static void assertTemperamentBounds(AdoptionProfile profile) {
        assertTrue(profile.traits().curiosity() >= 35 && profile.traits().curiosity() <= 65);
        assertTrue(profile.traits().boldness() >= 35 && profile.traits().boldness() <= 65);
        assertTrue(profile.traits().playfulness() >= 35 && profile.traits().playfulness() <= 65);
        assertTrue(profile.traits().expressiveness() >= 35 && profile.traits().expressiveness() <= 65);
        assertTrue(profile.traits().independence() >= 35 && profile.traits().independence() <= 65);
    }

    private static void assertRelationshipDefaults(AdoptionProfile profile) {
        assertEquals(40, profile.traits().attachment());
        assertEquals(45, profile.traits().trust());
        assertEquals(40, profile.traits().security());
    }
}
