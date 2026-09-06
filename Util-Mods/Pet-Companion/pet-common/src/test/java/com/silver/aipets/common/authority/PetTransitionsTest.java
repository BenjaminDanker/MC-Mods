package com.silver.aipets.common.authority;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.test.TestPets;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetTransitionsTest {
    private static final BackendId SOURCE = new BackendId("sky-island");
    private static final BackendId DESTINATION = new BackendId("ocean");
    private static final DimensionId OVERWORLD = DimensionId.parse("minecraft:overworld");
    private static final DimensionId NETHER = DimensionId.parse("minecraft:the_nether");
    private static final WorldPosition ENTITY_POSITION = new WorldPosition(10, 64, 10);
    private static final WorldPosition OWNER_POSITION = new WorldPosition(12, 64, 10);
    private static final UUID ENTITY_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID NEW_ENTITY_ID = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID TRANSFER_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final Instant PLACED_AT = TestPets.CREATED_AT.plusSeconds(10);

    @Test
    void placeCommitsExactAppearanceAndIncrementsRevision() {
        Pet held = TestPets.held();

        TransitionResult result = PetTransitions.place(held, new PetTransitions.Place(
                held.ownerUuid(), 0, SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID, PLACED_AT));

        assertTrue(result.applied());
        assertEquals(1, result.pet().recordVersion());
        assertSame(held.appearance(), result.pet().appearance());
        assertEquals(
                PlacedPlacement.materialized(SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID),
                result.pet().placement());
    }

    @Test
    void placeRejectsWrongOwnerStaleVersionAndAlreadyPlacedState() {
        Pet held = TestPets.held();
        var base = new PetTransitions.Place(
                held.ownerUuid(), 0, SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID, PLACED_AT);

        assertRejected(
                held,
                PetTransitions.place(held, new PetTransitions.Place(
                        UUID.randomUUID(), 0, SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID, PLACED_AT)),
                TransitionFailure.OWNER_MISMATCH);
        assertRejected(
                held,
                PetTransitions.place(held, new PetTransitions.Place(
                        held.ownerUuid(), 1, SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID, PLACED_AT)),
                TransitionFailure.VERSION_MISMATCH);

        Pet placed = PetTransitions.place(held, base).pet();
        assertRejected(
                placed,
                PetTransitions.place(placed, new PetTransitions.Place(
                        held.ownerUuid(), 1, SOURCE, OVERWORLD, ENTITY_POSITION, NEW_ENTITY_ID,
                        PLACED_AT.plusSeconds(1))),
                TransitionFailure.STATE_MISMATCH);
    }

    @Test
    void placementFailureCompensationRequiresExactCommittedRepresentation() {
        Pet placed = placedPet();

        assertRejected(
                placed,
                PetTransitions.compensatePlaceFailure(placed, new PetTransitions.CompensatePlaceFailure(
                        1, SOURCE, NEW_ENTITY_ID, PLACED_AT.plusSeconds(1))),
                TransitionFailure.ENTITY_UUID_MISMATCH);

        TransitionResult compensated = PetTransitions.compensatePlaceFailure(
                placed,
                new PetTransitions.CompensatePlaceFailure(
                        1, SOURCE, ENTITY_ID, PLACED_AT.plusSeconds(1)));
        assertTrue(compensated.applied());
        assertSame(HeldPlacement.INSTANCE, compensated.pet().placement());
        assertEquals(2, compensated.pet().recordVersion());
    }

    @Test
    void pickupRequiresExactOwnerContextIdentityAndThreeDimensionalRadius() {
        Pet placed = placedPet();

        assertRejected(
                placed,
                PetTransitions.pickup(placed, pickup(OVERWORLD, ENTITY_ID, new WorldPosition(15, 64, 10), 4)),
                TransitionFailure.OUT_OF_RANGE);
        assertRejected(
                placed,
                PetTransitions.pickup(placed, pickup(NETHER, ENTITY_ID, OWNER_POSITION, 4)),
                TransitionFailure.DIMENSION_MISMATCH);
        assertRejected(
                placed,
                PetTransitions.pickup(placed, pickup(OVERWORLD, NEW_ENTITY_ID, OWNER_POSITION, 4)),
                TransitionFailure.ENTITY_UUID_MISMATCH);

        TransitionResult result = PetTransitions.pickup(
                placed, pickup(OVERWORLD, ENTITY_ID, new WorldPosition(14, 64, 10), 4));
        assertTrue(result.applied());
        assertSame(HeldPlacement.INSTANCE, result.pet().placement());
    }

    @Test
    void transferIsReservedBeforeSourceRemovalAndClaimedExactlyOnce() {
        Pet placed = placedPet();
        TransferMetadata transfer = transfer(PLACED_AT.plusSeconds(1), PLACED_AT.plusSeconds(31));

        TransitionResult prepared = PetTransitions.prepareTransfer(
                placed,
                new PetTransitions.PrepareTransfer(
                        placed.ownerUuid(), 1, OVERWORLD, OWNER_POSITION, ENTITY_POSITION, 16, transfer));
        assertTrue(prepared.applied());
        assertEquals(new TransferringPlacement(transfer), prepared.pet().placement());
        assertEquals(2, prepared.pet().recordVersion());

        TransitionResult completed = PetTransitions.completeTransfer(
                prepared.pet(),
                new PetTransitions.CompleteTransfer(
                        2,
                        TRANSFER_ID,
                        DESTINATION,
                        OVERWORLD,
                        new WorldPosition(0, 70, 0),
                        NEW_ENTITY_ID,
                        PLACED_AT.plusSeconds(2)));
        assertTrue(completed.applied());
        assertEquals(3, completed.pet().recordVersion());
        assertEquals(DESTINATION, ((PlacedPlacement) completed.pet().placement()).backendId());

        assertRejected(
                completed.pet(),
                PetTransitions.completeTransfer(
                        completed.pet(),
                        new PetTransitions.CompleteTransfer(
                                3,
                                TRANSFER_ID,
                                DESTINATION,
                                OVERWORLD,
                                new WorldPosition(0, 70, 0),
                                UUID.randomUUID(),
                                PLACED_AT.plusSeconds(3))),
                TransitionFailure.STATE_MISMATCH);
    }

    @Test
    void transferChecksDistanceDestinationFreshUuidAndExpiry() {
        Pet placed = placedPet();
        Instant started = PLACED_AT.plusSeconds(1);
        TransferMetadata transfer = transfer(started, started.plusSeconds(30));

        assertRejected(
                placed,
                PetTransitions.prepareTransfer(
                        placed,
                        new PetTransitions.PrepareTransfer(
                                placed.ownerUuid(), 1, OVERWORLD,
                                new WorldPosition(100, 64, 100), ENTITY_POSITION, 16, transfer)),
                TransitionFailure.OUT_OF_RANGE);

        Pet transferring = PetTransitions.prepareTransfer(
                placed,
                new PetTransitions.PrepareTransfer(
                        placed.ownerUuid(), 1, OVERWORLD, OWNER_POSITION, ENTITY_POSITION, 16, transfer))
                .pet();
        assertRejected(
                transferring,
                PetTransitions.completeTransfer(
                        transferring,
                        new PetTransitions.CompleteTransfer(
                                2, TRANSFER_ID, SOURCE, OVERWORLD, ENTITY_POSITION,
                                NEW_ENTITY_ID, started.plusSeconds(1))),
                TransitionFailure.DESTINATION_MISMATCH);
        assertRejected(
                transferring,
                PetTransitions.completeTransfer(
                        transferring,
                        new PetTransitions.CompleteTransfer(
                                2, TRANSFER_ID, DESTINATION, OVERWORLD, ENTITY_POSITION,
                                ENTITY_ID, started.plusSeconds(1))),
                TransitionFailure.ENTITY_UUID_NOT_FRESH);
        assertRejected(
                transferring,
                PetTransitions.completeTransfer(
                        transferring,
                        new PetTransitions.CompleteTransfer(
                                2, TRANSFER_ID, DESTINATION, OVERWORLD, ENTITY_POSITION,
                                NEW_ENTITY_ID, transfer.expiresAt())),
                TransitionFailure.TRANSFER_EXPIRED);
    }

    @Test
    void transferExpiresToHeldOnlyAtPersistedDeadline() {
        Pet placed = placedPet();
        Instant started = PLACED_AT.plusSeconds(1);
        TransferMetadata transfer = transfer(started, started.plusSeconds(30));
        Pet transferring = PetTransitions.prepareTransfer(
                placed,
                new PetTransitions.PrepareTransfer(
                        placed.ownerUuid(), 1, OVERWORLD, OWNER_POSITION, ENTITY_POSITION, 16, transfer))
                .pet();

        assertRejected(
                transferring,
                PetTransitions.expireTransfer(
                        transferring,
                        new PetTransitions.ExpireTransfer(2, TRANSFER_ID, transfer.expiresAt().minusNanos(1))),
                TransitionFailure.TRANSFER_NOT_EXPIRED);

        TransitionResult expired = PetTransitions.expireTransfer(
                transferring,
                new PetTransitions.ExpireTransfer(2, TRANSFER_ID, transfer.expiresAt()));
        assertTrue(expired.applied());
        assertSame(HeldPlacement.INSTANCE, expired.pet().placement());
    }

    @Test
    void transitionNeverMovesAuthoritativeTimeBackwards() {
        Pet held = TestPets.held();
        TransitionResult result = PetTransitions.place(
                held,
                new PetTransitions.Place(
                        held.ownerUuid(), 0, SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID,
                        held.updatedAt().minusNanos(1)));

        assertRejected(held, result, TransitionFailure.TIMESTAMP_BEFORE_CURRENT);
    }

    private static Pet placedPet() {
        return PetTransitions.place(
                TestPets.held(),
                new PetTransitions.Place(
                        TestPets.OWNER_ID, 0, SOURCE, OVERWORLD, ENTITY_POSITION, ENTITY_ID, PLACED_AT))
                .pet();
    }

    private static PetTransitions.Pickup pickup(
            DimensionId dimensionId, UUID entityId, WorldPosition ownerPosition, double radius) {
        return new PetTransitions.Pickup(
                TestPets.OWNER_ID,
                1,
                SOURCE,
                dimensionId,
                entityId,
                ownerPosition,
                ENTITY_POSITION,
                radius,
                PLACED_AT.plusSeconds(1));
    }

    private static TransferMetadata transfer(Instant startedAt, Instant expiresAt) {
        return new TransferMetadata(
                TRANSFER_ID, SOURCE, ENTITY_ID, DESTINATION, startedAt, expiresAt);
    }

    private static void assertRejected(
            Pet expectedUnchanged, TransitionResult result, TransitionFailure failure) {
        assertFalse(result.applied());
        assertSame(expectedUnchanged, result.pet());
        assertEquals(failure, result.failureOrThrow());
    }
}
