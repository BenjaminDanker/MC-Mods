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

    @Test
    void temperamentReachesBothEndsAndEachPetGetsItsOwnCharacterLines() {
        PetRandomizer randomizer = randomizer(7L);
        int bold = 0;
        int timid = 0;
        Set<String> characters = new HashSet<>();
        for (int index = 0; index < 400; index++) {
            AdoptionProfile profile = randomizer.generate(PetSpecies.DOG, ADOPTED_AT);
            if (profile.traits().boldness() >= 65) bold++;
            if (profile.traits().boldness() <= 35) timid++;
            characters.add(profile.characterJson());
            assertEquals(5, com.google.gson.JsonParser.parseString(profile.characterJson())
                    .getAsJsonObject().getAsJsonArray("character").size());
        }
        assertTrue(bold > 80 && timid > 80, "extremes should be common: bold=" + bold + " timid=" + timid);
        assertTrue(characters.size() > 350, "character lines should rarely repeat: " + characters.size());
    }

    private static PetRandomizer randomizer(long seed) {
        return new PetRandomizer(
                AppearanceRules.defaults(),
                AppearanceCatalog.vanilla12110(),
                InitialMood.defaults(),
                new Random(seed));
    }

    private static void assertTemperamentBounds(AdoptionProfile profile) {
        assertTrue(profile.traits().curiosity() >= PetRandomizer.TEMPERAMENT_MINIMUM
                && profile.traits().curiosity() <= PetRandomizer.TEMPERAMENT_MAXIMUM);
        assertTrue(profile.traits().boldness() >= PetRandomizer.TEMPERAMENT_MINIMUM
                && profile.traits().boldness() <= PetRandomizer.TEMPERAMENT_MAXIMUM);
        assertTrue(profile.traits().playfulness() >= PetRandomizer.TEMPERAMENT_MINIMUM
                && profile.traits().playfulness() <= PetRandomizer.TEMPERAMENT_MAXIMUM);
        assertTrue(profile.traits().expressiveness() >= PetRandomizer.TEMPERAMENT_MINIMUM
                && profile.traits().expressiveness() <= PetRandomizer.TEMPERAMENT_MAXIMUM);
        assertTrue(profile.traits().independence() >= PetRandomizer.TEMPERAMENT_MINIMUM
                && profile.traits().independence() <= PetRandomizer.TEMPERAMENT_MAXIMUM);
    }

    private static void assertRelationshipDefaults(AdoptionProfile profile) {
        assertEquals(40, profile.traits().attachment());
        assertEquals(45, profile.traits().trust());
        assertEquals(40, profile.traits().security());
    }
}
