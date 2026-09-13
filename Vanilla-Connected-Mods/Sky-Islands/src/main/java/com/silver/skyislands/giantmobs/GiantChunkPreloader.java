package com.silver.skyislands.giantmobs;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

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
    private static final TicketType TICKET_TYPE = TicketType.FORCED;

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

    int request(ServerLevel world, UUID id, Iterable<ChunkPos> desiredChunks, long nowTick, int budget) {
        GiantTickets tickets = ticketsByGiant.computeIfAbsent(id, ignored -> new GiantTickets());
        tickets.lastTouchedTick = nowTick;

        for (ChunkPos pos : desiredChunks) {
            long key = ChunkPos.pack(pos.x(), pos.z());
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

            long key = ChunkPos.pack(pos.x(), pos.z());
            tickets.pendingKeys.remove(key);
            if (tickets.ticketKeys.contains(key)) {
                continue;
            }

            try {
                CompletableFuture<?> future = world.getChunkSource().addTicketAndLoadWithRadius(TICKET_TYPE, pos, ticketLevel);
                tickets.ticketKeys.add(key);
                tickets.activeLoads.put(key, future);
                started++;
            } catch (Exception ignored) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][giants][preload] ticket add failed id={} chunk=({}, {})", id, pos.x(), pos.z());
                }
            }
        }

        return started;
    }

    boolean isChunkLoaded(ServerLevel world, ChunkPos pos) {
        return world.getChunkSource().hasChunk(pos.x(), pos.z());
    }

    void release(ServerLevel world, UUID id) {
        GiantTickets tickets = ticketsByGiant.remove(id);
        if (tickets == null) {
            return;
        }

        try {
            for (long key : tickets.ticketKeys) {
                int cx = ChunkPos.getX(key);
                int cz = ChunkPos.getZ(key);
                world.getChunkSource().removeTicketWithRadius(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
            }
        } catch (Exception ignored) {
        }
    }

    void releaseUnused(ServerLevel world, long nowTick, int releaseAfterTicks) {
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
                    int cx = ChunkPos.getX(key);
                    int cz = ChunkPos.getZ(key);
                    world.getChunkSource().removeTicketWithRadius(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
                }
            } catch (Exception ignored) {
            }

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][giants][preload] releaseUnused id={}", id);
            }
        }
    }

    private void pollActive(ServerLevel world, GiantTickets tickets) {
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

            int cx = ChunkPos.getX(key);
            int cz = ChunkPos.getZ(key);
            if (!world.getChunkSource().hasChunk(cx, cz)) {
                continue;
            }

            it.remove();

            LevelChunk chunk = world.getChunkSource().getChunkNow(cx, cz);
            if (chunk == null) {
                continue;
            }

            LevelLightEngine lightingProvider = world.getChunkSource().getLightEngine();
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(chunk, lightingProvider, null, null);
            ClientboundLightUpdatePacket lightPacket = new ClientboundLightUpdatePacket(chunk.getPos(), lightingProvider, null, null);
            for (ServerPlayer player : world.players()) {
                if (Math.max(Math.abs(player.chunkPosition().x() - cx), Math.abs(player.chunkPosition().z() - cz)) <= 127) {
                    player.connection.send(packet);
                    player.connection.send(lightPacket);
                }
            }
        }
    }
}