package com.silver.atlantis.protect;

import com.silver.atlantis.AtlantisMod;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

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

    public synchronized boolean isBreakProtected(ServerLevel world, BlockPos pos) {
        return isAnyProtected(world, pos);
    }

    public synchronized boolean isPlaceProtected(ServerLevel world, BlockPos pos) {
        return isAnyProtected(world, pos);
    }

    public synchronized boolean isInteriorProtected(ServerLevel world, BlockPos pos) {
        if (world == null || pos == null || active == null) {
            return false;
        }
        if (!dimensionId(world).equals(active.dimensionId())) {
            return false;
        }

        InteriorMask mask = active.interiorMask();
        return mask != null && mask.contains(pos);
    }

    public synchronized boolean isAnyProtected(ServerLevel world, BlockPos pos) {
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

    public boolean shouldBlockBreak(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (!isAllowedBypass(player)) {
            ServerLevel serverWorld = player.level();
            boolean blocked = isBreakProtected(serverWorld, pos);
            if (blocked) {
                maybeLogBlocked(player, "break", pos);
            }
            return blocked;
        }

        return false;
    }

    public boolean shouldBlockPlace(ServerPlayer player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (!isAllowedBypass(player)) {
            ServerLevel serverWorld = player.level();
            boolean blocked = isPlaceProtected(serverWorld, pos);
            if (blocked) {
                maybeLogBlocked(player, "place", pos);
            }
            return blocked;
        }

        return false;
    }

    private void maybeLogBlocked(ServerPlayer player, String action, BlockPos pos) {
        UUID id = player.getUUID();
        long now = System.nanoTime();
        long last = lastWarnNanosByPlayer.getOrDefault(id, 0L);
        if (last != 0L && (now - last) < WARN_COOLDOWN_NANOS) {
            return;
        }
        lastWarnNanosByPlayer.put(id, now);

        AtlantisMod.LOGGER.info(
            "Protection blocked {} by {} at {} (dim={})",
            action,
            player.getUUID(),
            pos.toShortString(),
            player.level().dimension().identifier()
        );
    }

    private static String dimensionId(ServerLevel world) {
        return world.dimension().identifier().toString();
    }

    /**
     * Only ops in creative can bypass protections.
     */
    private static boolean isAllowedBypass(ServerPlayer player) {
        return player.createCommandSourceStack().permissions() instanceof net.minecraft.server.permissions.LevelBasedPermissionSet levels
            && levels.level().isEqualOrHigherThan(net.minecraft.server.permissions.PermissionLevel.byId(2))
            && player.isCreative();
    }
}
