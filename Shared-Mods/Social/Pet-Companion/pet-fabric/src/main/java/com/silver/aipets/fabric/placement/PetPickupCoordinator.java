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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Validates proximity and exact identity, commits PLACED -> HELD, then discards the entity. */
public final class PetPickupCoordinator {
    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final double maximumDistance;
    private final Clock clock;
    private final Supplier<UUID> operationIds;

    public PetPickupCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            double maximumDistance,
            Clock clock,
            Supplier<UUID> operationIds) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.authority = Objects.requireNonNull(authority, "authority");
        if (!Double.isFinite(maximumDistance) || maximumDistance < 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and non-negative");
        }
        this.maximumDistance = maximumDistance;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.operationIds = Objects.requireNonNull(operationIds, "operationIds");
    }

    public CompletableFuture<PetPickupOutcome> pickup(ServerPlayer initiatingPlayer) {
        Objects.requireNonNull(initiatingPlayer, "initiatingPlayer");
        MinecraftServer server = initiatingPlayer.level().getServer();
        UUID ownerUuid = initiatingPlayer.getUUID();
        CompletableFuture<PetPickupOutcome> outcome = new CompletableFuture<>();
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null) {
                    outcome.complete(PetPickupOutcome.of(
                            PetPickupStatus.SERVICE_FAILURE, null, "Pet authority lookup failed"));
                    return;
                }
                onServer(server, () -> {
                    try {
                        validateAndCommit(
                                server,
                                ownerUuid,
                                Objects.requireNonNull(snapshot, "snapshot"),
                                outcome);
                    } catch (RuntimeException unexpected) {
                        outcome.complete(PetPickupOutcome.of(
                                PetPickupStatus.SERVICE_FAILURE,
                                null,
                                "Pickup validation failed safely"));
                    }
                });
            });
        } catch (RuntimeException failure) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.SERVICE_FAILURE, null, "Pet authority lookup failed"));
        }
        return outcome;
    }

    private void validateAndCommit(
            MinecraftServer server,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetPickupOutcome> outcome) {
        if (snapshot.isEmpty()) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.NO_PET, null, "No adopted pet exists"));
            return;
        }
        Pet pet = snapshot.orElseThrow().pet();
        if (!pet.ownerUuid().equals(ownerUuid)
                || !(pet.placement() instanceof PlacedPlacement placed)
                || !placed.backendId().equals(backendId)
                || placed.entityUuid().isEmpty()) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.NOT_PLACED_HERE,
                    pet,
                    "Pet is not materially placed on this backend"));
            return;
        }

        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player == null || !player.isAlive()) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.NOT_PLACED_HERE,
                    pet,
                    "Owner left before pickup"));
            return;
        }
        ServerLevel world = player.level();
        DimensionId dimensionId = DimensionId.parse(world.dimension().identifier().toString());
        if (!placed.dimensionId().equals(dimensionId)) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.NOT_PLACED_HERE,
                    pet,
                    "Pet is in another dimension"));
            return;
        }

        UUID entityUuid = placed.entityUuid().orElseThrow();
        Entity entity = world.getEntityInAnyDimension(entityUuid);
        if (entity == null
                || entity.level() != world
                || !(entity instanceof PetEntityData data)
                || !data.aipets$isPet()
                || !pet.petId().equals(data.aipets$getPetId())
                || !ownerUuid.equals(data.aipets$getOwnerUuid())
                || data.aipets$getRecordVersion() != pet.recordVersion()) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.ENTITY_MISSING_OR_STALE,
                    pet,
                    "Authoritative physical entity is missing or stale"));
            return;
        }

        WorldPosition ownerPosition = new WorldPosition(player.getX(), player.getY(), player.getZ());
        WorldPosition entityPosition = new WorldPosition(entity.getX(), entity.getY(), entity.getZ());
        if (!ownerPosition.isWithin(entityPosition, maximumDistance)) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.OUT_OF_RANGE,
                    pet,
                    "Pet is outside the pickup radius"));
            return;
        }

        UUID operationId = Objects.requireNonNull(
                operationIds.get(), "operation ID supplier returned null");
        PetTransitions.Pickup command = new PetTransitions.Pickup(
                ownerUuid,
                pet.recordVersion(),
                backendId,
                dimensionId,
                entityUuid,
                ownerPosition,
                entityPosition,
                maximumDistance,
                clock.instant());
        try {
            authority.pickup(operationId, pet.petId(), command).whenComplete((commit, failure) -> {
                if (failure != null) {
                    outcome.complete(PetPickupOutcome.of(
                            PetPickupStatus.SERVICE_FAILURE,
                            pet,
                            "Pet authority pickup failed"));
                    return;
                }
                onServer(server, () -> handleCommit(
                        entity,
                        pet,
                        Objects.requireNonNull(commit, "commit"),
                        outcome));
            });
        } catch (RuntimeException failure) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.SERVICE_FAILURE,
                    pet,
                    "Pet authority pickup failed"));
        }
    }

    private static void handleCommit(
            Entity entity,
            Pet previous,
            AuthorityMutationResult commit,
            CompletableFuture<PetPickupOutcome> outcome) {
        if (commit.status() != AuthorityMutationStatus.APPLIED) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.AUTHORITY_REJECTED,
                    commit.pet().orElse(previous),
                    "Authoritative pickup was not applied"));
            return;
        }
        Pet committed = commit.pet().orElseThrow();
        if (!committed.petId().equals(previous.petId())
                || !committed.ownerUuid().equals(previous.ownerUuid())
                || !(committed.placement() instanceof HeldPlacement)) {
            outcome.complete(PetPickupOutcome.of(
                    PetPickupStatus.AUTHORITY_REJECTED,
                    committed,
                    "Authority returned an unexpected pickup state"));
            return;
        }
        if (!entity.isRemoved()) {
            entity.discard();
        }
        outcome.complete(PetPickupOutcome.of(
                PetPickupStatus.PICKED_UP,
                committed,
                "Pet picked up"));
    }

    private static void onServer(MinecraftServer server, Runnable action) {
        if (server.isSameThread()) {
            action.run();
        } else {
            server.execute(action);
        }
    }
}
