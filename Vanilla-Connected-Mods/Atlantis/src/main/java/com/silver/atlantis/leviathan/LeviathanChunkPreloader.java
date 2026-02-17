package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class LeviathanChunkPreloader {
    private static final ChunkTicketType TICKET_TYPE = ChunkTicketType.FORCED;

    private static final class LeviathanTickets {
        final Deque<ChunkPos> pending = new ArrayDeque<>();
        final LongSet pendingKeys = new LongOpenHashSet();
        final LongSet ticketKeys = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<CompletableFuture<?>> activeLoads = new Long2ObjectOpenHashMap<>();
        long lastTouchedTick;
    }

    private final Map<UUID, LeviathanTickets> ticketsByLeviathan = new HashMap<>();
    private final int ticketLevel;

    LeviathanChunkPreloader(int ticketLevel) {
        this.ticketLevel = ticketLevel;
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] chunk preloader initialized ticketLevel={}", ticketLevel);
    }

    int request(ServerWorld world, UUID id, Iterable<ChunkPos> desiredChunks, long nowTick, int budget) {
        if (budget <= 0) {
            touch(id, nowTick);
            pollActive(world, id);
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk request skipped budget<=0 id={}", shortId(id));
            return 0;
        }

        LeviathanTickets tickets = ticketsByLeviathan.computeIfAbsent(id, ignored -> new LeviathanTickets());
        tickets.lastTouchedTick = nowTick;

        for (ChunkPos pos : desiredChunks) {
            long key = ChunkPos.toLong(pos.x, pos.z);
            if (tickets.ticketKeys.contains(key) || tickets.pendingKeys.contains(key)) {
                continue;
            }
            tickets.pending.addLast(pos);
            tickets.pendingKeys.add(key);
        }
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk request queued id={} pending={} activeTickets={} budget={}",
            shortId(id),
            tickets.pending.size(),
            tickets.ticketKeys.size(),
            budget);

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
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk ticket added id={} chunk=({}, {}) level={}",
                    shortId(id),
                    pos.x,
                    pos.z,
                    ticketLevel);
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("[Atlantis][leviathan] chunk ticket add failed id={} chunk=({}, {}) error={}",
                    shortId(id),
                    pos.x,
                    pos.z,
                    e.getMessage());
            }
        }

        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk request processed id={} started={} remainingPending={} activeLoads={}",
            shortId(id),
            started,
            tickets.pending.size(),
            tickets.activeLoads.size());
        return started;
    }

    boolean isChunkLoaded(ServerWorld world, ChunkPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.x, pos.z);
    }

    void release(ServerWorld world, UUID id) {
        LeviathanTickets tickets = ticketsByLeviathan.remove(id);
        if (tickets == null) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk release no-op id={}", shortId(id));
            return;
        }

        try {
            for (long key : tickets.ticketKeys) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                world.getChunkManager().removeTicket(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
            }
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk release removed tickets id={} count={}", shortId(id), tickets.ticketKeys.size());
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("[Atlantis][leviathan] chunk release failed id={} error={}", shortId(id), e.getMessage());
        }

        tickets.pending.clear();
        tickets.pendingKeys.clear();
        tickets.ticketKeys.clear();
        tickets.activeLoads.clear();
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk release state cleared id={}", shortId(id));
    }

    void releaseUnused(ServerWorld world, long nowTick, int releaseAfterTicks) {
        Iterator<Map.Entry<UUID, LeviathanTickets>> it = ticketsByLeviathan.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, LeviathanTickets> entry = it.next();
            if (nowTick - entry.getValue().lastTouchedTick < releaseAfterTicks) {
                continue;
            }

            it.remove();
            try {
                for (long key : entry.getValue().ticketKeys) {
                    int cx = ChunkPos.getPackedX(key);
                    int cz = ChunkPos.getPackedZ(key);
                    world.getChunkManager().removeTicket(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
                }
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk releaseUnused removed stale id={} count={}",
                    shortId(entry.getKey()),
                    entry.getValue().ticketKeys.size());
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("[Atlantis][leviathan] chunk releaseUnused failed id={} error={}",
                    shortId(entry.getKey()),
                    e.getMessage());
            }
        }
    }

    private void pollActive(ServerWorld world, UUID id) {
        LeviathanTickets tickets = ticketsByLeviathan.get(id);
        if (tickets != null) {
            pollActive(world, tickets);
        }
    }

    private void pollActive(ServerWorld world, LeviathanTickets tickets) {
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
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] chunk load completed chunk=({}, {})", cx, cz);
        }
    }

    private void touch(UUID id, long nowTick) {
        LeviathanTickets tickets = ticketsByLeviathan.get(id);
        if (tickets != null) {
            tickets.lastTouchedTick = nowTick;
        }
    }

    private static String shortId(UUID id) {
        String value = id.toString();
        return value.length() <= 8 ? value : value.substring(0, 8);
    }
}
