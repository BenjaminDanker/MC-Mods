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
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.GlobalPos;

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
            ServerPlayerEntity player,
            PetAuthoritySnapshot snapshot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        Pet pet = snapshot.pet();
        if (!pet.ownerUuid().equals(player.getUuid())) {
            throw new IllegalArgumentException("Authority snapshot does not belong to player");
        }

        Reconciliation reconciliation = reconcileInventory(player, Optional.of(snapshot));
        if (reconciliation.kept().isPresent()) {
            return new PetCompassIssueResult(
                    PetCompassIssueStatus.REFRESHED,
                    reconciliation.presentation(),
                    reconciliation.removed());
        }

        ItemStack compass = PetCompassItem.create(pet, signer);
        String presentation = updatePresentation(player, compass, snapshot);
        if (player.getInventory().getEmptySlot() < 0) {
            return new PetCompassIssueResult(
                    PetCompassIssueStatus.INVENTORY_FULL,
                    presentation,
                    reconciliation.removed());
        }
        if (!player.getInventory().insertStack(compass)) {
            return new PetCompassIssueResult(
                    PetCompassIssueStatus.INVENTORY_FULL,
                    presentation,
                    reconciliation.removed());
        }
        player.getInventory().markDirty();
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

    public void validateAsync(ServerPlayerEntity player) {
        UUID ownerUuid = player.getUuid();
        if (!containsCandidate(player.getInventory()) || !inFlight.add(ownerUuid)) {
            return;
        }
        try {
            authority.findByOwner(ownerUuid).whenComplete((snapshot, failure) -> {
                MinecraftServer server = player.getEntityWorld().getServer();
                Runnable finish = () -> {
                    try {
                        ServerPlayerEntity current = server.getPlayerManager().getPlayer(ownerUuid);
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
                    if (server.isOnThread()) {
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
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            validateAsync(player);
        }
    }

    public void clear() {
        ticks = 0L;
        inFlight.clear();
    }

    private Reconciliation reconcileInventory(
            ServerPlayerEntity player,
            Optional<PetAuthoritySnapshot> authoritySnapshot) {
        PlayerInventory inventory = player.getInventory();
        Pet expectedPet = authoritySnapshot.map(PetAuthoritySnapshot::pet).orElse(null);
        ItemStack kept = null;
        int removed = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!PetCompassItem.isCandidate(stack)) {
                continue;
            }
            Optional<PetCompassMarker> marker = PetCompassItem.trustedMarker(stack, signer);
            boolean matches = expectedPet != null
                    && marker.isPresent()
                    && marker.orElseThrow().ownerUuid().equals(player.getUuid())
                    && marker.orElseThrow().petId().equals(expectedPet.petId());
            if (!matches || kept != null) {
                inventory.setStack(slot, ItemStack.EMPTY);
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
            inventory.markDirty();
        }
        return new Reconciliation(Optional.ofNullable(kept), presentation, removed);
    }

    private String updatePresentation(
            ServerPlayerEntity player,
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
                            player.getEntityWorld().getRegistryKey().getValue().toString());
                    if (!placed.dimensionId().equals(playerDimension)) {
                        status = "In " + placed.dimensionId();
                    } else {
                        Optional<BlockPos> livePosition = livePosition(player.getEntityWorld(), pet, placed);
                        BlockPos targetPosition = livePosition.orElseGet(() -> blockPosition(placed.position()));
                        target = Optional.of(GlobalPos.create(
                                player.getEntityWorld().getRegistryKey(),
                                targetPosition));
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
            ServerWorld world,
            Pet pet,
            PlacedPlacement placed) {
        if (placed.entityUuid().isEmpty()) {
            return Optional.empty();
        }
        Entity entity = world.getEntityAnyDimension(placed.entityUuid().orElseThrow());
        if (entity == null || entity.getEntityWorld() != world || !(entity instanceof PetEntityData data)) {
            return Optional.empty();
        }
        if (!data.aipets$isPet()
                || !pet.petId().equals(data.aipets$getPetId())
                || !pet.ownerUuid().equals(data.aipets$getOwnerUuid())) {
            return Optional.empty();
        }
        return Optional.of(entity.getBlockPos());
    }

    private String friendlyName(BackendId backendId) {
        return friendlyNames.getOrDefault(backendId, backendId.value());
    }

    private static BlockPos blockPosition(WorldPosition position) {
        return BlockPos.ofFloored(position.x(), position.y(), position.z());
    }

    private static boolean containsCandidate(PlayerInventory inventory) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (PetCompassItem.isCandidate(inventory.getStack(slot))) {
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
