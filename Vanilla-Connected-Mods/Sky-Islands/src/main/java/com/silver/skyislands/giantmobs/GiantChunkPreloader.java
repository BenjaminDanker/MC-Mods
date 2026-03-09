package com.silver.skyislands.giantmobs;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class GiantChunkPreloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiantChunkPreloader.class);
    private static final ChunkTicketType TICKET_TYPE = ChunkTicketType.FORCED;

    private static final class GiantTickets {
        final Deque<ChunkPos> pending = new ArrayDeque<>();
        final LongSet pendingKeys = new LongOpenHashSet();
        final LongSet ticketKeys = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<CompletableFuture<?>> activeLoads = new Long2ObjectOpenHashMap<>();
        long lastTouchedTick;
    }

    private final Map<UUID, GiantTickets> ticketsByGiant = new HashMap<>();
    private final int ticketLevel;

    GiantChunkPreloader(int ticketLevel) {
        this.ticketLevel = ticketLevel;
    }

    int request(ServerWorld world, UUID id, Iterable<ChunkPos> desiredChunks, long nowTick, int budget) {
        GiantTickets tickets = ticketsByGiant.computeIfAbsent(id, ignored -> new GiantTickets());
        tickets.lastTouchedTick = nowTick;

        for (ChunkPos pos : desiredChunks) {
            long key = ChunkPos.toLong(pos.x, pos.z);
            if (tickets.ticketKeys.contains(key) || tickets.pendingKeys.contains(key)) {
                continue;
            }
            tickets.pending.addLast(pos);
            tickets.pendingKeys.add(key);
        }

        pollActive(world, tickets);

        int started = 0;
        while (started < budget && !tickets.pending.isEmpty()) {
            ChunkPos pos = tickets.pending.pollFirst();
            if (pos == null) {
                break;
            }

            long key = ChunkPos.toLong(pos.x, pos.z);
            tickets.pendingKeys.remove(key);
            if (tickets.ticketKeys.contains(key)) {
                continue;
            }

            try {
                CompletableFuture<?> future = world.getChunkManager().addChunkLoadingTicket(TICKET_TYPE, pos, ticketLevel);
                tickets.ticketKeys.add(key);
                tickets.activeLoads.put(key, future);
                started++;
            } catch (Exception ignored) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][giants][preload] ticket add failed id={} chunk=({}, {})", id, pos.x, pos.z);
                }
            }
        }

        return started;
    }

    boolean isChunkLoaded(ServerWorld world, ChunkPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.x, pos.z);
    }

    void release(ServerWorld world, UUID id) {
        GiantTickets tickets = ticketsByGiant.remove(id);
        if (tickets == null) {
            return;
        }

        try {
            for (long key : tickets.ticketKeys) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                world.getChunkManager().removeTicket(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
            }
        } catch (Exception ignored) {
        }
    }

    void releaseUnused(ServerWorld world, long nowTick, int releaseAfterTicks) {
        if (ticketsByGiant.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, GiantTickets>> it = ticketsByGiant.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, GiantTickets> entry = it.next();
            if (nowTick - entry.getValue().lastTouchedTick < releaseAfterTicks) {
                continue;
            }

            UUID id = entry.getKey();
            it.remove();
            try {
                for (long key : entry.getValue().ticketKeys) {
                    int cx = ChunkPos.getPackedX(key);
                    int cz = ChunkPos.getPackedZ(key);
                    world.getChunkManager().removeTicket(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
                }
            } catch (Exception ignored) {
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][giants][preload] releaseUnused id={}", id);
            }
        }
    }

    private void pollActive(ServerWorld world, GiantTickets tickets) {
        if (tickets.activeLoads.isEmpty()) {
            return;
        }

        var it = tickets.activeLoads.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            long key = entry.getLongKey();
            CompletableFuture<?> future = entry.getValue();
            if (!future.isDone()) {
                continue;
            }

            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
                continue;
            }

            it.remove();

            WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
            if (chunk == null) {
                continue;
            }

            LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
            ChunkDataS2CPacket packet = new ChunkDataS2CPacket(chunk, lightingProvider, null, null);
            LightUpdateS2CPacket lightPacket = new LightUpdateS2CPacket(chunk.getPos(), lightingProvider, null, null);
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (Math.max(Math.abs(player.getChunkPos().x - cx), Math.abs(player.getChunkPos().z - cz)) <= 127) {
                    player.networkHandler.sendPacket(packet);
                    player.networkHandler.sendPacket(lightPacket);
                }
            }
        }
    }
}