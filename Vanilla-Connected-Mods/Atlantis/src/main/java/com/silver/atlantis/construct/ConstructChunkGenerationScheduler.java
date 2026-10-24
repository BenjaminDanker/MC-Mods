package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * Generates missing target chunks before a build or undo. Each chunk is requested with a load ticket and generated
 * by the server between ticks; this class only polls for completion, so a slow chunk never stalls a tick. Finished
 * chunks lose their ticket at once so they can unload again for the region edit.
 */
final class ConstructChunkGenerationScheduler {

    private static final TicketType TICKET = TicketType.FORCED;

    private final Deque<ChunkPos> pending = new ArrayDeque<>();
    private final Set<Long> pendingKeys = new HashSet<>();
    private final Map<Long, CompletableFuture<?>> active = new HashMap<>();
    private final Set<Long> observedLoadedChunkKeys = new HashSet<>();
    private final Set<Long> recentlyGeneratedChunkKeys = new HashSet<>();

    ConstructChunkGenerationScheduler(int ticketLevel) {
    }

    void reset(List<ChunkPos> chunks) {
        pending.clear();
        pendingKeys.clear();
        observedLoadedChunkKeys.clear();
        recentlyGeneratedChunkKeys.clear();

        for (ChunkPos pos : chunks) {
            long key = ChunkPos.pack(pos.x(), pos.z());
            if (pendingKeys.contains(key)) {
                continue;
            }
            pending.addLast(pos);
            pendingKeys.add(key);
        }

        AtlantisMod.LOGGER.info("[construct-chunk-prep] queued {} chunk(s).", pending.size());
    }

    /** Polls finished chunks and starts new ones, keeping at most twice {@code budgetPerTick} in flight. */
    boolean tickGenerate(ServerLevel world, int budgetPerTick) {
        Iterator<Map.Entry<Long, CompletableFuture<?>>> it = active.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, CompletableFuture<?>> entry = it.next();
            if (!entry.getValue().isDone()) {
                continue;
            }
            it.remove();
            long key = entry.getKey();
            ChunkPos pos = new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key));
            world.getChunkSource().removeTicketWithRadius(TICKET, pos, 0);
            if (entry.getValue().isCompletedExceptionally()) {
                AtlantisMod.LOGGER.warn("[construct-chunk-prep] chunk ({}, {}) failed to generate", pos.x(), pos.z());
                continue;
            }
            if (world.getChunkSource().hasChunk(pos.x(), pos.z())) {
                recentlyGeneratedChunkKeys.add(key);
                observeLoadedNeighborhood(world, pos);
            }
        }

        int maxInFlight = Math.max(1, budgetPerTick) * 2;
        while (active.size() < maxInFlight && !pending.isEmpty()) {
            ChunkPos pos = pending.pollFirst();
            long key = ChunkPos.pack(pos.x(), pos.z());
            pendingKeys.remove(key);
            try {
                active.put(key, world.getChunkSource().addTicketAndLoadWithRadius(TICKET, pos, 0));
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("[construct-chunk-prep] could not request chunk ({}, {}): {}", pos.x(), pos.z(), e.toString());
            }
        }

        return pending.isEmpty() && active.isEmpty();
    }

    /** Drops the queue and the load tickets of chunks still being generated. */
    void releaseTickets(ServerLevel world) {
        pending.clear();
        pendingKeys.clear();
        for (long key : active.keySet()) {
            world.getChunkSource().removeTicketWithRadius(TICKET, new ChunkPos(ChunkPos.getX(key), ChunkPos.getZ(key)), 0);
        }
        int released = active.size();
        active.clear();
        AtlantisMod.LOGGER.info("[construct-chunk-prep] released prep scheduler (in-flight tickets released={}, observedLoadedChunks={}).",
            released, observedLoadedChunkKeys.size());
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
        return active.size();
    }

    private void observeLoadedNeighborhood(ServerLevel world, ChunkPos center) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int chunkX = center.x() + dx;
                int chunkZ = center.z() + dz;
                if (world.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    observedLoadedChunkKeys.add(ChunkPos.pack(chunkX, chunkZ));
                }
            }
        }
    }
}
