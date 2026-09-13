package com.silver.aipets.fabric.recall;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.transport.PetRecallWireResult;
import com.silver.aipets.common.transport.PetRecallWireStatus;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import com.silver.aipets.fabric.entity.PreparedPetEntity;
import com.silver.aipets.fabric.placement.SafePlacementFinder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Commit-first monthly recall with loaded-only stale cleanup and entitlement compensation. */
public final class PetRecallCoordinator {
    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final SafePlacementFinder safePlacementFinder;
    private final PetEntityFactory entityFactory;
    private final Clock clock;
    private final Supplier<UUID> operationIds;
    private final Supplier<UUID> entityIds;
    private final EntitySpawner entitySpawner;

    public PetRecallCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            Clock clock,
            Supplier<UUID> operationIds,
            Supplier<UUID> entityIds) {
        this(backendId, authority, safePlacementFinder, entityFactory, clock,
                operationIds, entityIds, (world, entity) -> world.addFreshEntity(entity));
    }

    public PetRecallCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            Clock clock,
            Supplier<UUID> operationIds,
            Supplier<UUID> entityIds,
            EntitySpawner entitySpawner) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.safePlacementFinder = Objects.requireNonNull(safePlacementFinder, "safePlacementFinder");
        this.entityFactory = Objects.requireNonNull(entityFactory, "entityFactory");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
        this.entityIds = Objects.requireNonNull(entityIds, "entityIds");
        this.entitySpawner = Objects.requireNonNull(entitySpawner, "entitySpawner");
    }

    public CompletableFuture<PetRecallOutcome> recall(ServerPlayer initiatingPlayer) {
        Objects.requireNonNull(initiatingPlayer, "initiatingPlayer");
        MinecraftServer server = initiatingPlayer.level().getServer();
        UUID ownerUuid = initiatingPlayer.getUUID();
        CompletableFuture<PetRecallOutcome> outcome = new CompletableFuture<>();
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null) {
                    outcome.complete(PetRecallOutcome.of(
                            PetRecallStatus.SERVICE_FAILURE, null, null, "Authority lookup failed"));
                    return;
                }
                onServer(server, () -> prepare(server, ownerUuid, snapshot, outcome));
            });
        } catch (RuntimeException failure) {
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.SERVICE_FAILURE, null, null, "Authority lookup failed"));
        }
        return outcome;
    }

    private void prepare(
            MinecraftServer server,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetRecallOutcome> outcome) {
        if (snapshot == null || snapshot.isEmpty()) {
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.NO_PET, null, null, "No adopted pet"));
            return;
        }
        PetAuthoritySnapshot authoritySnapshot = snapshot.orElseThrow();
        Pet pet = authoritySnapshot.pet();
        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player == null || !player.isAlive()) {
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.PLAYER_CONTEXT_CHANGED, pet, null, "Owner left before recall"));
            return;
        }
        ServerLevel world = player.level();
        DimensionId dimension = DimensionId.parse(world.dimension().identifier().toString());
        UUID entityUuid = Objects.requireNonNull(entityIds.get(), "entity ID supplier returned null");
        WorldPosition playerPosition = new WorldPosition(player.getX(), player.getY(), player.getZ());
        PreparedPetEntity prepared = entityFactory.prepare(
                world, pet, entityUuid, playerPosition, authoritySnapshot.sleeping());
        Optional<WorldPosition> safe = safePlacementFinder.find(
                world, prepared.entity(), player.blockPosition());
        if (safe.isEmpty()) {
            prepared.entity().discard();
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.NO_SAFE_POSITION, pet, null, "No safe loaded position"));
            return;
        }
        WorldPosition target = safe.orElseThrow();
        prepared.entity().snapTo(
                target.x(), target.y(), target.z(), player.getYRot(), 0.0F);
        UUID operationId = Objects.requireNonNull(operationIds.get(), "operation ID supplier returned null");
        PetTransitions.Recall command = new PetTransitions.Recall(
                ownerUuid, pet.recordVersion(), backendId, dimension, target,
                entityUuid, clock.instant());
        try {
            authority.recall(operationId, pet.petId(), command).whenComplete((result, failure) -> {
                if (failure != null) {
                    onServer(server, () -> {
                        prepared.entity().discard();
                        outcome.complete(PetRecallOutcome.of(
                                PetRecallStatus.SERVICE_FAILURE, pet, null, "Recall commit failed"));
                    });
                    return;
                }
                onServer(server, () -> completeCommit(
                        server, ownerUuid, world, pet, prepared.entity(), target,
                        entityUuid, operationId, result, outcome));
            });
        } catch (RuntimeException failure) {
            prepared.entity().discard();
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.SERVICE_FAILURE, pet, null, "Recall commit failed"));
        }
    }

    private void completeCommit(
            MinecraftServer server,
            UUID ownerUuid,
            ServerLevel world,
            Pet before,
            TamableAnimal entity,
            WorldPosition target,
            UUID entityUuid,
            UUID operationId,
            PetRecallWireResult result,
            CompletableFuture<PetRecallOutcome> outcome) {
        if (result == null || result.status() != PetRecallWireStatus.APPLIED) {
            entity.discard();
            if (result != null && result.status() == PetRecallWireStatus.UNAVAILABLE) {
                outcome.complete(PetRecallOutcome.of(
                        PetRecallStatus.UNAVAILABLE,
                        result.pet().orElse(before), result.nextAvailableAt().orElse(null),
                        "Monthly recall already used"));
            } else {
                outcome.complete(PetRecallOutcome.of(
                        result != null && result.status() == PetRecallWireStatus.NOT_FOUND
                                ? PetRecallStatus.NO_PET : PetRecallStatus.AUTHORITY_REJECTED,
                        result == null ? before : result.pet().orElse(before), null,
                        "Recall authority rejected the operation"));
            }
            return;
        }
        Pet committed = result.pet().orElseThrow();
        if (!matches(committed, world, target, entityUuid, before)) {
            entity.discard();
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.SPAWN_FAILED_UNRESOLVED, committed, null,
                    "Authority returned an unexpected recall identity"));
            return;
        }
        discardLoadedPreviousRepresentation(server, before);
        ((PetEntityData) entity).aipets$setRecordVersion(committed.recordVersion());
        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player == null || !player.isAlive() || player.level() != world) {
            entity.discard();
            compensate(committed, entityUuid, operationId, outcome, "Owner context changed");
            return;
        }
        boolean spawned;
        try {
            spawned = entitySpawner.spawn(world, entity);
        } catch (RuntimeException failure) {
            spawned = false;
        }
        if (spawned) {
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.RECALLED, committed,
                    result.nextAvailableAt().orElse(null), "Pet recalled"));
            return;
        }
        entity.discard();
        compensate(committed, entityUuid, operationId, outcome, "Destination spawn failed");
    }

    private void compensate(
            Pet committed,
            UUID entityUuid,
            UUID operationId,
            CompletableFuture<PetRecallOutcome> outcome,
            String detail) {
        PetTransitions.CompensateRecallFailure command =
                new PetTransitions.CompensateRecallFailure(
                        committed.recordVersion(), backendId, entityUuid, clock.instant());
        try {
            authority.compensateRecallFailure(operationId, committed.petId(), command)
                    .whenComplete((result, failure) -> {
                        boolean compensated = failure == null
                                && result != null
                                && result.status() == PetRecallWireStatus.COMPENSATED;
                        outcome.complete(PetRecallOutcome.of(
                                compensated
                                        ? PetRecallStatus.SPAWN_FAILED_COMPENSATED
                                        : PetRecallStatus.SPAWN_FAILED_UNRESOLVED,
                                result == null ? committed : result.pet().orElse(committed),
                                null,
                                compensated ? detail + "; returned to held and entitlement released"
                                        : detail + "; compensation failed"));
                    });
        } catch (RuntimeException failure) {
            outcome.complete(PetRecallOutcome.of(
                    PetRecallStatus.SPAWN_FAILED_UNRESOLVED, committed, null,
                    detail + "; compensation failed"));
        }
    }

    private static void discardLoadedPreviousRepresentation(MinecraftServer server, Pet before) {
        if (!(before.placement() instanceof PlacedPlacement placed) || placed.entityUuid().isEmpty()) {
            return;
        }
        UUID oldEntityId = placed.entityUuid().orElseThrow();
        for (ServerLevel candidateWorld : server.getAllLevels()) {
            Entity loaded = candidateWorld.getEntity(oldEntityId);
            if (loaded instanceof PetEntityData marker
                    && marker.aipets$isPet()
                    && before.petId().equals(marker.aipets$getPetId())) {
                loaded.discard();
                return;
            }
        }
    }

    private boolean matches(
            Pet committed,
            ServerLevel world,
            WorldPosition target,
            UUID entityUuid,
            Pet before) {
        if (!(committed.placement() instanceof PlacedPlacement placed)) return false;
        return committed.petId().equals(before.petId())
                && committed.ownerUuid().equals(before.ownerUuid())
                && placed.backendId().equals(backendId)
                && placed.dimensionId().toString().equals(world.dimension().identifier().toString())
                && placed.position().equals(target)
                && placed.entityUuid().filter(entityUuid::equals).isPresent();
    }

    private static void onServer(MinecraftServer server, Runnable action) {
        if (server.isSameThread()) action.run(); else server.execute(action);
    }

    @FunctionalInterface
    public interface EntitySpawner {
        boolean spawn(ServerLevel world, TamableAnimal entity);
    }
}
