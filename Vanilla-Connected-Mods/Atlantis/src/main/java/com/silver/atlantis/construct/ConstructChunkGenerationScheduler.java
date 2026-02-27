package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class ConstructChunkGenerationScheduler {

    private static final long LIGHT_FORCE_NANOS = 12_000_000L;
    private static final long HEAVY_FORCE_NANOS = 35_000_000L;
    private static final int MAX_ADAPTIVE_COOLDOWN_TICKS = 8;

    private final Deque<ChunkPos> pending = new ArrayDeque<>();
    private final Set<Long> pendingKeys = new HashSet<>();
    private final Set<Long> observedLoadedChunkKeys = new HashSet<>();
    private final Set<Long> recentlyGeneratedChunkKeys = new HashSet<>();
    private int cooldownTicksRemaining;
    private int cooldownTicksTarget;
    private long forceLatencyEwmaNanos;

    ConstructChunkGenerationScheduler(int ticketLevel) {
    }

    void reset(List<ChunkPos> chunks) {
        pending.clear();
        pendingKeys.clear();
        observedLoadedChunkKeys.clear();
        recentlyGeneratedChunkKeys.clear();
        cooldownTicksRemaining = 0;
        cooldownTicksTarget = 0;
        forceLatencyEwmaNanos = 0L;

        for (ChunkPos pos : chunks) {
            long key = ChunkPos.toLong(pos.x, pos.z);
            if (pendingKeys.contains(key)) {
                continue;
            }
            pending.addLast(pos);
            pendingKeys.add(key);
        }

        AtlantisMod.LOGGER.info("[construct-chunk-prep] queued {} chunk(s).", pending.size());
    }

    boolean tickGenerate(ServerWorld world, int budgetPerTick) {
        if (pending.isEmpty()) {
            return true;
        }

        if (cooldownTicksRemaining > 0) {
            cooldownTicksRemaining--;
            return false;
        }

        int budget = Math.max(1, budgetPerTick);
        int started = 0;
        while (started < budget && !pending.isEmpty()) {
            ChunkPos pos = pending.pollFirst();
            if (pos == null) {
                break;
            }

            long key = ChunkPos.toLong(pos.x, pos.z);
            pendingKeys.remove(key);

            long startedAt = System.nanoTime();
            forceChunkGeneration(world, pos);
            long elapsedNanos = System.nanoTime() - startedAt;

            updateAdaptiveCooldown(elapsedNanos);

            boolean loadedNow = world.getChunkManager().isChunkLoaded(pos.x, pos.z);
            if (loadedNow) {
                recentlyGeneratedChunkKeys.add(key);
                observeLoadedNeighborhood(world, pos);
            }
            AtlantisMod.LOGGER.debug("[construct-chunk-prep] chunk prep chunk=({}, {}), pending={}, loadedNow={}", pos.x, pos.z, pending.size(), loadedNow);
            started++;

            if (elapsedNanos >= HEAVY_FORCE_NANOS) {
                break;
            }
        }

        if (!pending.isEmpty() && cooldownTicksTarget > 0) {
            cooldownTicksRemaining = cooldownTicksTarget;
        }

        return pending.isEmpty();
    }

    void releaseTickets(ServerWorld world) {
        pending.clear();
        pendingKeys.clear();
        AtlantisMod.LOGGER.info("[construct-chunk-prep] released prep scheduler state (observedLoadedChunks={}).", observedLoadedChunkKeys.size());
    }

    Set<Long> consumeObservedLoadedChunkKeys() {
        Set<Long> copy = Set.copyOf(observedLoadedChunkKeys);
        observedLoadedChunkKeys.clear();
        return copy;
    }

    Set<Long> consumeRecentlyGeneratedChunkKeys() {
        Set<Long> copy = Set.copyOf(recentlyGeneratedChunkKeys);
        recentlyGeneratedChunkKeys.clear();
        return copy;
    }

    int pendingCount() {
        return pending.size();
    }

    int activeCount() {
        return 0;
    }

    private void updateAdaptiveCooldown(long elapsedNanos) {
        if (elapsedNanos <= 0L) {
            return;
        }

        if (forceLatencyEwmaNanos <= 0L) {
            forceLatencyEwmaNanos = elapsedNanos;
        } else {
            forceLatencyEwmaNanos = (forceLatencyEwmaNanos * 7L + elapsedNanos) / 8L;
        }

        if (forceLatencyEwmaNanos >= HEAVY_FORCE_NANOS) {
            cooldownTicksTarget = Math.min(MAX_ADAPTIVE_COOLDOWN_TICKS, cooldownTicksTarget + 2);
            return;
        }

        if (forceLatencyEwmaNanos >= LIGHT_FORCE_NANOS) {
            cooldownTicksTarget = Math.min(MAX_ADAPTIVE_COOLDOWN_TICKS, cooldownTicksTarget + 1);
            return;
        }

        cooldownTicksTarget = Math.max(0, cooldownTicksTarget - 1);
    }

    private void forceChunkGeneration(ServerWorld world, ChunkPos pos) {
        try {
            world.getChunk(pos.x, pos.z);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("[construct-chunk-prep] failed forcing full chunk generation for ({}, {}): {}", pos.x, pos.z, e.toString());
        }
    }

    private void observeLoadedNeighborhood(ServerWorld world, ChunkPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = center.x + dx;
                int chunkZ = center.z + dz;
                if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    observedLoadedChunkKeys.add(ChunkPos.toLong(chunkX, chunkZ));
                }
            }
        }
    }
}
