package com.silver.aipets.fabric.reconciliation;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityData;
import com.silver.aipets.fabric.entity.PetEntityFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;

/**
 * Lazily reconstructs a missing authoritative representation only in an already-loaded chunk.
 * A second authority read after stale-entity reconciliation closes the async race before spawn.
 */
public final class PetEntityRecoveryCoordinator {
    private static final int SCAN_INTERVAL_TICKS = 100;

    private final BackendId backendId;
    private final PetAuthorityGateway authority;
    private final PetEntityReconciler reconciler;
    private final PetEntityFactory factory;
    private final Set<UUID> ownersInFlight = ConcurrentHashMap.newKeySet();
    private int ticksUntilScan;

    public PetEntityRecoveryCoordinator(
            BackendId backendId,
            PetAuthorityGateway authority,
            PetEntityReconciler reconciler,
            PetEntityFactory factory) {
        this.backendId = Objects.requireNonNull(backendId, "backendId");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.reconciler = Objects.requireNonNull(reconciler, "reconciler");
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (ticksUntilScan-- > 0) return;
        ticksUntilScan = SCAN_INTERVAL_TICKS - 1;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            recoverOwner(server, player.getUUID());
        }
    }

    public CompletionStage<PetRecoveryStatus> recoverOwner(
            MinecraftServer server, UUID ownerUuid) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        if (!ownersInFlight.add(ownerUuid)) {
            return CompletableFuture.completedFuture(PetRecoveryStatus.CONTEXT_CHANGED);
        }
        CompletableFuture<PetRecoveryStatus> result = new CompletableFuture<>();
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null || snapshot == null) {
                    finish(ownerUuid, result, PetRecoveryStatus.SERVICE_FAILURE, failure);
                    return;
                }
                onServer(server, () -> inspectLoadedContext(
                        server, ownerUuid, snapshot, result));
            });
        } catch (RuntimeException failure) {
            finish(ownerUuid, result, PetRecoveryStatus.SERVICE_FAILURE, failure);
        }
        return result;
    }

    public void clear() {
        ownersInFlight.clear();
        ticksUntilScan = 0;
    }

    private void inspectLoadedContext(
            MinecraftServer server,
            UUID ownerUuid,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetRecoveryStatus> result) {
        if (server.getPlayerList().getPlayer(ownerUuid) == null) {
            finish(ownerUuid, result, PetRecoveryStatus.OWNER_OFFLINE, null);
            return;
        }
        if (snapshot.isEmpty()) {
            finish(ownerUuid, result, PetRecoveryStatus.NO_PET, null);
            return;
        }
        Pet pet = snapshot.orElseThrow().pet();
        LocalPlacement local = localLoadedPlacement(server, pet).orElse(null);
        if (local == null) {
            PetRecoveryStatus status = isLocalPlacement(pet)
                    ? PetRecoveryStatus.DEFERRED_UNLOADED_CHUNK
                    : PetRecoveryStatus.NOT_LOCAL_PLACED;
            finish(ownerUuid, result, status, null);
            return;
        }
        List<CompletableFuture<PetEntityReconciliationResult>> checks = new ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            for (Entity entity : world.getAllEntities()) {
                if (entity instanceof PetEntityData data
                        && data.aipets$isPet()
                        && pet.petId().equals(data.aipets$getPetId())
                        && !entity.isRemoved()) {
                    checks.add(reconciler.reconcileLoaded(entity, world));
                }
            }
        }
        CompletableFuture.allOf(checks.toArray(CompletableFuture[]::new)).whenComplete(
                (ignored, failure) -> {
                    boolean reconciliationUnavailable = failure == null && checks.stream()
                            .map(CompletableFuture::join)
                            .anyMatch(check -> check.status()
                                    == PetEntityReconciliationStatus.SERVICE_FAILURE);
                    if (failure != null || reconciliationUnavailable) {
                        finish(ownerUuid, result, PetRecoveryStatus.SERVICE_FAILURE,
                                failure == null
                                        ? new IllegalStateException("Candidate reconciliation failed")
                                        : failure);
                        return;
                    }
                    refreshBeforeSpawn(server, ownerUuid, pet.petId(), result);
                });
    }

    private void refreshBeforeSpawn(
            MinecraftServer server,
            UUID ownerUuid,
            UUID expectedPetId,
            CompletableFuture<PetRecoveryStatus> result) {
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                if (failure != null || snapshot == null) {
                    finish(ownerUuid, result, PetRecoveryStatus.SERVICE_FAILURE, failure);
                    return;
                }
                onServer(server, () -> spawnIfStillMissing(
                        server, ownerUuid, expectedPetId, snapshot, result));
            });
        } catch (RuntimeException failure) {
            finish(ownerUuid, result, PetRecoveryStatus.SERVICE_FAILURE, failure);
        }
    }

    private void spawnIfStillMissing(
            MinecraftServer server,
            UUID ownerUuid,
            UUID expectedPetId,
            Optional<PetAuthoritySnapshot> snapshot,
            CompletableFuture<PetRecoveryStatus> result) {
        if (server.getPlayerList().getPlayer(ownerUuid) == null || snapshot.isEmpty()
                || !snapshot.orElseThrow().pet().petId().equals(expectedPetId)) {
            finish(ownerUuid, result, PetRecoveryStatus.CONTEXT_CHANGED, null);
            return;
        }
        PetAuthoritySnapshot current = snapshot.orElseThrow();
        Pet pet = current.pet();
        LocalPlacement local = localLoadedPlacement(server, pet).orElse(null);
        if (local == null) {
            finish(ownerUuid, result, isLocalPlacement(pet)
                    ? PetRecoveryStatus.DEFERRED_UNLOADED_CHUNK
                    : PetRecoveryStatus.CONTEXT_CHANGED, null);
            return;
        }
        UUID entityUuid = local.placement().entityUuid().orElseThrow();
        Entity existing = local.world().getEntityInAnyDimension(entityUuid);
        if (existing != null && !existing.isRemoved()) {
            finish(ownerUuid, result, PetRecoveryStatus.AUTHORITATIVE_ENTITY_PRESENT, null);
            return;
        }
        var prepared = factory.prepare(
                local.world(), pet, entityUuid, local.placement().position(), current.sleeping());
        if (!local.world().addFreshEntity(prepared.entity())) {
            prepared.entity().discard();
            finish(ownerUuid, result, PetRecoveryStatus.CONTEXT_CHANGED, null);
            return;
        }
        finish(ownerUuid, result, PetRecoveryStatus.RECONSTRUCTED, null);
    }

    private Optional<LocalPlacement> localLoadedPlacement(MinecraftServer server, Pet pet) {
        if (!(pet.placement() instanceof PlacedPlacement placed)
                || !placed.backendId().equals(backendId)
                || placed.entityUuid().isEmpty()) {
            return Optional.empty();
        }
        for (ServerLevel world : server.getAllLevels()) {
            DimensionId dimension = DimensionId.parse(world.dimension().identifier().toString());
            if (!dimension.equals(placed.dimensionId())) continue;
            BlockPos block = BlockPos.containing(
                    placed.position().x(), placed.position().y(), placed.position().z());
            ChunkPos chunk = ChunkPos.containing(block);
            if (world.getChunkSource().hasChunk(chunk.x(), chunk.z())) {
                return Optional.of(new LocalPlacement(world, placed));
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private boolean isLocalPlacement(Pet pet) {
        return pet.placement() instanceof PlacedPlacement placed
                && placed.backendId().equals(backendId)
                && placed.entityUuid().isPresent();
    }

    private void finish(
            UUID ownerUuid,
            CompletableFuture<PetRecoveryStatus> result,
            PetRecoveryStatus status,
            Throwable failure) {
        ownersInFlight.remove(ownerUuid);
        if (failure != null) {
            PetCompanionMod.LOGGER.warn(StructuredPetEvent
                    .operation("entity_recovery")
                    .owner(ownerUuid)
                    .backend(backendId)
                    .failure(failure)
                    .outcome(status.name().toLowerCase(java.util.Locale.ROOT))
                    .toJson());
        } else if (status == PetRecoveryStatus.RECONSTRUCTED) {
            PetCompanionMod.LOGGER.info(StructuredPetEvent
                    .operation("entity_recovery")
                    .owner(ownerUuid)
                    .backend(backendId)
                    .outcome("reconstructed")
                    .toJson());
        }
        result.complete(status);
    }

    private static void onServer(MinecraftServer server, Runnable action) {
        if (server.isSameThread()) action.run();
        else server.execute(action);
    }

    private record LocalPlacement(ServerLevel world, PlacedPlacement placement) {
    }
}
