package com.silver.atlantis.protect;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.bounds.ActiveConstructBoundsResolver;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;

/**
 * In-memory protection registry.
 *
 * Checks are O(1) via per-dimension position indexes.
 */
public final class ProtectionManager {

    public static final ProtectionManager INSTANCE = new ProtectionManager();

    private final Map<String, ProtectionEntry> entriesById = new Object2ObjectOpenHashMap<>();
    private final Map<String, ObjectArrayList<ProtectionEntry>> entriesByDimension = new Object2ObjectOpenHashMap<>();
    private final Map<String, ProtectionIndex> indexByDimension = new Object2ObjectOpenHashMap<>();

    // Index maintenance is time-sliced via tick().
    private final ArrayDeque<ProtectionIndexJob> jobQueue = new ArrayDeque<>();

    private final Object2LongOpenHashMap<UUID> lastWarnNanosByPlayer = new Object2LongOpenHashMap<>();
    private static final long WARN_COOLDOWN_NANOS = 2_000_000_000L;
    private static final long BOUNDS_CACHE_TTL_NANOS = 1_000_000_000L;
    private ActiveConstructBounds cachedBounds;
    private long cachedBoundsAtNanos;

    private ProtectionManager() {
    }

    public synchronized void tick(long budgetNanos) {
        if (jobQueue.isEmpty()) {
            return;
        }

        if (budgetNanos <= 0L) {
            budgetNanos = 1L;
        }

        long start = System.nanoTime();
        while (!jobQueue.isEmpty() && (System.nanoTime() - start) < budgetNanos) {
            ProtectionIndexJob job = jobQueue.peek();
            long elapsed = System.nanoTime() - start;
            long remaining = Math.max(1L, budgetNanos - elapsed);
            job.step(remaining);

            if (job.isDone()) {
                jobQueue.poll();
            }
        }
    }

    public synchronized void flushPendingIndexJobs() {
        while (!jobQueue.isEmpty()) {
            ProtectionIndexJob job = jobQueue.peek();
            job.step(Long.MAX_VALUE);
            if (job.isDone()) {
                jobQueue.poll();
            }
        }
        AtlantisMod.LOGGER.info("[Protection] flushed all pending index jobs.");
    }

    public synchronized void register(ProtectionEntry entry) {
        if (entry == null || entry.id() == null || entry.dimensionId() == null) {
            return;
        }

        // Replace existing with same id (keeps index correct).
        ProtectionEntry existing = entriesById.remove(entry.id());
        if (existing != null) {
            removeFromDimension(existing);
            enqueueIndexJob(ProtectionIndexJob.Mode.REMOVE, existing);
        }

        entriesById.put(entry.id(), entry);
        addToDimension(entry);
        enqueueIndexJob(ProtectionIndexJob.Mode.ADD, entry);
    }

    public synchronized void unregister(String id) {
        if (id == null) {
            return;
        }

        ProtectionEntry existing = entriesById.remove(id);
        if (existing == null) {
            return;
        }

        removeFromDimension(existing);
        enqueueIndexJob(ProtectionIndexJob.Mode.REMOVE, existing);
    }

    public synchronized boolean isBreakProtected(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }
        String dim = dimensionId(world);
        long key = pos.asLong();

        // Only trust the index when there are no pending add/remove jobs.
        if (jobQueue.isEmpty()) {
            ProtectionIndex idx = indexByDimension.get(dim);
            if (idx != null && idx.isBreakProtected(key)) {
                return true;
            }
        }

