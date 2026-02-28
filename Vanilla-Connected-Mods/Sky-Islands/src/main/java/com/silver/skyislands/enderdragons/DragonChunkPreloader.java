package com.silver.skyislands.enderdragons;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lightweight chunk ticket manager for keeping a small moving window of chunks loaded.
 *
 * <p>We use {@link ChunkTicketType#FORCED} with a low level so the chunks are actually loaded/ticking.
 * Tickets are created gradually (budgeted per tick) to avoid server spikes.
 */
final class DragonChunkPreloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(DragonChunkPreloader.class);

    private static final ChunkTicketType TICKET_TYPE = ChunkTicketType.FORCED;

    private static final long RELEASE_NO_TICKETS_LOG_INTERVAL_MS = 60_000L;
    private static final long REQUEST_LOG_INTERVAL_TICKS = 20L * 60L;
    private static final long TICKET_ADD_LOG_INTERVAL_TICKS = 20L * 60L;

    private static final class DragonTickets {
        final Deque<ChunkPos> pending = new ArrayDeque<>();
        final LongSet pendingKeys = new LongOpenHashSet();
        final LongSet ticketKeys = new LongOpenHashSet();
        final Long2ObjectOpenHashMap<CompletableFuture<?>> activeLoads = new Long2ObjectOpenHashMap<>();
        long lastTouchedTick;
    }

    private final Map<UUID, DragonTickets> ticketsByDragon = new HashMap<>();
    private final Map<UUID, Long> nextReleaseNoTicketsLogAtMs = new HashMap<>();
    private final Map<UUID, Long> nextRequestLogTickByDragon = new HashMap<>();
    private final Map<UUID, Long> nextTicketAddLogTickByDragon = new HashMap<>();
    private final Map<UUID, Integer> suppressedTicketAddLogsByDragon = new HashMap<>();

    private final int ticketLevel;

    DragonChunkPreloader(int ticketLevel) {
        this.ticketLevel = ticketLevel;
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][preload] init ticketLevel={}", ticketLevel);
        }
    }

    int request(ServerWorld world, UUID id, Iterable<ChunkPos> desiredChunks, long nowTick, int budget) {
        if (budget <= 0) {
            touch(id, nowTick);
            pollActive(world, id);
            if (LOGGER.isDebugEnabled()) {
                long nextTick = nextRequestLogTickByDragon.getOrDefault(id, 0L);
                if (nowTick >= nextTick) {
                    nextRequestLogTickByDragon.put(id, nowTick + REQUEST_LOG_INTERVAL_TICKS);
                    LOGGER.debug("[Sky-Islands][dragons][preload] request id={} budget=0 (touch/poll only)", id);
                }
            }
            return 0;
        }

        DragonTickets tickets = ticketsByDragon.computeIfAbsent(id, ignored -> new DragonTickets());
        tickets.lastTouchedTick = nowTick;

        for (ChunkPos pos : desiredChunks) {
            long key = ChunkPos.toLong(pos.x, pos.z);
            if (tickets.ticketKeys.contains(key) || tickets.pendingKeys.contains(key)) {
                continue;
            }
            tickets.pending.addLast(pos);
            tickets.pendingKeys.add(key);
        }

        if (LOGGER.isDebugEnabled()) {
            long nextTick = nextRequestLogTickByDragon.getOrDefault(id, 0L);
            if (nowTick >= nextTick) {
                nextRequestLogTickByDragon.put(id, nowTick + REQUEST_LOG_INTERVAL_TICKS);
                LOGGER.debug("[Sky-Islands][dragons][preload] request id={} pending={} active={} budget={}",
                        id, tickets.pending.size(), tickets.activeLoads.size(), budget);
            }
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
                if (LOGGER.isDebugEnabled()) {
                    long nextTick = nextTicketAddLogTickByDragon.getOrDefault(id, 0L);
                    if (nowTick >= nextTick) {
                        nextTicketAddLogTickByDragon.put(id, nowTick + TICKET_ADD_LOG_INTERVAL_TICKS);
                        int suppressed = suppressedTicketAddLogsByDragon.getOrDefault(id, 0);
                        suppressedTicketAddLogsByDragon.remove(id);

                        if (suppressed > 0) {
                            LOGGER.debug("[Sky-Islands][dragons][preload] ticket add id={} chunk=({}, {}) level={} (+{} suppressed)",
                                    id, pos.x, pos.z, ticketLevel, suppressed);
                        } else {
                            LOGGER.debug("[Sky-Islands][dragons][preload] ticket add id={} chunk=({}, {}) level={}",
                                    id, pos.x, pos.z, ticketLevel);
                        }
                    } else {
                        suppressedTicketAddLogsByDragon.merge(id, 1, Integer::sum);
                    }
                }
            } catch (Exception ignored) {
                // If tickets cannot be created (e.g. world shutting down), just skip.
                if (LOGGER.isDebugEnabled()) {
                    long nextTick = nextTicketAddLogTickByDragon.getOrDefault(id, 0L);
                    if (nowTick >= nextTick) {
                        nextTicketAddLogTickByDragon.put(id, nowTick + TICKET_ADD_LOG_INTERVAL_TICKS);
                        int suppressed = suppressedTicketAddLogsByDragon.getOrDefault(id, 0);
                        suppressedTicketAddLogsByDragon.remove(id);

                        if (suppressed > 0) {
                            LOGGER.debug("[Sky-Islands][dragons][preload] ticket add failed id={} chunk=({}, {}) (+{} suppressed)", id, pos.x, pos.z, suppressed);
                        } else {
                            LOGGER.debug("[Sky-Islands][dragons][preload] ticket add failed id={} chunk=({}, {})", id, pos.x, pos.z);
                        }
                    } else {
                        suppressedTicketAddLogsByDragon.merge(id, 1, Integer::sum);
                    }
                }
            }
        }

        return started;
    }

    boolean isChunkLoaded(ServerWorld world, ChunkPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.x, pos.z);
    }

    void release(ServerWorld world, UUID id) {
        DragonTickets tickets = ticketsByDragon.remove(id);
        nextTicketAddLogTickByDragon.remove(id);
        suppressedTicketAddLogsByDragon.remove(id);
        if (tickets == null) {
            if (LOGGER.isDebugEnabled()) {
                long nowMs = System.currentTimeMillis();
                long nextMs = nextReleaseNoTicketsLogAtMs.getOrDefault(id, 0L);
                if (nowMs >= nextMs) {
                    nextReleaseNoTicketsLogAtMs.put(id, nowMs + RELEASE_NO_TICKETS_LOG_INTERVAL_MS);
                    LOGGER.debug("[Sky-Islands][dragons][preload] release id={} (no tickets)", id);
                }
            }
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

        tickets.pending.clear();
        tickets.pendingKeys.clear();
        tickets.ticketKeys.clear();
        tickets.activeLoads.clear();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][preload] release id={} done", id);
        }
    }

    void releaseUnused(ServerWorld world, long nowTick, int releaseAfterTicks) {
        if (ticketsByDragon.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, DragonTickets>> it = ticketsByDragon.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DragonTickets> entry = it.next();
            if (nowTick - entry.getValue().lastTouchedTick < releaseAfterTicks) {
                continue;
            }

            UUID id = entry.getKey();
            it.remove();
            nextTicketAddLogTickByDragon.remove(id);
            suppressedTicketAddLogsByDragon.remove(id);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][preload] releaseUnused id={} ageTicks={} threshold={}",
                        id, (nowTick - entry.getValue().lastTouchedTick), releaseAfterTicks);
            }
            try {
                for (long key : entry.getValue().ticketKeys) {
                    int cx = ChunkPos.getPackedX(key);
                    int cz = ChunkPos.getPackedZ(key);
                    world.getChunkManager().removeTicket(TICKET_TYPE, new ChunkPos(cx, cz), ticketLevel);
                }
            } catch (Exception ignored) {
            }
        }
    }

    private void pollActive(ServerWorld world, UUID id) {
        DragonTickets tickets = ticketsByDragon.get(id);
        if (tickets != null) {
            pollActive(world, tickets);
        }
    }

    private void pollActive(ServerWorld world, DragonTickets tickets) {
        if (tickets.activeLoads.isEmpty()) {
            return;
        }

        var it = tickets.activeLoads.long2ObjectEntrySet().fastIterator();
        while (it.hasNext()) {
            var entry = it.next();
            long key = entry.getLongKey();
            CompletableFuture<?> fut = entry.getValue();

            if (!fut.isDone()) {
                continue;
            }

            int cx = ChunkPos.getPackedX(key);
            int cz = ChunkPos.getPackedZ(key);
            if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
                continue;
            }

            it.remove();
            
            // Broadcast chunk immediately to all players nearby to bypass View-Extend's slow radial scanner.
            // This prevents the visual bug where dragons appear to fly into empty void before View-Extend catches up.
            net.minecraft.world.chunk.WorldChunk chunk = world.getChunkManager().getWorldChunk(cx, cz);
            if (chunk != null) {
                net.minecraft.world.chunk.light.LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
                net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket packet = new net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket(chunk, lightingProvider, null, null);
                net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket lightPacket = new net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket(chunk.getPos(), lightingProvider, null, null);
                for (net.minecraft.server.network.ServerPlayerEntity player : world.getPlayers()) {
                    if (Math.max(Math.abs(player.getChunkPos().x - cx), Math.abs(player.getChunkPos().z - cz)) <= 127) {
                        player.networkHandler.sendPacket(packet);
                        player.networkHandler.sendPacket(lightPacket);
                    }
                }
            }
        }
    }

    private void touch(UUID id, long nowTick) {
        DragonTickets tickets = ticketsByDragon.get(id);
        if (tickets != null) {
            tickets.lastTouchedTick = nowTick;
        }
    }
}
