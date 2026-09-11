package com.silver.atlantis.protect;

import com.silver.atlantis.AtlantisMod;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Holds the single active protected structure.
 *
 * Placed blocks are sparse long keys. Interior air is queried through its
 * local BitSet mask. Exterior air is not represented and is never protected.
 */
public final class ProtectionManager {

    public static final ProtectionManager INSTANCE = new ProtectionManager();

    private final Object2LongOpenHashMap<UUID> lastWarnNanosByPlayer = new Object2LongOpenHashMap<>();
    private static final long WARN_COOLDOWN_NANOS = 2_000_000_000L;

    private ProtectionEntry active;

    private ProtectionManager() {
    }

    public synchronized void register(ProtectionEntry entry) {
        if (entry == null || entry.id() == null || entry.dimensionId() == null) {
            return;
        }

        ProtectionEntry previous = active;
        active = entry;
        if (previous != null && !previous.id().equals(entry.id())) {
            AtlantisMod.LOGGER.info(
                "[Protection] replaced active structure {} with {}",
                previous.id(),
                entry.id()
            );
        }
    }

    public synchronized boolean unregister(String id) {
        if (id == null || active == null || !id.equals(active.id())) {
            return false;
        }

        active = null;
        return true;
    }

    public synchronized boolean isBreakProtected(ServerWorld world, BlockPos pos) {
        return isAnyProtected(world, pos);
    }

    public synchronized boolean isPlaceProtected(ServerWorld world, BlockPos pos) {
        return isAnyProtected(world, pos);
    }

    public synchronized boolean isInteriorProtected(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null || active == null) {
            return false;
        }
        if (!dimensionId(world).equals(active.dimensionId())) {
            return false;
        }

        InteriorMask mask = active.interiorMask();
        return mask != null && mask.contains(pos);
    }

    public synchronized boolean isAnyProtected(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null || active == null) {
            return false;
        }
        if (!dimensionId(world).equals(active.dimensionId())) {
            return false;
        }

        if (active.placedPositions().contains(pos.asLong())) {
            return true;
        }

        InteriorMask mask = active.interiorMask();
        return mask != null && mask.contains(pos);
    }

    public boolean shouldBlockBreak(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (!isAllowedBypass(player)) {
            ServerWorld serverWorld = player.getEntityWorld();
            boolean blocked = isBreakProtected(serverWorld, pos);
            if (blocked) {
                maybeLogBlocked(player, "break", pos);
            }
            return blocked;
        }

        return false;
    }

    public boolean shouldBlockPlace(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (!isAllowedBypass(player)) {
            ServerWorld serverWorld = player.getEntityWorld();
            boolean blocked = isPlaceProtected(serverWorld, pos);
            if (blocked) {
                maybeLogBlocked(player, "place", pos);
            }
            return blocked;
        }

        return false;
    }

    private void maybeLogBlocked(ServerPlayerEntity player, String action, BlockPos pos) {
        UUID id = player.getUuid();
        long now = System.nanoTime();
        long last = lastWarnNanosByPlayer.getOrDefault(id, 0L);
        if (last != 0L && (now - last) < WARN_COOLDOWN_NANOS) {
            return;
        }
        lastWarnNanosByPlayer.put(id, now);

        AtlantisMod.LOGGER.info(
            "Protection blocked {} by {} at {} (dim={})",
            action,
            player.getUuid(),
            pos.toShortString(),
            player.getEntityWorld().getRegistryKey().getValue()
        );
    }

    private static String dimensionId(ServerWorld world) {
        return world.getRegistryKey().getValue().toString();
    }

    /**
     * Only ops in creative can bypass protections.
     */
    private static boolean isAllowedBypass(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) && player.getAbilities().creativeMode;
    }
}
