package com.silver.aipets.service.transfer;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.transport.PetMutationWireCodec;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.service.persistence.IdempotentMutationDisposition;
import com.silver.aipets.service.persistence.InMemoryPetRepository;
import com.silver.aipets.service.placement.PetMutationStatus;
import com.silver.aipets.service.placement.PetPlacementService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetTransferLifecycleTest {
    private static final UUID PET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID SOURCE_ENTITY = UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID DESTINATION_ENTITY = UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID TRANSFER_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final BackendId SOURCE = new BackendId("sky-island");
    private static final BackendId DESTINATION = new BackendId("ocean");
    private static final DimensionId OVERWORLD = DimensionId.parse("minecraft:overworld");
    private static final WorldPosition SOURCE_POSITION = new WorldPosition(10, 64, 10);
    private static final WorldPosition DESTINATION_POSITION = new WorldPosition(2, 70, 2);
    private static final Instant CREATED = Instant.parse("2026-08-30T12:00:00Z");
    private static final Instant STARTED = CREATED.plusSeconds(20);
    private static final Instant EXPIRES = STARTED.plusSeconds(30);

    @Test
    void durableTransferFlowIsBoundedIdempotentOneShotAndRecoverable() {
        InMemoryPetRepository repository = placedRepository();
        PetPlacementService service = service(repository, STARTED);
        TransferMetadata metadata = new TransferMetadata(
                TRANSFER_ID, SOURCE, SOURCE_ENTITY, DESTINATION, STARTED, EXPIRES);
        PetTransitions.PrepareTransfer far = prepare(metadata, new WorldPosition(100, 64, 100));

        var farResult = service.prepareTransfer(UUID.randomUUID(), PET_ID, far)
                .mutation().orElseThrow();
        assertEquals(PetMutationStatus.REJECTED, farResult.status());
        assertEquals(TransitionFailure.OUT_OF_RANGE, farResult.failure().orElseThrow());
        assertTrue(repository.findById(PET_ID).orElseThrow().placement() instanceof PlacedPlacement);

        PetTransitions.PrepareTransfer near = prepare(metadata, new WorldPosition(11, 64, 10));
        PetMutationWireCodec wire = new PetMutationWireCodec(
                new PetWireCodec(AppearanceRules.defaults()));
        assertEquals(near, wire.decodePrepareTransfer(wire.encodePrepareTransfer(near)));
        var prepared = service.prepareTransfer(TRANSFER_ID, PET_ID, near);
        var preparedReplay = service.prepareTransfer(TRANSFER_ID, PET_ID, near);
        assertEquals(IdempotentMutationDisposition.EXECUTED, prepared.disposition());
        assertEquals(IdempotentMutationDisposition.REPLAYED, preparedReplay.disposition());
        Pet transferringPet = prepared.mutation().orElseThrow().pet().orElseThrow();
        assertEquals(new TransferringPlacement(metadata), transferringPet.placement());
        assertEquals(prepared.mutation(), preparedReplay.mutation());

        var wrongDestination = service.completeTransfer(
                        UUID.randomUUID(), PET_ID,
                        new PetTransitions.CompleteTransfer(
                                2, TRANSFER_ID, new BackendId("waiting-lobby"), OVERWORLD,
                                DESTINATION_POSITION, DESTINATION_ENTITY, STARTED.plusSeconds(1)))
                .mutation().orElseThrow();
        assertEquals(PetMutationStatus.REJECTED, wrongDestination.status());
        assertEquals(TransitionFailure.DESTINATION_MISMATCH,
                wrongDestination.failure().orElseThrow());

        PetTransitions.CompleteTransfer wrongId = complete(UUID.randomUUID());
        var wrong = service.completeTransfer(UUID.randomUUID(), PET_ID, wrongId)
                .mutation().orElseThrow();
        assertEquals(PetMutationStatus.REJECTED, wrong.status());
        assertEquals(TransitionFailure.TRANSFER_ID_MISMATCH, wrong.failure().orElseThrow());

        PetTransitions.CompleteTransfer complete = complete(TRANSFER_ID);
        assertEquals(complete, wire.decodeCompleteTransfer(wire.encodeCompleteTransfer(complete)));
        UUID completionOperation = UUID.randomUUID();
        var completed = service.completeTransfer(completionOperation, PET_ID, complete);
        var completionReplay = service.completeTransfer(completionOperation, PET_ID, complete);
        assertEquals(PetMutationStatus.APPLIED, completed.mutation().orElseThrow().status());
        assertEquals(IdempotentMutationDisposition.REPLAYED, completionReplay.disposition());
        Pet destinationPet = completed.mutation().orElseThrow().pet().orElseThrow();
        assertEquals(DESTINATION, ((PlacedPlacement) destinationPet.placement()).backendId());
        assertEquals(DESTINATION_ENTITY,
                ((PlacedPlacement) destinationPet.placement()).entityUuid().orElseThrow());
        var secondClaim = service.completeTransfer(
                UUID.randomUUID(), PET_ID,
                new PetTransitions.CompleteTransfer(
                        destinationPet.recordVersion(), TRANSFER_ID, DESTINATION, OVERWORLD,
                        DESTINATION_POSITION, UUID.randomUUID(), STARTED.plusSeconds(2)))
                .mutation().orElseThrow();
        assertEquals(PetMutationStatus.REJECTED, secondClaim.status());
        assertEquals(TransitionFailure.STATE_MISMATCH, secondClaim.failure().orElseThrow());

        var compensated = service.compensatePlaceFailure(
                UUID.randomUUID(), PET_ID,
                new PetTransitions.CompensatePlaceFailure(
                        destinationPet.recordVersion(), DESTINATION, DESTINATION_ENTITY,
                        STARTED.plusSeconds(3)))
                .mutation().orElseThrow();
        assertEquals(PetMutationStatus.APPLIED, compensated.status());
        assertSame(HeldPlacement.INSTANCE, compensated.pet().orElseThrow().placement());

        InMemoryPetRepository restartedRepository = placedRepository();
        PetPlacementService beforeRestart = service(restartedRepository, STARTED);
        assertEquals(PetMutationStatus.APPLIED,
                beforeRestart.prepareTransfer(TRANSFER_ID, PET_ID, near)
                        .mutation().orElseThrow().status());
        PetPlacementService afterRestart = service(restartedRepository, EXPIRES.plusSeconds(1));
        PetTransferExpiryWorker restartedWorker = new PetTransferExpiryWorker(
                restartedRepository, afterRestart,
                Clock.fixed(EXPIRES.plusSeconds(1), ZoneOffset.UTC));
        assertEquals(new PetTransferExpiryRun(1, 1, 0), restartedWorker.processDue(10));
        assertSame(HeldPlacement.INSTANCE,
                restartedRepository.findById(PET_ID).orElseThrow().placement());
        assertEquals(new PetTransferExpiryRun(0, 0, 0), restartedWorker.processDue(10));
    }

    private static PetTransitions.PrepareTransfer prepare(
            TransferMetadata metadata, WorldPosition ownerPosition) {
        return new PetTransitions.PrepareTransfer(
                OWNER_ID, 1, OVERWORLD, ownerPosition, SOURCE_POSITION, 16.0, metadata);
    }

    private static PetTransitions.CompleteTransfer complete(UUID transferId) {
        return new PetTransitions.CompleteTransfer(
                2, transferId, DESTINATION, OVERWORLD, DESTINATION_POSITION,
                DESTINATION_ENTITY, STARTED.plusSeconds(1));
    }

    private static PetPlacementService service(InMemoryPetRepository repository, Instant now) {
        return new PetPlacementService(
                repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(7));
    }

    private static InMemoryPetRepository placedRepository() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        Pet held = Pet.adopted(
                PET_ID, OWNER_ID, "Mochi",
                PetAppearance.create(PetSpecies.CAT, "minecraft:tabby", 0.67, 1L,
                        AppearanceRules.defaults()),
                PetTraits.initial(50, 50, 50, 50, 50, CREATED),
                PetMood.initial(50, 20, 10, 10, CREATED),
                CREATED);
        repository.createIfOwnerAbsent(held);
        Pet placed = PetTransitions.place(held, new PetTransitions.Place(
                OWNER_ID, 0, SOURCE, OVERWORLD, SOURCE_POSITION, SOURCE_ENTITY,
                CREATED.plusSeconds(10))).pet();
        repository.compareAndSet(PET_ID, 0, placed);
        return repository;
    }
}
