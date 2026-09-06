package com.silver.aipets.service.recall;

import com.silver.aipets.common.authority.PetTransitions;
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
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.service.persistence.InMemoryPetRepository;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetRecallServiceTest {
    @Test
    void utcMonthlyRecallRejectsSecondUseAndFailedSpawnReleasesEntitlement() {
        Instant adoptedAt = Instant.parse("2026-08-01T00:00:00Z");
        Instant recallAt = Instant.parse("2026-08-30T23:59:00Z");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000051");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000051");
        BackendId remote = new BackendId("remote");
        BackendId destination = new BackendId("survival");
        DimensionId dimension = DimensionId.parse("minecraft:overworld");
        UUID oldEntity = UUID.fromString("30000000-0000-0000-0000-000000000051");
        UUID recalledEntity = UUID.fromString("30000000-0000-0000-0000-000000000052");
        UUID replacementEntity = UUID.fromString("30000000-0000-0000-0000-000000000053");
        AppearanceRules rules = AppearanceRules.defaults();
        Pet adopted = Pet.adopted(
                petId, ownerId, "Mochi",
                PetAppearance.create(PetSpecies.CAT, "minecraft:tabby", 0.7, 51L, rules),
                PetTraits.initial(50, 50, 50, 50, 50, adoptedAt),
                PetMood.initial(50, 10, 5, 5, adoptedAt), adoptedAt);
        Pet placed = PetTransitions.place(adopted, new PetTransitions.Place(
                ownerId, 0, remote, dimension, new WorldPosition(1, 64, 1), oldEntity,
                adoptedAt.plusSeconds(1))).pet();
        InMemoryPetRepository repository = new InMemoryPetRepository();
        repository.createIfOwnerAbsent(placed);
        PetOperationalMetrics metrics = new PetOperationalMetrics();
        PetRecallService service = new PetRecallService(
                repository, Clock.fixed(recallAt, ZoneOffset.UTC), metrics);
        UUID firstOperation = UUID.fromString("40000000-0000-0000-0000-000000000051");

        PetRecallWireResult first = service.recall(firstOperation, petId, new PetTransitions.Recall(
                        ownerId, 1, destination, dimension, new WorldPosition(8, 65, 8),
                        recalledEntity, recallAt.minusSeconds(30)))
                .result().orElseThrow();
        assertEquals(PetRecallWireStatus.APPLIED, first.status());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), first.nextAvailableAt().orElseThrow());
        assertEquals(recalledEntity, ((PlacedPlacement) repository.findById(petId)
                .orElseThrow().placement()).entityUuid().orElseThrow());

        PetRecallWireResult unavailable = service.recall(UUID.randomUUID(), petId,
                        new PetTransitions.Recall(
                                ownerId, 2, destination, dimension, new WorldPosition(9, 65, 9),
                                UUID.randomUUID(), recallAt))
                .result().orElseThrow();
        assertEquals(PetRecallWireStatus.UNAVAILABLE, unavailable.status());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"),
                unavailable.nextAvailableAt().orElseThrow());

        PetRecallWireResult compensated = service.compensateFailure(
                        firstOperation, petId, new PetTransitions.CompensateRecallFailure(
                                2, destination, recalledEntity, recallAt))
                .result().orElseThrow();
        assertEquals(PetRecallWireStatus.COMPENSATED, compensated.status());
        assertInstanceOf(HeldPlacement.class, repository.findById(petId).orElseThrow().placement());
        assertEquals(PetRecallWireStatus.COMPENSATED,
                service.recall(firstOperation, petId, new PetTransitions.Recall(
                                ownerId, 1, destination, dimension, new WorldPosition(8, 65, 8),
                                recalledEntity, recallAt.minusSeconds(30)))
                        .result().orElseThrow().status());

        PetRecallWireResult afterRelease = service.recall(UUID.randomUUID(), petId,
                        new PetTransitions.Recall(
                                ownerId, 3, destination, dimension, new WorldPosition(10, 65, 10),
                                replacementEntity, recallAt))
                .result().orElseThrow();
        assertEquals(PetRecallWireStatus.APPLIED, afterRelease.status());
        assertEquals(replacementEntity, ((PlacedPlacement) repository.findById(petId)
                .orElseThrow().placement()).entityUuid().orElseThrow());
        String metricSnapshot = new String(metrics.prometheusSnapshot());
        assertTrue(metricSnapshot.contains("aipets_recall_attempts_total 4\n"));
        assertTrue(metricSnapshot.contains("aipets_recall_successes_total 4\n"));
        assertTrue(metricSnapshot.contains("aipets_recall_failures_total 0\n"));
    }
}
