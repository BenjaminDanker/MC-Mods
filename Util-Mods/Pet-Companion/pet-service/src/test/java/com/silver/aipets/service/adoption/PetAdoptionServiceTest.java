package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.service.persistence.InMemoryPetRepository;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetAdoptionServiceTest {
    private static final UUID OWNER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void inactiveOwnerWithoutPetCannotAdopt() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        PetAdoptionService service = service(repository, false);

        AdoptionResult result = service.adopt(OWNER_ID, PetSpecies.CAT, "Mochi");

        assertEquals(AdoptionStatus.SUBSCRIPTION_REQUIRED, result.status());
        assertTrue(result.pet().isEmpty());
        assertTrue(repository.findByOwner(OWNER_ID).isEmpty());
    }

    @Test
    void existingPetIsReturnedAfterSubscriptionEndsWithoutRerolling() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        AdoptionResult created = service(repository, true)
                .adopt(OWNER_ID, PetSpecies.CAT, "Mochi");

        AdoptionResult retryAfterCancellation = service(repository, false)
                .adopt(OWNER_ID, PetSpecies.DOG, "Different");

        assertEquals(AdoptionStatus.EXISTING, retryAfterCancellation.status());
        assertEquals(created.pet(), retryAfterCancellation.pet());
        assertEquals(PetSpecies.CAT, retryAfterCancellation.pet().orElseThrow().appearance().species());
    }

    @Test
    void concurrentAdoptionCreatesExactlyOnePersistedPet() throws Exception {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        PetAdoptionService service = service(repository, true);
        int requestCount = 24;
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Callable<AdoptionResult>> tasks = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    return service.adopt(OWNER_ID, PetSpecies.CAT, "Mochi");
                });
            }
            List<Future<AdoptionResult>> futures = tasks.stream()
                    .map(executor::submit)
                    .toList();
            ready.await();
            start.countDown();

            List<AdoptionResult> results = new ArrayList<>();
            for (Future<AdoptionResult> future : futures) {
                results.add(future.get());
            }

            assertEquals(1, results.stream()
                    .filter(result -> result.status() == AdoptionStatus.CREATED)
                    .count());
            assertEquals(requestCount - 1L, results.stream()
                    .filter(result -> result.status() == AdoptionStatus.EXISTING)
                    .count());
            UUID persistedId = repository.findByOwner(OWNER_ID).orElseThrow().petId();
            assertTrue(results.stream()
                    .map(result -> result.pet().orElseThrow().petId())
                    .allMatch(persistedId::equals));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void invalidNameNeverCreatesARecord() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        PetAdoptionService service = service(repository, true);

        boolean threw = false;
        try {
            service.adopt(OWNER_ID, PetSpecies.CAT, " bad ");
        } catch (IllegalArgumentException expected) {
            threw = true;
        }

        assertTrue(threw);
        assertFalse(repository.findByOwner(OWNER_ID).isPresent());
    }

    @Test
    void configuredSpeciesAllowlistRejectsDisabledSpeciesBeforeCreation() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        PetAdoptionService service = new PetAdoptionService(
                repository,
                ignored -> true,
                new PetRandomizer(
                        AppearanceRules.defaults(), AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(), new Random(12345L)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                UUID::randomUUID,
                Set.of(PetSpecies.CAT));

        AdoptionResult denied = service.adopt(OWNER_ID, PetSpecies.DOG, "Pepper");
        assertEquals(AdoptionStatus.SPECIES_UNAVAILABLE, denied.status());
        assertTrue(repository.findByOwner(OWNER_ID).isEmpty());

        AdoptionResult allowed = service.adopt(OWNER_ID, PetSpecies.CAT, "Mochi");
        assertEquals(AdoptionStatus.CREATED, allowed.status());
        assertEquals(PetSpecies.CAT, allowed.pet().orElseThrow().appearance().species());
    }

    @Test
    void creationMetricsCountOnlyTheWinningTransactionalCreation() {
        InMemoryPetRepository repository = new InMemoryPetRepository();
        PetOperationalMetrics metrics = new PetOperationalMetrics();
        PetAdoptionService service = new PetAdoptionService(
                repository,
                ignored -> true,
                new PetRandomizer(
                        AppearanceRules.defaults(), AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(), new Random(12345L)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                UUID::randomUUID,
                Set.of(PetSpecies.CAT, PetSpecies.DOG),
                metrics);

        assertEquals(AdoptionStatus.CREATED,
                service.adopt(OWNER_ID, PetSpecies.CAT, "Mochi").status());
        assertEquals(AdoptionStatus.EXISTING,
                service.adopt(OWNER_ID, PetSpecies.DOG, "Pepper").status());

        String snapshot = new String(metrics.prometheusSnapshot());
        assertTrue(snapshot.contains("aipets_pets_created_cat_total 1\n"));
        assertTrue(snapshot.contains("aipets_pets_created_dog_total 0\n"));
    }

    private static PetAdoptionService service(
            InMemoryPetRepository repository, boolean subscriptionActive) {
        return new PetAdoptionService(
                repository,
                ownerUuid -> subscriptionActive,
                new PetRandomizer(
                        AppearanceRules.defaults(),
                        AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(),
                        new Random(12345L)),
                Clock.fixed(NOW, ZoneOffset.UTC),
                UUID::randomUUID);
    }
}
