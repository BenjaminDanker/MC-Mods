package com.silver.aipets.fabric.compass;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.TransferringPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.fabric.PetCompanionMod;
import com.silver.aipets.fabric.authority.PetAuthorityGateway;
import com.silver.aipets.fabric.authority.PetAuthoritySnapshot;
import com.silver.aipets.fabric.entity.PetEntityData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Cached-read compass lifecycle; this class never performs an authority mutation. */
public final class PetCompassManager {
    private static final long REFRESH_INTERVAL_TICKS = 200L;

    private final BackendId localBackend;
    private final PetAuthorityGateway authority;
    private final PetCompassSigner signer;
    private final Map<BackendId, String> friendlyNames;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> trackedOwners = ConcurrentHashMap.newKeySet();
    private long ticks;

    public PetCompassManager(
            BackendId localBackend,
            PetAuthorityGateway authority,
            PetCompassSigner signer,
            Map<BackendId, String> friendlyNames) {
        this.localBackend = Objects.requireNonNull(localBackend, "localBackend");
        this.authority = Objects.requireNonNull(authority, "authority");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.friendlyNames = Map.copyOf(Objects.requireNonNull(friendlyNames, "friendlyNames"));
    }

    public PetCompassIssueResult issueOrRefresh(
            ServerPlayer player,
            PetAuthoritySnapshot snapshot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Pet pet = snapshot.pet();
        if (!pet.ownerUuid().equals(player.getUUID())) {
            throw new IllegalArgumentException("Authority snapshot does not belong to player");
        }

        Reconciliation reconciliation = reconcileInventory(player, Optional.of(snapshot));
        if (reconciliation.kept().isPresent()) {
            trackedOwners.add(player.getUUID());
            return new PetCompassIssueResult(
                    PetCompassIssueStatus.REFRESHED,
                    reconciliation.presentation(),
                    reconciliation.removed());
        }

        ItemStack compass = PetCompassItem.create(pet, signer);
        String presentation = updatePresentation(player, compass, snapshot);
        if (player.getInventory().getFreeSlot() < 0) {
            return new PetCompassIssueResult(
                    PetCompassIssueStatus.INVENTORY_FULL,
                    presentation,
                    reconciliation.removed());
        }
        if (!player.getInventory().add(compass)) {
            return new PetCompassIssueResult(
                    PetCompassIssueStatus.INVENTORY_FULL,
                    presentation,
                    reconciliation.removed());
        }
        player.getInventory().setChanged();
        trackedOwners.add(player.getUUID());
        return new PetCompassIssueResult(
                PetCompassIssueStatus.ISSUED,
                presentation,
                reconciliation.removed());
    }

    public boolean isAllowedInPlayerInventory(ItemStack stack, UUID playerUuid) {
        return PetCompassItem.trustedMarker(stack, signer)
                .filter(marker -> marker.ownerUuid().equals(playerUuid))
                .isPresent();
    }

    /** Stops refresh work immediately when a bound compass is discarded into the world. */
    public void onDropped(ItemStack stack) {
        PetCompassItem.trustedMarker(Objects.requireNonNull(stack, "stack"), signer)
                .ifPresent(marker -> trackedOwners.remove(marker.ownerUuid()));
    }

