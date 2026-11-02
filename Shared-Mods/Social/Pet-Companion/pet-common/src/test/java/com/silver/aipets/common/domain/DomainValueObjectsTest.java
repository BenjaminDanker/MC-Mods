package com.silver.aipets.common.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DomainValueObjectsTest {
    private static final BackendId BACKEND = new BackendId("sky-island");
    private static final DimensionId OVERWORLD = DimensionId.parse("minecraft:overworld");
    private static final WorldPosition POSITION = new WorldPosition(10.5, 64.0, -3.25);

    @Test
    void speciesIsAnExactTwoEntryAllowlistWithDogMappedToWolf() {
        assertEquals(2, PetSpecies.values().length);
        assertEquals(PetSpecies.CAT, PetSpecies.fromEntityTypeId("minecraft:cat"));
        assertEquals(PetSpecies.DOG, PetSpecies.fromEntityTypeId("minecraft:wolf"));
        assertEquals("dog", PetSpecies.DOG.displayName());
        assertThrows(
                IllegalArgumentException.class,
                () -> PetSpecies.fromEntityTypeId("minecraft:fox"));
    }

    @Test
    void resourceAndBackendIdsRequireCanonicalSyntax() {
        ResourceId id = ResourceId.parse("minecraft:pale/wolf_variant");
        assertEquals("minecraft", id.namespace());
        assertEquals("pale/wolf_variant", id.path());
        assertEquals("minecraft:pale/wolf_variant", id.toString());

        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("cat"));
        assertThrows(IllegalArgumentException.class, () -> ResourceId.parse("Minecraft:cat"));
        assertThrows(IllegalArgumentException.class, () -> new BackendId("Sky Island"));
        assertThrows(IllegalArgumentException.class, () -> new BackendId(" sky-island"));
    }

    @Test
    void positionRequiresFiniteCoordinatesAndUsesThreeDimensionalInclusiveDistance() {
        WorldPosition origin = new WorldPosition(0, 0, 0);
        WorldPosition endpoint = new WorldPosition(3, 4, 12);

        assertEquals(13.0, origin.distanceTo(endpoint));
        assertTrue(origin.isWithin(endpoint, 13.0));
        assertFalse(origin.isWithin(endpoint, 12.999));
        assertThrows(IllegalArgumentException.class, () -> new WorldPosition(Double.NaN, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WorldPosition(0, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> origin.isWithin(endpoint, -1));
    }

    @Test
    void defaultAppearanceBoundsAreInclusiveAndSpeciesSpecific() {
        AppearanceRules rules = AppearanceRules.defaults();

        PetAppearance catMin = PetAppearance.create(
                PetSpecies.CAT, "minecraft:black", 0.55, null, rules);
        PetAppearance catMax = PetAppearance.create(
                PetSpecies.CAT, "minecraft:tabby", 0.80, 42L, rules);
        PetAppearance dogMin = PetAppearance.create(
                PetSpecies.DOG, "minecraft:pale", 0.50, null, rules);
        PetAppearance dogMax = PetAppearance.create(
                PetSpecies.DOG, "minecraft:spotted", 0.78, null, rules);

        assertEquals(ResourceId.parse("minecraft:cat"), catMin.entityTypeId());
        assertEquals(OptionalLong.of(42L), catMax.appearanceSeed());
        assertEquals(ResourceId.parse("minecraft:wolf"), dogMin.entityTypeId());
        assertEquals(0.78, dogMax.scale());
        assertThrows(
                IllegalArgumentException.class,
                () -> PetAppearance.create(PetSpecies.CAT, "minecraft:black", 0.549, null, rules));
        assertThrows(
                IllegalArgumentException.class,
                () -> PetAppearance.create(PetSpecies.DOG, "minecraft:pale", 0.79, null, rules));
    }

    @Test
    void appearanceRulesAreInjectedAndDefensivelyCopied() {
        EnumMap<PetSpecies, ScaleRange> mutable = new EnumMap<>(PetSpecies.class);
        mutable.put(PetSpecies.CAT, new ScaleRange(0.40, 0.90));
        mutable.put(PetSpecies.DOG, new ScaleRange(0.40, 0.90));
        AppearanceRules rules = new AppearanceRules(mutable);
        mutable.put(PetSpecies.CAT, new ScaleRange(0.10, 0.20));

        PetAppearance custom = PetAppearance.create(
                PetSpecies.CAT, "minecraft:tabby", 0.85, null, rules);
        assertEquals(0.85, custom.scale());
        assertThrows(
                UnsupportedOperationException.class,
                () -> rules.scaleRanges().put(PetSpecies.CAT, new ScaleRange(0.1, 0.2)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AppearanceRules(Map.of(PetSpecies.CAT, new ScaleRange(0.5, 0.8))));
    }

    @Test
    void deterministicVariantRepairPreservesEveryOtherAppearanceField() {
        AppearanceRules rules = AppearanceRules.defaults();
        PetAppearance original = PetAppearance.create(
                PetSpecies.DOG, "minecraft:removed_variant", 0.66, 17L, rules);

        PetAppearance repaired = original.withVariant(ResourceId.parse("minecraft:pale"), rules);

        assertEquals(PetSpecies.DOG, repaired.species());
        assertEquals(ResourceId.parse("minecraft:pale"), repaired.variantId());
        assertEquals(original.scale(), repaired.scale());
        assertEquals(original.appearanceSeed(), repaired.appearanceSeed());
    }

    @Test
    void invalidScaleRangesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ScaleRange(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new ScaleRange(1, 0.5));
        assertThrows(IllegalArgumentException.class, () -> new ScaleRange(0.5, Double.NaN));
    }

    @Test
    void placedStateDistinguishesMaterializedFromVirtualized() {
        UUID entityId = UUID.randomUUID();
        PlacedPlacement live = PlacedPlacement.materialized(
                BACKEND, OVERWORLD, POSITION, entityId);
        PlacedPlacement virtualized = PlacedPlacement.virtualized(
                BACKEND, OVERWORLD, POSITION);

        assertEquals(PlacementState.PLACED, live.state());
        assertEquals(Optional.of(entityId), live.entityUuid());
        assertTrue(live.isMaterialized());
        assertFalse(virtualized.isMaterialized());
        assertEquals(POSITION, virtualized.position());
    }

    @Test
    void transferRequiresCompleteMetadataAndStrictlyLaterExpiry() {
        Instant started = Instant.parse("2026-08-30T12:05:00Z");
        UUID transferId = UUID.randomUUID();
        UUID sourceEntityId = UUID.randomUUID();
        TransferMetadata transfer = new TransferMetadata(
                transferId,
                BACKEND,
                sourceEntityId,
                new BackendId("ocean"),
                started,
                started.plusSeconds(30));

        assertEquals(PlacementState.TRANSFERRING, new TransferringPlacement(transfer).state());
        assertFalse(transfer.isExpiredAt(started.plusSeconds(29)));
        assertTrue(transfer.isExpiredAt(started.plusSeconds(30)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new TransferMetadata(
                        transferId,
                        BACKEND,
                        sourceEntityId,
                        new BackendId("ocean"),
                        started,
                        started));
    }
}
