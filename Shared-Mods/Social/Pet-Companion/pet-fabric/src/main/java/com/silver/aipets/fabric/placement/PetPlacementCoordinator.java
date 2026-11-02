package com.silver.aipets.fabric.placement;

import com.silver.aipets.common.authority.PetTransitions;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.fabric.authority.AuthorityMutationResult;
import com.silver.aipets.fabric.authority.AuthorityMutationStatus;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import com.silver.aipets.fabric.entity.PreparedPetEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.TamableAnimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Server-thread lifecycle for safe commit-before-spawn placement. Database/service I/O remains
 * asynchronous; every Minecraft world/entity operation is marshalled back to the server thread.
 */
public final class PetPlacementCoordinator {
    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final SafePlacementFinder safePlacementFinder;
    private final PetEntityFactory entityFactory;
    private final Clock clock;
    private final Supplier<UUID> operationIds;
    private final Supplier<UUID> entityIds;
    private final EntitySpawner entitySpawner;
    private final Set<UUID> ownersInFlight = ConcurrentHashMap.newKeySet();

    public PetPlacementCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            SafePlacementFinder safePlacementFinder,
            PetEntityFactory entityFactory,
            Clock clock,
            Supplier<UUID> operationIds,
            Supplier<UUID> entityIds) {
        this(
                backendId,
                authority,
                safePlacementFinder,
                entityFactory,
                clock,
                operationIds,
                entityIds,
                (world, entity) -> world.addFreshEntity(entity));
    }

    public PetPlacementCoordinator(
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

    public CompletableFuture<PetPlacementOutcome> place(ServerPlayer initiatingPlayer) {
        Objects.requireNonNull(initiatingPlayer, "initiatingPlayer");
        MinecraftServer server = initiatingPlayer.level().getServer();
        UUID ownerUuid = initiatingPlayer.getUUID();
        if (!ownersInFlight.add(ownerUuid)) {
            return CompletableFuture.completedFuture(PetPlacementOutcome.of(
                    PetPlacementStatus.ALREADY_IN_PROGRESS,
                    null,
                    "A placement request is already in progress for this owner"));
        }
        CompletableFuture<PetPlacementOutcome> outcome = new CompletableFuture<>();
        outcome.whenComplete((ignored, failure) -> ownersInFlight.remove(ownerUuid));

        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null) {
                    outcome.complete(PetPlacementOutcome.of(
                            PetPlacementStatus.SERVICE_FAILURE,
                            null,
                            "Pet authority lookup failed"));
                    return;
                }
                onServer(server, () -> {
                    try {
                        prepareAndCommit(
                                server,
                                ownerUuid,
                                Objects.requireNonNull(snapshot, "snapshot"),
                                outcome);
                    } catch (RuntimeException unexpected) {
                        outcome.complete(PetPlacementOutcome.of(
                                PetPlacementStatus.SERVICE_FAILURE,
                                null,
                                "Placement preparation failed safely"));
                    }
                });
            });
        } catch (RuntimeException failure) {
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.SERVICE_FAILURE,
                    null,
                    "Pet authority lookup failed"));
        }
        return outcome;
    }

    private void prepareAndCommit(
            MinecraftServer server,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetPlacementOutcome> outcome) {
        if (outcome.isDone()) {
            return;
        }
        if (snapshot.isEmpty()) {
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.NO_PET, null, "No adopted pet exists"));
            return;
        }

        PetAuthoritySnapshot authoritySnapshot = snapshot.orElseThrow();
        Pet pet = authoritySnapshot.pet();
        if (!pet.ownerUuid().equals(ownerUuid) || !(pet.placement() instanceof HeldPlacement)) {
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.NOT_HELD, pet, "Pet is not held by this owner"));
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player == null || !player.isAlive()) {
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.PLAYER_CONTEXT_CHANGED,
                    pet,
                    "Owner left before placement"));
            return;
        }
        ServerLevel world = player.level();
        DimensionId dimensionId = DimensionId.parse(world.dimension().identifier().toString());
        UUID entityUuid = requireUuid(entityIds, "entity ID");
        WorldPosition playerPosition = position(player);
        PreparedPetEntity prepared = entityFactory.prepare(
                world,
                pet,
                entityUuid,
                playerPosition,
                authoritySnapshot.sleeping());
        Optional<WorldPosition> safePosition = safePlacementFinder.find(
                world, prepared.entity(), player.blockPosition());
        if (safePosition.isEmpty()) {
            prepared.entity().discard();
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.NO_SAFE_POSITION,
                    pet,
                    "No safe loaded position was found"));
            return;
        }

        WorldPosition target = safePosition.orElseThrow();
        prepared.entity().snapTo(
                target.x(), target.y(), target.z(), player.getYRot(), 0.0F);
        UUID operationId = requireUuid(operationIds, "operation ID");
        Instant occurredAt = clock.instant();
        PetTransitions.Place command = new PetTransitions.Place(
                ownerUuid,
                pet.recordVersion(),
                backendId,
                dimensionId,
                target,
                entityUuid,
                occurredAt);
        PreparedPlacement placement = new PreparedPlacement(
                world,
                dimensionId,
                prepared.entity(),
                pet,
                entityUuid,
                target);

        try {
            authority.place(operationId, pet.petId(), command).whenComplete((commit, failure) -> {
                if (failure != null) {
                    onServer(server, () -> {
                        placement.entity().discard();
                        outcome.complete(PetPlacementOutcome.of(
                                PetPlacementStatus.SERVICE_FAILURE,
                                pet,
                                "Pet authority placement failed"));
                    });
                    return;
                }
                onServer(server, () -> {
                    AuthorityMutationResult nonNullCommit = Objects.requireNonNull(commit, "commit");
                    try {
                        handleCommit(server, ownerUuid, placement, nonNullCommit, outcome);
                    } catch (RuntimeException unexpected) {
                        placement.entity().discard();
                        if (nonNullCommit.status() == AuthorityMutationStatus.APPLIED
                                && nonNullCommit.pet().isPresent()) {
                            compensate(
                                    placement,
                                    nonNullCommit.pet().orElseThrow(),
                                    outcome,
                                    "Post-commit placement handling failed");
                        } else {
                            outcome.complete(PetPlacementOutcome.of(
                                    PetPlacementStatus.SERVICE_FAILURE,
                                    placement.preCommitPet(),
                                    "Placement response handling failed safely"));
                        }
                    }
                });
            });
        } catch (RuntimeException failure) {
            placement.entity().discard();
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.SERVICE_FAILURE,
                    pet,
                    "Pet authority placement failed"));
        }
    }

    private void handleCommit(
            MinecraftServer server,
            UUID ownerUuid,
            PreparedPlacement placement,
            AuthorityMutationResult commit,
            CompletableFuture<PetPlacementOutcome> outcome) {
        if (outcome.isDone()) {
            placement.entity().discard();
            return;
        }
        if (commit.status() != AuthorityMutationStatus.APPLIED) {
            placement.entity().discard();
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.AUTHORITY_REJECTED,
                    commit.pet().orElse(placement.preCommitPet()),
                    "Authoritative placement was not applied"));
            return;
        }

        Pet committed = commit.pet().orElseThrow();
        if (!matchesCommittedPlacement(committed, placement)) {
            placement.entity().discard();
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.SPAWN_FAILED_UNRESOLVED,
                    committed,
                    "Authority returned an unexpected placement identity"));
            return;
        }

        ((PetEntityData) placement.entity()).aipets$setRecordVersion(committed.recordVersion());
        ServerPlayer currentPlayer = server.getPlayerList().getPlayer(ownerUuid);
        if (currentPlayer == null
                || !currentPlayer.isAlive()
                || currentPlayer.level() != placement.world()) {
            placement.entity().discard();
            compensate(placement, committed, outcome, "Owner context changed after commit");
            return;
        }

        boolean spawned;
        try {
            spawned = entitySpawner.spawn(placement.world(), placement.entity());
        } catch (RuntimeException failure) {
            spawned = false;
        }
        if (spawned) {
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.PLACED,
                    committed,
                    "Pet placed"));
            return;
        }
        placement.entity().discard();
        compensate(placement, committed, outcome, "Entity spawn failed after commit");
    }

    private void compensate(
            PreparedPlacement placement,
            Pet committed,
            CompletableFuture<PetPlacementOutcome> outcome,
            String reason) {
        try {
            UUID compensationId = requireUuid(operationIds, "compensation operation ID");
            PetTransitions.CompensatePlaceFailure command = new PetTransitions.CompensatePlaceFailure(
                    committed.recordVersion(),
                    backendId,
                    placement.entityUuid(),
                    clock.instant());
            authority.compensatePlaceFailure(
                            compensationId,
                            committed.petId(),
                            command)
                    .whenComplete((compensation, failure) -> {
                        if (failure != null || compensation == null) {
                            outcome.complete(PetPlacementOutcome.of(
                                    PetPlacementStatus.SPAWN_FAILED_UNRESOLVED,
                                    committed,
                                    reason + "; compensation failed"));
                            return;
                        }
                        boolean applied = compensation.status() == AuthorityMutationStatus.APPLIED;
                        outcome.complete(PetPlacementOutcome.of(
                                applied
                                        ? PetPlacementStatus.SPAWN_FAILED_COMPENSATED
                                        : PetPlacementStatus.SPAWN_FAILED_UNRESOLVED,
                                compensation.pet().orElse(committed),
                                applied ? reason + "; returned to held" : reason + "; compensation rejected"));
                    });
        } catch (RuntimeException failure) {
            outcome.complete(PetPlacementOutcome.of(
                    PetPlacementStatus.SPAWN_FAILED_UNRESOLVED,
                    committed,
                    reason + "; compensation failed"));
        }
    }

    private boolean matchesCommittedPlacement(Pet committed, PreparedPlacement placement) {
        if (!(committed.placement() instanceof PlacedPlacement placed)) {
            return false;
        }
        return placed.backendId().equals(backendId)
                && placed.dimensionId().equals(placement.dimensionId())
                && placed.position().equals(placement.targetPosition())
                && placed.entityUuid().filter(placement.entityUuid()::equals).isPresent()
                && committed.petId().equals(placement.preCommitPet().petId())
                && committed.ownerUuid().equals(placement.preCommitPet().ownerUuid());
    }

    private static void onServer(MinecraftServer server, Runnable action) {
        if (server.isSameThread()) {
            action.run();
        } else {
            server.execute(action);
        }
    }

    private static UUID requireUuid(Supplier<UUID> supplier, String name) {
        return Objects.requireNonNull(supplier.get(), name + " supplier returned null");
    }

    private static WorldPosition position(ServerPlayer player) {
        return new WorldPosition(player.getX(), player.getY(), player.getZ());
    }

    @FunctionalInterface
    public interface EntitySpawner {
        boolean spawn(ServerLevel world, TamableAnimal entity);
    }

    private record PreparedPlacement(
            ServerLevel world,
            DimensionId dimensionId,
            TamableAnimal entity,
            Pet preCommitPet,
            UUID entityUuid,
            WorldPosition targetPosition) {
    }
}