    public void validateAsync(ServerPlayer player) {
        UUID ownerUuid = player.getUUID();
        if (!containsCandidate(player.getInventory())) {
            trackedOwners.remove(ownerUuid);
            return;
        }
        trackedOwners.add(ownerUuid);
        if (!inFlight.add(ownerUuid)) {
            return;
        }
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                MinecraftServer server = player.level().getServer();
                Runnable finish = () -> {
                    try {
                        ServerPlayer current = server.getPlayerList().getPlayer(ownerUuid);
                        if (current == player && failure == null && snapshot != null) {
                            reconcileInventory(current, snapshot);
                        } else if (failure != null) {
                            logValidationFailure(ownerUuid, failure);
                        }
                    } catch (RuntimeException completionFailure) {
                        logValidationFailure(ownerUuid, completionFailure);
                    } finally {
                        inFlight.remove(ownerUuid);
                    }
                };
                try {
                    if (server.isSameThread()) {
                        finish.run();
                    } else {
                        server.execute(finish);
                    }
                } catch (RuntimeException schedulingFailure) {
                    inFlight.remove(ownerUuid);
                    logValidationFailure(ownerUuid, schedulingFailure);
                }
            });
        } catch (RuntimeException failure) {
            inFlight.remove(ownerUuid);
            logValidationFailure(ownerUuid, failure);
        }
    }

    public void onEndServerTick(MinecraftServer server) {
        ticks++;
        if (ticks % REFRESH_INTERVAL_TICKS != 0L) {
            return;
        }
        for (UUID ownerUuid : Set.copyOf(trackedOwners)) {
            ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
            if (player == null) trackedOwners.remove(ownerUuid);
            else validateAsync(player);
        }
    }

    public void clear() {
        ticks = 0L;
        inFlight.clear();
        trackedOwners.clear();
    }

    private Reconciliation reconcileInventory(
            ServerPlayer player,
            Optional<PetAuthoritySnapshot> authoritySnapshot) {
        Inventory inventory = player.getInventory();
        Pet expectedPet = authoritySnapshot.map(PetAuthoritySnapshot::pet).orElse(null);
        ItemStack kept = null;
        int removed = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!PetCompassItem.isCandidate(stack)) {
                continue;
            }
            Optional<PetCompassMarker> marker = PetCompassItem.trustedMarker(stack, signer);
            boolean matches = expectedPet != null
                    && marker.isPresent()
                    && marker.orElseThrow().ownerUuid().equals(player.getUUID())
                    && marker.orElseThrow().petId().equals(expectedPet.petId());
            if (!matches || kept != null) {
                inventory.setItem(slot, ItemStack.EMPTY);
                removed++;
                continue;
            }
            if (stack.getCount() != 1) {
                stack.setCount(1);
                removed++;
            }
            kept = stack;
        }

        String presentation = "";
        if (kept != null) {
            presentation = updatePresentation(player, kept, authoritySnapshot.orElseThrow());
        }
        if (removed > 0 || kept != null) {
            inventory.setChanged();
        }
        return new Reconciliation(Optional.ofNullable(kept), presentation, removed);
    }

    private String updatePresentation(
            ServerPlayer player,
            ItemStack stack,
            PetAuthoritySnapshot snapshot) {
        Pet pet = snapshot.pet();
        String status;
        Optional<GlobalPos> target = Optional.empty();
        switch (pet.placement()) {
            case PlacedPlacement placed -> {
                if (!placed.backendId().equals(localBackend)) {
                    status = "On " + friendlyName(placed.backendId());
                } else {
                    DimensionId playerDimension = DimensionId.parse(
                            player.level().dimension().identifier().toString());
                    if (!placed.dimensionId().equals(playerDimension)) {
                        status = "In " + placed.dimensionId();
                    } else {
                        Optional<BlockPos> livePosition = livePosition(player.level(), pet, placed);
                        BlockPos targetPosition = livePosition.orElseGet(() -> blockPosition(placed.position()));
                        target = Optional.of(GlobalPos.of(
                                player.level().dimension(), targetPosition));
                        status = livePosition.isPresent()
                                ? "Points to current position"
                                : "Points to last known position";
                    }
                }
            }
            case TransferringPlacement transferring ->
                    status = "Moving to " + friendlyName(
                            transferring.transfer().destinationBackendId());
            default -> status = "Held by you";
        }
        PetCompassItem.showStatus(stack, status, target);
        return status;
    }

    private Optional<BlockPos> livePosition(
            ServerLevel world,
            Pet pet,
            PlacedPlacement placed) {
        if (placed.entityUuid().isEmpty()) {
            return Optional.empty();
        }
        Entity entity = world.getEntityInAnyDimension(placed.entityUuid().orElseThrow());
        if (entity == null || entity.level() != world || !(entity instanceof PetEntityData data)) {
            return Optional.empty();
        }
        if (!data.aipets$isPet()
                || !pet.petId().equals(data.aipets$getPetId())
                || !pet.ownerUuid().equals(data.aipets$getOwnerUuid())) {
            return Optional.empty();
        }
        return Optional.of(entity.blockPosition());
    }

    private String friendlyName(BackendId backendId) {
        return friendlyNames.getOrDefault(backendId, backendId.value());
    }

    private static BlockPos blockPosition(WorldPosition position) {
        return BlockPos.containing(position.x(), position.y(), position.z());
    }

    private static boolean containsCandidate(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (PetCompassItem.isCandidate(inventory.getItem(slot))) {
                return true;
            }
        }
        return false;
    }

    private void logValidationFailure(UUID ownerUuid, Throwable failure) {
        PetCompanionMod.LOGGER.warn(StructuredPetEvent
                .operation("compass_validation")
                .owner(ownerUuid).backend(localBackend)
                .failure(failure).outcome("contained").toJson());
    }

    private record Reconciliation(Optional<ItemStack> kept, String presentation, int removed) {
    }
}