        ObjectArrayList<ProtectionEntry> entries = entriesByDimension.get(dim);
        if (entries == null) {
            return isWithinLatestConstructBounds(world, pos, dim);
        }
        for (int i = 0; i < entries.size(); i++) {
            ProtectionEntry e = entries.get(i);
            if (e.placedPositions().contains(key)) {
                return true;
            }
        }
        return isWithinLatestConstructBounds(world, pos, dim);
    }

    public synchronized boolean isPlaceProtected(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        String dim = dimensionId(world);
        long key = pos.asLong();

        // Only trust the index when there are no pending add/remove jobs.
        if (jobQueue.isEmpty()) {
            ProtectionIndex idx = indexByDimension.get(dim);
            if (idx != null && idx.isPlaceProtected(key)) {
                return true;
            }
        }

        ObjectArrayList<ProtectionEntry> entries = entriesByDimension.get(dim);
        if (entries == null) {
            return isWithinLatestConstructBounds(world, pos, dim);
        }
        for (int i = 0; i < entries.size(); i++) {
            ProtectionEntry e = entries.get(i);
            if (e.interiorPositions().contains(key)) {
                return true;
            }
        }
        return isWithinLatestConstructBounds(world, pos, dim);
    }

    public synchronized boolean isInteriorProtected(ServerWorld world, BlockPos pos) {
        return isPlaceProtected(world, pos);
    }

    public synchronized boolean isAnyProtected(ServerWorld world, BlockPos pos) {
        if (world == null || pos == null) {
            return false;
        }

        String dim = dimensionId(world);
        long key = pos.asLong();

        // Only trust the index when there are no pending add/remove jobs.
        if (jobQueue.isEmpty()) {
            ProtectionIndex idx = indexByDimension.get(dim);
            if (idx != null && idx.isAnyProtected(key)) {
                return true;
            }
        }

        ObjectArrayList<ProtectionEntry> entries = entriesByDimension.get(dim);
        if (entries == null) {
            return isWithinLatestConstructBounds(world, pos, dim);
        }
        for (int i = 0; i < entries.size(); i++) {
            ProtectionEntry e = entries.get(i);
            if (e.placedPositions().contains(key) || e.interiorPositions().contains(key)) {
                return true;
            }
        }
        return isWithinLatestConstructBounds(world, pos, dim);
    }

    public boolean shouldBlockBreak(ServerPlayerEntity player, BlockPos pos) {
        if (player == null || pos == null) {
            return false;
        }

        if (!isAllowedBypass(player)) {
            // Survival/adventure/spectator ops and all non-ops are blocked.
            ServerWorld serverWorld = player.getEntityWorld();
            boolean blocked = isAnyProtected(serverWorld, pos);
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

    private void addToDimension(ProtectionEntry entry) {
        entriesByDimension
            .computeIfAbsent(entry.dimensionId(), ignored -> new ObjectArrayList<>())
            .add(entry);
    }

    private void removeFromDimension(ProtectionEntry entry) {
        ObjectArrayList<ProtectionEntry> list = entriesByDimension.get(entry.dimensionId());
        if (list == null || list.isEmpty()) {
            return;
        }

        for (int i = list.size() - 1; i >= 0; i--) {
            ProtectionEntry e = list.get(i);
            if (entry.id().equals(e.id())) {
                list.remove(i);
                return;
            }
        }
    }

    private void enqueueIndexJob(ProtectionIndexJob.Mode mode, ProtectionEntry entry) {
        ProtectionIndex index = indexByDimension.computeIfAbsent(entry.dimensionId(), ignored -> new ProtectionIndex());
        ProtectionIndexJob job = new ProtectionIndexJob(mode, entry, index);
        jobQueue.add(job);
    }

    private boolean isWithinLatestConstructBounds(ServerWorld world, BlockPos pos, String dimensionId) {
        long now = System.nanoTime();
        if ((now - cachedBoundsAtNanos) > BOUNDS_CACHE_TTL_NANOS) {
            cachedBounds = ActiveConstructBoundsResolver.tryResolveLatest();
            cachedBoundsAtNanos = now;
        }

        ActiveConstructBounds bounds = cachedBounds;
        if (bounds == null) {
            return false;
        }
        if (bounds.dimensionId() == null || !bounds.dimensionId().equals(dimensionId)) {
            return false;
        }
        return bounds.contains(pos);
    }

    /**
     * Only ops in creative can bypass protections.
     */
    private static boolean isAllowedBypass(ServerPlayerEntity player) {
        return player.hasPermissionLevel(2) && player.getAbilities().creativeMode;
    }
}
