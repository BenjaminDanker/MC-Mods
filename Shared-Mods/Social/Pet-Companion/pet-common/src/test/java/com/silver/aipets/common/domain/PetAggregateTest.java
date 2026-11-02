package com.silver.aipets.common.domain;

import static com.silver.aipets.common.test.TestPets.CREATED_AT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.silver.aipets.common.test.TestPets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PetAggregateTest {
    @Test
    void adoptionBeginsHeldAtRevisionZeroWithoutRerollingSuppliedState() {
        Pet pet = TestPets.held();

        assertEquals(PlacementState.HELD, pet.placementState());
        assertSame(HeldPlacement.INSTANCE, pet.placement());
        assertEquals(0, pet.recordVersion());
        assertEquals(CREATED_AT, pet.createdAt());
        assertEquals(CREATED_AT, pet.updatedAt());
        assertEquals(ResourceId.parse("minecraft:tabby"), pet.appearance().variantId());
        assertEquals(0.67, pet.appearance().scale());
    }

    @Test
    void petRejectsInvalidVersionTimestampAndDisplayName() {
        Pet valid = TestPets.held();

        assertThrows(
                IllegalArgumentException.class,
                () -> copy(valid, "Mochi", -1, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(valid, "Mochi", 0, CREATED_AT.minusSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(valid, "", 0, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(valid, " Mochi", 0, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(valid, "Mo\u00a7chi", 0, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(valid, "x".repeat(Pet.MAX_NAME_CODE_POINTS + 1), 0, CREATED_AT));
    }

    @Test
    void traitsCoverEveryAuthoritativeDimensionAndUseSpecifiedRelationshipDefaults() {
        PetTraits traits = PetTraits.initial(35, 40, 50, 60, 65, CREATED_AT);

        assertEquals(35, traits.value(TraitName.CURIOSITY));
        assertEquals(40, traits.value(TraitName.BOLDNESS));
        assertEquals(50, traits.value(TraitName.PLAYFULNESS));
        assertEquals(60, traits.value(TraitName.EXPRESSIVENESS));
        assertEquals(65, traits.value(TraitName.INDEPENDENCE));
        assertEquals(40, traits.value(TraitName.ATTACHMENT));
        assertEquals(45, traits.value(TraitName.TRUST));
        assertEquals(40, traits.value(TraitName.SECURITY));
        assertEquals(TraitCategory.TEMPERAMENT, TraitName.CURIOSITY.category());
        assertEquals(TraitCategory.RELATIONSHIP, TraitName.TRUST.category());
    }

    @Test
    void traitsEnforceInclusiveZeroToOneHundredBoundsAndNonnegativeSummaryVersion() {
        PetTraits boundaries = new PetTraits(
                0, 100, 0, 100, 0, 100, 0, 100, "summary", 0, CREATED_AT);
        assertEquals(0, boundaries.curiosity());
        assertEquals(100, boundaries.boldness());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PetTraits(-1, 50, 50, 50, 50, 50, 50, 50, "", 0, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PetTraits(50, 50, 50, 50, 50, 50, 50, 101, "", 0, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PetTraits(50, 50, 50, 50, 50, 50, 50, 50, "", -1, CREATED_AT));
    }

    @Test
    void moodIsBoundedAndCannotClaimAnUpdateBeforeItsDecay() {
        PetMood mood = PetMood.initial(0, 25, 75, 100, CREATED_AT);

        assertEquals(0, mood.value(MoodDimension.CONTENT));
        assertEquals(25, mood.value(MoodDimension.EXCITED));
        assertEquals(75, mood.value(MoodDimension.ANXIOUS));
        assertEquals(100, mood.value(MoodDimension.TIRED));
        assertThrows(
                IllegalArgumentException.class,
                () -> PetMood.initial(101, 0, 0, 0, CREATED_AT));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PetMood(50, 50, 50, 50, CREATED_AT, CREATED_AT.minusSeconds(1)));
    }

    private static Pet copy(Pet source, String name, long version, Instant updatedAt) {
        return new Pet(
                source.petId(),
                source.ownerUuid(),
                name,
                source.appearance(),
                source.traits(),
                source.mood(),
                source.placement(),
                version,
                source.createdAt(),
                updatedAt);
    }
}
