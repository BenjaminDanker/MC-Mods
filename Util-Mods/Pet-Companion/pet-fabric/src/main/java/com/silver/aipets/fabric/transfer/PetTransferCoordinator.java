package com.silver.aipets.fabric.transfer;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.TransferMetadata;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.fabric.authority.AuthorityMutationResult;
import com.silver.aipets.fabric.authority.AuthorityMutationStatus;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import com.silver.aipets.fabric.entity.PreparedPetEntity;
import com.silver.aipets.fabric.metrics.PetMetricsReporter;
import com.silver.aipets.fabric.metrics.PetMetricsClassifier;
import com.silver.aipets.fabric.placement.SafePlacementFinder;
import net.minecraft.entity.Entity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Server-thread-safe source and destination halves of one automatic cross-backend carry.
 * Authority is committed before either source discard or destination spawn.
 */
public final class PetTransferCoordinator {
    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final SafePlacementFinder safePlacementFinder;
    private final PetEntityFactory entityFactory;
    private final PetTransferConfig config;
    private final Clock clock;
    private final Supplier<UUID> transferIds;
    private final Supplier<UUID> operationIds;
    private final EntitySpawner entitySpawner;
    private final PetMetricsReporter metrics;

    public PetTransferCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            PetTransferConfig config,
            Clock clock,
            Supplier<UUID> transferIds,
            Supplier<UUID> operationIds) {
        this(backendId, authority, safePlacementFinder, entityFactory, config, clock,
                transferIds, operationIds, ServerWorld::spawnEntity, PetMetricsReporter.noop());
    }

    public PetTransferCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            PetTransferConfig config,
            Clock clock,
            Supplier<UUID> transferIds,
            Supplier<UUID> operationIds,
            PetMetricsReporter metrics) {
        this(backendId, authority, safePlacementFinder, entityFactory, config, clock,
                transferIds, operationIds, ServerWorld::spawnEntity, metrics);
    }

    PetTransferCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            PetTransferConfig config,
            Clock clock,
            Supplier<UUID> transferIds,
            Supplier<UUID> operationIds,
            EntitySpawner entitySpawner) {
        this(backendId, authority, safePlacementFinder, entityFactory, config, clock,
                transferIds, operationIds, entitySpawner, PetMetricsReporter.noop());
    }

    private PetTransferCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            PetTransferConfig config,
            Clock clock,
            Supplier<UUID> transferIds,
            Supplier<UUID> operationIds,
            EntitySpawner entitySpawner,
            PetMetricsReporter metrics) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.safePlacementFinder = Objects.requireNonNull(safePlacementFinder, "safePlacementFinder");
        this.entityFactory = Objects.requireNonNull(entityFactory, "entityFactory");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.transferIds = Objects.requireNonNull(transferIds, "transferIds");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
        this.entitySpawner = Objects.requireNonNull(entitySpawner, "entitySpawner");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Called by the portal pre-transfer hook; it always completes without blocking server I/O. */
    public CompletableFuture<PetTransferOutcome> prepareSource(
            ServerPlayerEntity initiatingPlayer, String destinationBackend) {
        Objects.requireNonNull(initiatingPlayer, "initiatingPlayer");
        BackendId destination = new BackendId(destinationBackend);
        MinecraftServer server = initiatingPlayer.getEntityWorld().getServer();
        UUID ownerUuid = initiatingPlayer.getUuid();
        CompletableFuture<PetTransferOutcome> outcome = new CompletableFuture<>();
        observe(outcome, true);
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null || snapshot == null) {
                    outcome.complete(PetTransferOutcome.of(
                            PetTransferStatus.SERVICE_FAILURE, null,
                            "Pet authority lookup failed; pet remains on source"));
                    return;
                }
                onServer(server, () -> prepareLoadedSource(
                        server, ownerUuid, destination, snapshot, outcome));
            });
        } catch (RuntimeException failure) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.SERVICE_FAILURE, null,
                    "Pet authority lookup failed; pet remains on source"));
        }
        return outcome;
    }

    /** Called after a player joins any backend; only the reserved final backend can materialize. */
    public CompletableFuture<PetTransferOutcome> claimDestination(
            ServerPlayerEntity joiningPlayer) {
        Objects.requireNonNull(joiningPlayer, "joiningPlayer");
        MinecraftServer server = joiningPlayer.getEntityWorld().getServer();
        UUID ownerUuid = joiningPlayer.getUuid();
        CompletableFuture<PetTransferOutcome> outcome = new CompletableFuture<>();
        observe(outcome, false);
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null || snapshot == null) {
                    outcome.complete(PetTransferOutcome.of(
                            PetTransferStatus.SERVICE_FAILURE, null,
                            "Pet authority lookup failed on destination"));
                    return;
                }
                onServer(server, () -> prepareDestination(
                        server, ownerUuid, snapshot, outcome));
            });
        } catch (RuntimeException failure) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.SERVICE_FAILURE, null,
                    "Pet authority lookup failed on destination"));
        }
        return outcome;
    }

    private void prepareLoadedSource(
            MinecraftServer server,
            UUID ownerUuid,
            BackendId destination,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetTransferOutcome> outcome) {
        if (snapshot.isEmpty()) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.NO_PET, null, "No adopted pet exists"));
            return;
        }
        Pet pet = snapshot.orElseThrow().pet();
        if (pet.placement() instanceof HeldPlacement) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.HELD_UNCHANGED, pet,
                    "Held pet remains held across backend transfer"));
            return;
        }
        if (pet.placement() instanceof TransferringPlacement transferring) {
            TransferMetadata transfer = transferring.transfer();
            if (transfer.sourceBackendId().equals(backendId)
                    && transfer.destinationBackendId().equals(destination)) {
                discardExactSource(server, pet, transfer.sourceEntityUuid());
                outcome.complete(PetTransferOutcome.of(
                        PetTransferStatus.SOURCE_ALREADY_PREPARED, pet,
                        "Existing transfer reservation reused"));
            } else {
                outcome.complete(PetTransferOutcome.of(
                        PetTransferStatus.AUTHORITY_REJECTED, pet,
                        "Pet is already reserved for another transfer"));
            }
            return;
        }
        if (!(pet.placement() instanceof PlacedPlacement placed)
                || !placed.backendId().equals(backendId)
                || placed.entityUuid().isEmpty()) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.LEFT_BEHIND, pet,
                    "Pet is not a materialized entity on this source backend"));
            return;
        }

        ServerPlayerEntity player = currentPlayer(server, ownerUuid);
        if (player == null) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.PLAYER_CONTEXT_CHANGED, pet,
                    "Owner left before source transfer preparation"));
            return;
        }
        ServerWorld world = player.getEntityWorld();
        DimensionId dimension = dimension(world);
        if (!placed.dimensionId().equals(dimension)) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.LEFT_BEHIND, pet,
                    "Pet is in another dimension and remains on source"));
            return;
        }
        UUID sourceEntityId = placed.entityUuid().orElseThrow();
        Entity entity = world.getEntity(sourceEntityId);
        if (!isExactEntity(entity, pet)) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.LEFT_BEHIND, pet,
                    "Authoritative source entity is not loaded"));
            return;
        }
        WorldPosition ownerPosition = position(player);
        WorldPosition entityPosition = position(entity);
        if (!ownerPosition.isWithin(entityPosition, config.carryRadius())) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.LEFT_BEHIND, pet,
                    "Pet is outside the automatic carry radius"));
            return;
        }

        Instant startedAt = clock.instant();
        UUID transferId = requireUuid(transferIds, "transfer ID");
        TransferMetadata transfer = new TransferMetadata(
                transferId,
                backendId,
                sourceEntityId,
                destination,
                startedAt,
                startedAt.plus(config.reservationLifetime()));
        PetTransitions.PrepareTransfer command = new PetTransitions.PrepareTransfer(
                ownerUuid,
                pet.recordVersion(),
                dimension,
                ownerPosition,
                entityPosition,
                config.carryRadius(),
                transfer);
        try {
            authority.prepareTransfer(transferId, pet.petId(), command)
                    .whenComplete((commit, failure) -> onServer(server, () -> {
                        if (failure != null || commit == null) {
                            outcome.complete(PetTransferOutcome.of(
                                    PetTransferStatus.SERVICE_FAILURE, pet,
                                    "Source reservation failed; pet remains authoritative on source"));
                            return;
                        }
                        if (commit.status() != AuthorityMutationStatus.APPLIED
                                || commit.pet().isEmpty()
                                || !matchesTransfer(commit.pet().orElseThrow(), transfer)) {
                            outcome.complete(PetTransferOutcome.of(
                                    PetTransferStatus.AUTHORITY_REJECTED,
                                    commit.pet().orElse(pet),
                                    "Source reservation was not applied"));
                            return;
                        }
                        discardExactSource(server, commit.pet().orElseThrow(), sourceEntityId);
                        outcome.complete(PetTransferOutcome.of(
                                PetTransferStatus.SOURCE_PREPARED,
                                commit.pet().orElseThrow(),
                                "Source authority reserved before entity discard"));
                    }));
        } catch (RuntimeException failure) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.SERVICE_FAILURE, pet,
                    "Source reservation failed; pet remains authoritative on source"));
        }
    }

    private void prepareDestination(
            MinecraftServer server,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetTransferOutcome> outcome) {
        if (snapshot.isEmpty()) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.NO_PET, null, "No adopted pet exists"));
            return;
        }
        PetAuthoritySnapshot authoritySnapshot = snapshot.orElseThrow();
        Pet pet = authoritySnapshot.pet();
        if (pet.placement() instanceof HeldPlacement) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.HELD_UNCHANGED, pet,
                    "Held pet is not automatically placed"));
            return;
        }
        if (!(pet.placement() instanceof TransferringPlacement transferring)
                || !transferring.transfer().destinationBackendId().equals(backendId)) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.DESTINATION_NOT_RESERVED, pet,
                    "This backend is not the reserved final destination"));
            return;
        }
        TransferMetadata transfer = transferring.transfer();
        Instant now = clock.instant();
        if (transfer.isExpiredAt(now)) {
            expireAtDestination(pet, transfer, outcome);
            return;
        }
        ServerPlayerEntity player = currentPlayer(server, ownerUuid);
        if (player == null) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.PLAYER_CONTEXT_CHANGED, pet,
                    "Owner left before destination claim"));
            return;
        }
        ServerWorld world = player.getEntityWorld();
        UUID newEntityId = destinationEntityId(transfer.transferId());
        PreparedPetEntity prepared = entityFactory.prepare(
                world, pet, newEntityId, position(player), authoritySnapshot.sleeping());
        Optional<WorldPosition> safe = safePlacementFinder.find(
                world, prepared.entity(), player.getBlockPos());
        if (safe.isEmpty()) {
            prepared.entity().discard();
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.DESTINATION_NO_SAFE_POSITION, pet,
                    "No safe loaded destination position; reservation will expire to held"));
            return;
        }
        WorldPosition target = safe.orElseThrow();
        prepared.entity().refreshPositionAndAngles(
                target.x(), target.y(), target.z(), player.getYaw(), 0.0F);
        PetTransitions.CompleteTransfer command = new PetTransitions.CompleteTransfer(
                pet.recordVersion(), transfer.transferId(), backendId, dimension(world), target,
                newEntityId, now);
        try {
            authority.completeTransfer(
                            requireUuid(operationIds, "complete operation ID"),
                            pet.petId(),
                            command)
                    .whenComplete((commit, failure) -> onServer(server, () ->
                            finishDestination(server, ownerUuid, pet, prepared.entity(),
                                    newEntityId, target, commit, failure, outcome)));
        } catch (RuntimeException failure) {
            prepared.entity().discard();
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.SERVICE_FAILURE, pet,
                    "Destination authority claim failed"));
        }
    }

    private void finishDestination(
            MinecraftServer server,
            UUID ownerUuid,
            Pet preCommitPet,
            TameableEntity entity,
            UUID entityId,
            WorldPosition target,
            AuthorityMutationResult commit,
            Throwable failure,
            CompletableFuture<PetTransferOutcome> outcome) {
        if (failure != null || commit == null) {
            entity.discard();
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.SERVICE_FAILURE, preCommitPet,
                    "Destination authority claim failed"));
            return;
        }
        if (commit.status() != AuthorityMutationStatus.APPLIED || commit.pet().isEmpty()) {
            entity.discard();
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.AUTHORITY_REJECTED, commit.pet().orElse(preCommitPet),
                    "Destination claim was not applied"));
            return;
        }
        Pet committed = commit.pet().orElseThrow();
        ServerPlayerEntity player = currentPlayer(server, ownerUuid);
        if (!matchesDestination(committed, entityId, target)
                || player == null
                || player.getEntityWorld() != entity.getEntityWorld()) {
            entity.discard();
            compensateDestination(committed, entityId, outcome,
                    "Destination context changed after authority commit");
            return;
        }
        ((PetEntityData) entity).aipets$setRecordVersion(committed.recordVersion());
        boolean spawned;
        try {
            spawned = entitySpawner.spawn((ServerWorld) entity.getEntityWorld(), entity);
        } catch (RuntimeException spawnFailure) {
            spawned = false;
        }
        if (spawned) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.DESTINATION_PLACED, committed,
                    "Reserved transfer claimed and exact pet spawned once"));
            return;
        }
        entity.discard();
        compensateDestination(committed, entityId, outcome,
                "Destination spawn failed after authority commit");
    }

    private void compensateDestination(
            Pet committed,
            UUID entityId,
            CompletableFuture<PetTransferOutcome> outcome,
            String reason) {
        try {
            authority.compensatePlaceFailure(
                            requireUuid(operationIds, "compensation operation ID"),
                            committed.petId(),
                            new PetTransitions.CompensatePlaceFailure(
                                    committed.recordVersion(), backendId, entityId, clock.instant()))
                    .whenComplete((compensation, failure) -> {
                        boolean applied = failure == null
                                && compensation != null
                                && compensation.status() == AuthorityMutationStatus.APPLIED;
                        outcome.complete(PetTransferOutcome.of(
                                applied
                                        ? PetTransferStatus.DESTINATION_SPAWN_FAILED_COMPENSATED
                                        : PetTransferStatus.DESTINATION_SPAWN_FAILED_UNRESOLVED,
                                applied ? compensation.pet().orElse(committed) : committed,
                                applied ? reason + "; returned to held" : reason + "; compensation failed"));
                    });
        } catch (RuntimeException failure) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.DESTINATION_SPAWN_FAILED_UNRESOLVED, committed,
                    reason + "; compensation failed"));
        }
    }

    private void expireAtDestination(
            Pet pet,
            TransferMetadata transfer,
            CompletableFuture<PetTransferOutcome> outcome) {
        try {
            authority.expireTransfer(
                            transfer.transferId(),
                            pet.petId(),
                            new PetTransitions.ExpireTransfer(
                                    pet.recordVersion(), transfer.transferId(), transfer.expiresAt()))
                    .whenComplete((expired, failure) -> {
                        if (failure == null && expired != null
                                && expired.status() == AuthorityMutationStatus.APPLIED) {
                            outcome.complete(PetTransferOutcome.of(
                                    PetTransferStatus.DESTINATION_EXPIRED,
                                    expired.pet().orElse(pet),
                                    "Expired reservation returned to held"));
                        } else {
                            outcome.complete(PetTransferOutcome.of(
                                    PetTransferStatus.SERVICE_FAILURE, pet,
                                    "Expired reservation recovery will be retried by the service"));
                        }
                    });
        } catch (RuntimeException failure) {
            outcome.complete(PetTransferOutcome.of(
                    PetTransferStatus.SERVICE_FAILURE, pet,
                    "Expired reservation recovery will be retried by the service"));
        }
    }

    private void discardExactSource(MinecraftServer server, Pet pet, UUID sourceEntityId) {
        for (ServerWorld world : server.getWorlds()) {
            Entity entity = world.getEntity(sourceEntityId);
            if (isExactEntity(entity, pet)) {
                entity.discard();
                return;
            }
        }
    }

    private static boolean isExactEntity(Entity entity, Pet pet) {
        if (!(entity instanceof PetEntityData data) || !data.aipets$isPet()) return false;
        return data.aipets$getPetId().equals(pet.petId())
                && data.aipets$getOwnerUuid().equals(pet.ownerUuid())
                && data.aipets$getRecordVersion() <= pet.recordVersion();
    }

    private static boolean matchesTransfer(Pet pet, TransferMetadata expected) {
        return pet.placement() instanceof TransferringPlacement transferring
                && transferring.transfer().equals(expected);
    }

    private boolean matchesDestination(Pet pet, UUID entityId, WorldPosition position) {
        return pet.placement() instanceof PlacedPlacement placed
                && placed.backendId().equals(backendId)
                && placed.entityUuid().filter(entityId::equals).isPresent()
                && placed.position().equals(position);
    }

    private static ServerPlayerEntity currentPlayer(MinecraftServer server, UUID ownerUuid) {
        ServerPlayerEntity player = server.getPlayerManager().getPlayer(ownerUuid);
        return player != null && player.isAlive() ? player : null;
    }

    private static DimensionId dimension(ServerWorld world) {
        return DimensionId.parse(world.getRegistryKey().getValue().toString());
    }

    private static WorldPosition position(Entity entity) {
        return new WorldPosition(entity.getX(), entity.getY(), entity.getZ());
    }

    private static UUID destinationEntityId(UUID transferId) {
        return UUID.nameUUIDFromBytes(
                ("aipets-transfer-entity:" + transferId).getBytes(StandardCharsets.UTF_8));
    }

    private static UUID requireUuid(Supplier<UUID> supplier, String name) {
        return Objects.requireNonNull(supplier.get(), name + " supplier returned null");
    }

    private static void onServer(MinecraftServer server, Runnable action) {
        if (server.isOnThread()) action.run(); else server.execute(action);
    }

    private void observe(CompletableFuture<PetTransferOutcome> outcome, boolean source) {
        outcome.whenComplete((resolved, failure) -> {
            if (failure != null || resolved == null) return;
            java.util.Optional<com.silver.aipets.common.transport.PetMetricWireEvent.Metric> metric =
                    PetMetricsClassifier.transferFailure(source, resolved.status());
            if (metric.isEmpty()) return;
            try {
                metrics.increment(metric.orElseThrow());
            } catch (RuntimeException ignored) {
                // Metrics are diagnostic only and cannot change transfer safety.
            }
        });
    }

    @FunctionalInterface
    interface EntitySpawner {
        boolean spawn(ServerWorld world, TameableEntity entity);
    }
}
