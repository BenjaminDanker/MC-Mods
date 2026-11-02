package com.silver.aipets.fabric.authority;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.transport.PetWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireCodec;
import com.silver.aipets.common.transport.PetAdoptionWireRequest;
import com.silver.aipets.common.transport.PetAdoptionWireResult;
import com.silver.aipets.common.transport.PetAdoptionWireStatus;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.fabric.config.PetServiceClientConfig;
import com.silver.aipets.service.http.PetAuthorityReadHttpHandler;
import com.silver.aipets.service.http.PetAuthorityHttpHandler;
import com.silver.aipets.service.http.PetAdoptionHttpHandler;
import com.silver.aipets.service.adoption.AppearanceCatalog;
import com.silver.aipets.service.adoption.InitialMood;
import com.silver.aipets.service.adoption.PetAdoptionService;
import com.silver.aipets.service.adoption.PetRandomizer;
import com.silver.aipets.service.persistence.InMemoryPetRepository;
import com.silver.aipets.service.placement.PetPlacementService;
import com.silver.aipets.service.recall.PetRecallService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPetAuthorityGatewayTest {
    private static final String TOKEN = "test-token-0123456789-0123456789-ab";

    @Test
    void authenticatedTransferTransportPersistsAndClaimsExactReservation() throws Exception {
        AppearanceRules rules = AppearanceRules.defaults();
        Instant created = Instant.parse("2026-08-30T12:00:00Z");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000071");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000071");
        UUID sourceEntity = UUID.fromString("30000000-0000-0000-0000-000000000071");
        UUID destinationEntity = UUID.fromString("30000000-0000-0000-0000-000000000072");
        UUID transferId = UUID.fromString("40000000-0000-0000-0000-000000000071");
        BackendId source = new BackendId("sky-island");
        BackendId destination = new BackendId("ocean");
        DimensionId dimension = DimensionId.parse("minecraft:overworld");
        WorldPosition sourcePosition = new WorldPosition(10, 64, 10);
        Pet held = Pet.adopted(
                petId, ownerId, "Mochi",
                PetAppearance.create(PetSpecies.CAT, "minecraft:tabby", 0.67, 71L, rules),
                PetTraits.initial(50, 50, 50, 50, 50, created),
                PetMood.initial(50, 10, 5, 5, created), created);
        Pet placed = PetTransitions.place(held, new PetTransitions.Place(
                ownerId, 0, source, dimension, sourcePosition, sourceEntity,
                created.plusSeconds(1))).pet();
        InMemoryPetRepository repository = new InMemoryPetRepository();
        repository.createIfOwnerAbsent(placed);
        PetWireCodec codec = new PetWireCodec(rules);
        PetPlacementService placements = new PetPlacementService(
                repository, Clock.fixed(created.plusSeconds(2), ZoneOffset.UTC), Duration.ofDays(7));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/pets", new PetAuthorityHttpHandler(
                repository, ignored -> false, codec, placements, TOKEN));
        server.start();
        try {
            HttpPetAuthorityGateway gateway = gateway(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    TOKEN, codec);
            TransferMetadata transfer = new TransferMetadata(
                    transferId, source, sourceEntity, destination,
                    created.plusSeconds(2), created.plusSeconds(32));
            PetTransitions.PrepareTransfer prepare = new PetTransitions.PrepareTransfer(
                    ownerId, 1, dimension, new WorldPosition(11, 64, 10), sourcePosition,
                    16.0, transfer);
            AuthorityMutationResult prepared = gateway.prepareTransfer(
                    transferId, petId, prepare).toCompletableFuture().join();
            assertEquals(AuthorityMutationStatus.APPLIED, prepared.status());
            assertEquals(prepared, gateway.prepareTransfer(transferId, petId, prepare)
                    .toCompletableFuture().join());

            PetTransitions.CompleteTransfer complete = new PetTransitions.CompleteTransfer(
                    2, transferId, destination, dimension, new WorldPosition(0, 70, 0),
                    destinationEntity, created.plusSeconds(3));
            AuthorityMutationResult completed = gateway.completeTransfer(
                    UUID.randomUUID(), petId, complete).toCompletableFuture().join();
            assertEquals(AuthorityMutationStatus.APPLIED, completed.status());
            assertEquals(destinationEntity,
                    ((com.silver.aipets.common.domain.PlacedPlacement)
                            repository.findById(petId).orElseThrow().placement())
                            .entityUuid().orElseThrow());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void recallTransportCommitsReplaysRejectsSecondUseAndCompensates() throws Exception {
        AppearanceRules rules = AppearanceRules.defaults();
        Instant now = Instant.parse("2026-08-30T12:00:00Z");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000061");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000061");
        UUID entityId = UUID.fromString("30000000-0000-0000-0000-000000000061");
        UUID operationId = UUID.fromString("40000000-0000-0000-0000-000000000061");
        BackendId backend = new BackendId("survival");
        DimensionId dimension = DimensionId.parse("minecraft:overworld");
        Pet held = Pet.adopted(
                petId, ownerId, "Mochi",
                PetAppearance.create(PetSpecies.CAT, "minecraft:tabby", 0.67, 61L, rules),
                PetTraits.initial(50, 50, 50, 50, 50, now),
                PetMood.initial(50, 10, 5, 5, now), now);
        InMemoryPetRepository repository = new InMemoryPetRepository();
        repository.createIfOwnerAbsent(held);
        PetWireCodec codec = new PetWireCodec(rules);
        PetPlacementService placements = new PetPlacementService(
                repository, Clock.fixed(now, ZoneOffset.UTC), Duration.ofDays(7));
        PetRecallService recalls = new PetRecallService(
                repository, Clock.fixed(now.plusSeconds(1), ZoneOffset.UTC));
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/pets", new PetAuthorityHttpHandler(
                repository, ignored -> false, ignored -> false,
                codec, placements, recalls, TOKEN));
        server.start();
        try {
            HttpPetAuthorityGateway gateway = gateway(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/"),
                    TOKEN, codec);
            PetTransitions.Recall command = new PetTransitions.Recall(
                    ownerId, 0, backend, dimension, new WorldPosition(4, 65, 4),
                    entityId, now);
            PetRecallWireResult applied = gateway.recall(operationId, petId, command)
                    .toCompletableFuture().join();
            assertEquals(PetRecallWireStatus.APPLIED, applied.status());
            assertEquals(applied, gateway.recall(operationId, petId, command)
                    .toCompletableFuture().join());
            assertEquals(PetRecallWireStatus.UNAVAILABLE,
                    gateway.recall(UUID.randomUUID(), petId, new PetTransitions.Recall(
                                    ownerId, 1, backend, dimension, new WorldPosition(5, 65, 5),
                                    UUID.randomUUID(), now))
                            .toCompletableFuture().join().status());
            PetTransitions.CompensateRecallFailure compensation =
                    new PetTransitions.CompensateRecallFailure(1, backend, entityId, now);
            PetRecallWireResult compensated = gateway.compensateRecallFailure(
                            operationId, petId, compensation)
                    .toCompletableFuture().join();
            assertEquals(PetRecallWireStatus.COMPENSATED, compensated.status());
            assertEquals(compensated, gateway.compensateRecallFailure(
                            operationId, petId, compensation)
                    .toCompletableFuture().join());
            CompletionException compensationKeyReuse = assertThrows(
                    CompletionException.class,
                    () -> gateway.compensateRecallFailure(
                                    operationId, petId,
                                    new PetTransitions.CompensateRecallFailure(
                                            2, backend, entityId, now))
                            .toCompletableFuture().join());
            assertInstanceOf(PetAuthorityTransportException.class,
                    compensationKeyReuse.getCause());
            assertInstanceOf(com.silver.aipets.common.domain.HeldPlacement.class,
                    repository.findById(petId).orElseThrow().placement());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void adoptionTransportEnforcesAccessAndReturnsExistingWithoutReroll() throws Exception {
        AppearanceRules rules = AppearanceRules.defaults();
        Instant createdAt = Instant.parse("2026-08-30T12:00:00Z");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000041");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000041");
        AtomicBoolean active = new AtomicBoolean(false);
        InMemoryPetRepository repository = new InMemoryPetRepository();
        PetWireCodec petCodec = new PetWireCodec(rules);
        PetAdoptionService adoptions = new PetAdoptionService(
                repository,
                ignored -> active.get(),
                new PetRandomizer(
                        rules,
                        AppearanceCatalog.vanilla12110(),
                        InitialMood.defaults(),
                        new Random(41L)),
                Clock.fixed(createdAt, ZoneOffset.UTC),
                () -> petId,
                Set.of(PetSpecies.CAT));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/adoptions",
                new PetAdoptionHttpHandler(
                        adoptions,
                        new PetAdoptionWireCodec(petCodec),
                        TOKEN));
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpPetAuthorityGateway gateway = gateway(baseUri, TOKEN, petCodec);
            PetAdoptionWireResult denied = gateway.adopt(new PetAdoptionWireRequest(
                            ownerId, PetSpecies.CAT, "Luna"))
                    .toCompletableFuture().join();
            assertEquals(PetAdoptionWireStatus.SUBSCRIPTION_REQUIRED, denied.status());
            assertTrue(repository.findByOwner(ownerId).isEmpty());

            active.set(true);
            PetAdoptionWireResult disabled = gateway.adopt(new PetAdoptionWireRequest(
                            ownerId, PetSpecies.DOG, "Pepper"))
                    .toCompletableFuture().join();
            assertEquals(PetAdoptionWireStatus.SPECIES_UNAVAILABLE, disabled.status());
            assertTrue(repository.findByOwner(ownerId).isEmpty());

            PetAdoptionWireResult created = gateway.adopt(new PetAdoptionWireRequest(
                            ownerId, PetSpecies.CAT, "Luna"))
                    .toCompletableFuture().join();
            PetAdoptionWireResult retry = gateway.adopt(new PetAdoptionWireRequest(
                            ownerId, PetSpecies.DOG, "Reroll"))
                    .toCompletableFuture().join();
            assertEquals(PetAdoptionWireStatus.CREATED, created.status());
            assertEquals(PetAdoptionWireStatus.EXISTING, retry.status());
            assertEquals(created.pet(), retry.pet());
            assertEquals(petId, created.pet().orElseThrow().petId());
            assertEquals(PetSpecies.CAT, created.pet().orElseThrow().appearance().species());
            assertTrue(rules.scaleRange(PetSpecies.CAT).contains(
                    created.pet().orElseThrow().appearance().scale()));

            CompletionException unauthorized = assertThrows(
                    CompletionException.class,
                    () -> gateway(baseUri, TOKEN + "wrong", petCodec)
                            .adopt(new PetAdoptionWireRequest(ownerId, PetSpecies.CAT, "Luna"))
                            .toCompletableFuture().join());
            assertInstanceOf(PetAuthorityTransportException.class, unauthorized.getCause());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authenticatedMutationsReplayExactlyAndRejectKeyReuse() throws Exception {
        AppearanceRules rules = AppearanceRules.defaults();
        Instant createdAt = Instant.parse("2026-08-30T12:00:00Z");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000031");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000031");
        UUID firstEntity = UUID.fromString("30000000-0000-0000-0000-000000000031");
        UUID secondEntity = UUID.fromString("30000000-0000-0000-0000-000000000032");
        BackendId backend = new BackendId("ocean");
        DimensionId dimension = DimensionId.parse("minecraft:overworld");
        WorldPosition position = new WorldPosition(12.5, 64.0, -3.5);
        Pet held = Pet.adopted(
                petId,
                ownerId,
                "Mochi",
                PetAppearance.create(
                        PetSpecies.CAT, "minecraft:tabby", 0.67, 55L, rules),
                PetTraits.initial(51, 52, 53, 54, 55, createdAt),
                PetMood.initial(60, 20, 10, 5, createdAt),
                createdAt);
        InMemoryPetRepository repository = new InMemoryPetRepository();
        repository.createIfOwnerAbsent(held);
        PetWireCodec codec = new PetWireCodec(rules);
        PetPlacementService placements = new PetPlacementService(
                repository, Clock.fixed(createdAt, ZoneOffset.UTC), Duration.ofDays(7));

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/pets",
                new PetAuthorityHttpHandler(repository, ignored -> false, codec, placements, TOKEN));
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpPetAuthorityGateway gateway = gateway(baseUri, TOKEN, codec);
            UUID placeOperation = UUID.fromString("40000000-0000-0000-0000-000000000031");
            PetTransitions.Place place = new PetTransitions.Place(
                    ownerId, 0, backend, dimension, position, firstEntity,
                    createdAt.plusSeconds(1));

            AuthorityMutationResult placed = gateway.place(placeOperation, petId, place)
                    .toCompletableFuture().join();
            AuthorityMutationResult placeReplay = gateway.place(placeOperation, petId, place)
                    .toCompletableFuture().join();
            assertEquals(AuthorityMutationStatus.APPLIED, placed.status());
            assertEquals(placed, placeReplay);
            assertEquals(1, repository.findById(petId).orElseThrow().recordVersion());

            PetTransitions.Place conflictingPlace = new PetTransitions.Place(
                    ownerId, 0, backend, dimension, position, secondEntity,
                    createdAt.plusSeconds(1));
            CompletionException keyReuse = assertThrows(
                    CompletionException.class,
                    () -> gateway.place(placeOperation, petId, conflictingPlace)
                            .toCompletableFuture().join());
            assertInstanceOf(PetAuthorityTransportException.class, keyReuse.getCause());
            assertEquals(firstEntity, ((com.silver.aipets.common.domain.PlacedPlacement)
                    repository.findById(petId).orElseThrow().placement())
                    .entityUuid().orElseThrow());

            UUID compensationOperation = UUID.fromString("40000000-0000-0000-0000-000000000032");
            PetTransitions.CompensatePlaceFailure compensation =
                    new PetTransitions.CompensatePlaceFailure(
                            1, backend, firstEntity, createdAt.plusSeconds(2));
            AuthorityMutationResult compensated = gateway.compensatePlaceFailure(
                    compensationOperation, petId, compensation).toCompletableFuture().join();
            assertEquals(compensated, gateway.compensatePlaceFailure(
                    compensationOperation, petId, compensation).toCompletableFuture().join());
            assertEquals(2, repository.findById(petId).orElseThrow().recordVersion());

            UUID secondPlaceOperation = UUID.fromString("40000000-0000-0000-0000-000000000033");
            PetTransitions.Place secondPlace = new PetTransitions.Place(
                    ownerId, 2, backend, dimension, position, secondEntity,
                    createdAt.plusSeconds(3));
            assertEquals(
                    AuthorityMutationStatus.APPLIED,
                    gateway.place(secondPlaceOperation, petId, secondPlace)
                            .toCompletableFuture().join().status());

            UUID pickupOperation = UUID.fromString("40000000-0000-0000-0000-000000000034");
            PetTransitions.Pickup pickup = new PetTransitions.Pickup(
                    ownerId, 3, backend, dimension, secondEntity,
                    position, position, 8.0, createdAt.plusSeconds(4));
            AuthorityMutationResult pickedUp = gateway.pickup(pickupOperation, petId, pickup)
                    .toCompletableFuture().join();
            assertEquals(pickedUp, gateway.pickup(pickupOperation, petId, pickup)
                    .toCompletableFuture().join());
            assertEquals(AuthorityMutationStatus.APPLIED, pickedUp.status());
            assertEquals(4, repository.findById(petId).orElseThrow().recordVersion());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void authenticatedReadTransportRoundTripsAuthorityAndRejectsBadToken() throws Exception {
        AppearanceRules rules = AppearanceRules.defaults();
        Instant createdAt = Instant.parse("2026-08-30T12:00:00Z");
        UUID petId = UUID.fromString("10000000-0000-0000-0000-000000000021");
        UUID ownerId = UUID.fromString("20000000-0000-0000-0000-000000000021");
        UUID entityId = UUID.fromString("30000000-0000-0000-0000-000000000021");
        BackendId backend = new BackendId("ocean");
        DimensionId dimension = DimensionId.parse("minecraft:overworld");
        Pet held = Pet.adopted(
                petId,
                ownerId,
                "Mochi",
                PetAppearance.create(
                        PetSpecies.CAT,
                        "minecraft:tabby",
                        0.67,
                        55L,
                        rules),
                PetTraits.initial(51, 52, 53, 54, 55, createdAt),
                PetMood.initial(60, 20, 10, 5, createdAt),
                createdAt);
        Pet placed = PetTransitions.place(
                held,
                new PetTransitions.Place(
                        ownerId,
                        0,
                        backend,
                        dimension,
                        new WorldPosition(12.5, 64.0, -3.5),
                        entityId,
                        createdAt.plusSeconds(1)))
                .pet();
        InMemoryPetRepository repository = new InMemoryPetRepository();
        repository.createIfOwnerAbsent(placed);
        PetWireCodec codec = new PetWireCodec(rules);

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/v1/pets",
                new PetAuthorityReadHttpHandler(
                        repository,
                        ignored -> true,
                        ignored -> false,
                        codec,
                        TOKEN));
        server.start();
        try {
            URI baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            HttpPetAuthorityGateway gateway = gateway(baseUri, TOKEN, codec);

            PetAuthoritySnapshot byOwner = gateway.findByOwner(ownerId)
                    .toCompletableFuture()
                    .join()
                    .orElseThrow();
            assertEquals(placed, byOwner.pet());
            assertTrue(byOwner.sleeping());
            assertEquals(false, byOwner.aiAccessEnabled());
            assertEquals(
                    placed,
                    gateway.findByPetId(petId).toCompletableFuture().join().orElseThrow().pet());
            assertTrue(gateway.findByPetId(UUID.randomUUID()).toCompletableFuture().join().isEmpty());

            CompletionException unauthorized = assertThrows(
                    CompletionException.class,
                    () -> gateway(baseUri, TOKEN + "wrong", codec)
                            .findByOwner(ownerId)
                            .toCompletableFuture()
                            .join());
            assertInstanceOf(PetAuthorityTransportException.class, unauthorized.getCause());
        } finally {
            server.stop(0);
        }
    }

    private static HttpPetAuthorityGateway gateway(
            URI baseUri,
            String token,
            PetWireCodec codec) {
        return new HttpPetAuthorityGateway(
                new PetServiceClientConfig(
                        new BackendId("ocean"),
                        baseUri,
                        token,
                        Duration.ofSeconds(1),
                        Duration.ofSeconds(2)),
                codec);
    }
}
