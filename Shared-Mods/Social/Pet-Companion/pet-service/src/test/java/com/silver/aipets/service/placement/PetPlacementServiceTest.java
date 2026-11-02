package com.silver.aipets.service.placement;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.authority.TransitionFailure;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.service.persistence.InMemoryPetRepository;
import com.silver.aipets.service.persistence.IdempotentMutationDisposition;
import com.silver.aipets.service.persistence.IdempotentMutationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetPlacementServiceTest {
    private static final UUID PET_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final BackendId BACKEND = new BackendId("sky-island");
    private static final DimensionId DIMENSION = DimensionId.parse("minecraft:overworld");
    private static final WorldPosition POSITION = new WorldPosition(10, 64, 10);
    private static final Instant CREATED = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void idempotentRetryReplaysExactResultAndRejectsKeyReuse() {
        InMemoryPetRepository repository = repositoryWithHeldPet();
        PetPlacementService service = new PetPlacementService(
                repository, Clock.fixed(CREATED, ZoneOffset.UTC), Duration.ofDays(7));
        UUID operationId = UUID.fromString("30000000-0000-0000-0000-000000000003");
        UUID firstEntity = UUID.fromString("40000000-0000-0000-0000-000000000004");

        IdempotentMutationResult first = service.place(
                operationId, PET_ID, placeCommand(firstEntity));
        IdempotentMutationResult replay = service.place(
                operationId, PET_ID, placeCommand(firstEntity));
        IdempotentMutationResult conflict = service.place(
                operationId, PET_ID, placeCommand(UUID.randomUUID()));

        assertEquals(IdempotentMutationDisposition.EXECUTED, first.disposition());
        assertEquals(PetMutationStatus.APPLIED, first.mutation().orElseThrow().status());
        assertEquals(IdempotentMutationDisposition.REPLAYED, replay.disposition());
        assertEquals(first.mutation(), replay.mutation());
        assertEquals(IdempotentMutationDisposition.KEY_CONFLICT, conflict.disposition());
        assertTrue(conflict.mutation().isEmpty());
        Pet persisted = repository.findById(PET_ID).orElseThrow();
        assertEquals(1, persisted.recordVersion());
        assertEquals(firstEntity, ((PlacedPlacement) persisted.placement()).entityUuid().orElseThrow());
    }

    @Test
    void concurrentPlaceRequestsCommitOneRepresentation() throws Exception {
        InMemoryPetRepository repository = repositoryWithHeldPet();
        PetPlacementService service = new PetPlacementService(repository);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<PetMutationResult> first = placeTask(service, ready, start, UUID.randomUUID());
            Callable<PetMutationResult> second = placeTask(service, ready, start, UUID.randomUUID());
            Future<PetMutationResult> firstResult = executor.submit(first);
            Future<PetMutationResult> secondResult = executor.submit(second);
            ready.await();
            start.countDown();

            List<PetMutationResult> results = List.of(firstResult.get(), secondResult.get());
            assertEquals(1, results.stream()
                    .filter(result -> result.status() == PetMutationStatus.APPLIED)
                    .count());
            assertTrue(results.stream().anyMatch(result ->
                    result.status() == PetMutationStatus.CONCURRENT_MODIFICATION
                            || (result.status() == PetMutationStatus.REJECTED
                            && result.failure().orElseThrow() == TransitionFailure.VERSION_MISMATCH)));

            Pet persisted = repository.findById(PET_ID).orElseThrow();
            assertEquals(1, persisted.recordVersion());
            assertTrue(persisted.placement() instanceof PlacedPlacement);
            assertTrue(((PlacedPlacement) persisted.placement()).entityUuid().isPresent());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void staleExpectedVersionReturnsExplicitRejectionWithoutWriting() {
        InMemoryPetRepository repository = repositoryWithHeldPet();
        PetPlacementService service = new PetPlacementService(repository);

        PetMutationResult result = service.place(PET_ID, new PetTransitions.Place(
                OWNER_ID,
                7,
                BACKEND,
                DIMENSION,
                POSITION,
                UUID.randomUUID(),
                CREATED.plusSeconds(1)));

        assertEquals(PetMutationStatus.REJECTED, result.status());
        assertEquals(TransitionFailure.VERSION_MISMATCH, result.failure().orElseThrow());
        assertEquals(0, repository.findById(PET_ID).orElseThrow().recordVersion());
    }

    @Test
    void postCommitSpawnFailureCanOnlyCompensateMatchingEntity() {
        InMemoryPetRepository repository = repositoryWithHeldPet();
        PetPlacementService service = new PetPlacementService(repository);
        UUID entityId = UUID.randomUUID();
        assertEquals(
                PetMutationStatus.APPLIED,
                service.place(PET_ID, placeCommand(entityId)).status());

        PetMutationResult wrong = service.compensatePlaceFailure(
                PET_ID,
                new PetTransitions.CompensatePlaceFailure(
                        1, BACKEND, UUID.randomUUID(), CREATED.plusSeconds(2)));
        assertEquals(PetMutationStatus.REJECTED, wrong.status());
        assertEquals(TransitionFailure.ENTITY_UUID_MISMATCH, wrong.failure().orElseThrow());

        PetMutationResult exact = service.compensatePlaceFailure(
                PET_ID,
                new PetTransitions.CompensatePlaceFailure(
                        1, BACKEND, entityId, CREATED.plusSeconds(2)));
        assertEquals(PetMutationStatus.APPLIED, exact.status());
        assertEquals(2, exact.pet().orElseThrow().recordVersion());
    }

    private static Callable<PetMutationResult> placeTask(
            PetPlacementService service,
            CountDownLatch ready,
            CountDownLatch start,
            UUID entityId) {
        return () -> {
            ready.countDown();
            start.await();
            return service.place(PET_ID, placeCommand(entityId));
        };
    }

    private static PetTransitions.Place placeCommand(UUID entityId) {
        return new PetTransitions.Place(
                OWNER_ID,
                0,
                BACKEND,
                DIMENSION,
                POSITION,
                entityId,
                CREATED.plusSeconds(1));
    }

    private static InMemoryPetRepository repositoryWithHeldPet() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        Pet pet = Pet.adopted(
                PET_ID,
                OWNER_ID,
                "Mochi",
                PetAppearance.create(
                        PetSpecies.CAT, "minecraft:tabby", 0.67, 1L, AppearanceRules.defaults()),
                PetTraits.initial(50, 50, 50, 50, 50, CREATED),
                PetMood.initial(50, 20, 10, 10, CREATED),
                CREATED);
        repository.createIfOwnerAbsent(pet);
        return repository;
    }
}
