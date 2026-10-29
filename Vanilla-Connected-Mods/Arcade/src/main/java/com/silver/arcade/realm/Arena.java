package com.silver.arcade.realm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A slot in the void world where one room is built: slots sit 512 blocks apart along +x at y=100, far enough that
 * one room never shows in another's view. Every block a room places is remembered and cleared when the slot is freed.
 * The list is also kept on disk (world/arcade-arenas/slot-N.bin) until the slot is cleared, so whatever a crash or a
 * stop left behind is cleared on the next start.
 */
public final class Arena {
    public static final int SPACING = 512;
    public static final int BASE_Y = 100;
    private static final BitSet USED = new BitSet();

    private final ServerLevel level;
    private final int slot;
    private final BlockPos origin;
    private final Deque<BlockPos> placed = new ArrayDeque<>();
    private final List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
    private boolean ledgerDirty;
    /** Decoration waiting to be placed over the next ticks (see {@link #setLater}). */
    private final Deque<Object[]> later = new ArrayDeque<>();
    /** Decoration handed over from the look generator thread; moved into {@link #later} on the server thread. */
    private final java.util.concurrent.ConcurrentLinkedQueue<Object[]> incoming = new java.util.concurrent.ConcurrentLinkedQueue<>();
    /** Looks still being generated off the server thread (see Deco.async). */
    private final java.util.concurrent.atomic.AtomicInteger looks = new java.util.concurrent.atomic.AtomicInteger();
    private volatile boolean released;
    private static final List<Arena> PENDING = new ArrayList<>();
    private static boolean tickerOn;
    /** Time per server tick spent placing queued decoration, all arenas together. */
    private static final long BUDGET_NANOS = 12_000_000L;
    private static java.nio.file.Path ledgers;
    /** Set while the server stops: arenas then keep their ledgers instead of clearing. */
    public static volatile boolean stopping;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("arcade");

    private Arena(ServerLevel level, int slot) {
        this.level = level;
        this.slot = slot;
        this.origin = new BlockPos(SPACING * (slot + 1), BASE_Y, 0);
    }

    public static synchronized Arena take(ServerLevel level) {
        return take(level, 0);
    }

    /** The first free slot from {@code from} on (previews use high slots to measure builds into fresh chunks). */
    static synchronized Arena take(ServerLevel level, int from) {
        int slot = USED.nextClearBit(from);
        USED.set(slot);
        // the next room's slot is warmed from the next tick on (never in the tick a room is built)
        warmLevel = level;
        warmNext = USED.nextClearBit(0);
        ensureTicker();
        return new Arena(level, slot);
    }

    public ServerLevel level() {
        return level;
    }

    /** The arena's corner; rooms build at origin + offsets. */
    public BlockPos origin() {
        return origin;
    }

    public BlockPos at(int dx, int dy, int dz) {
        return origin.offset(dx, dy, dz);
    }

    public void set(int dx, int dy, int dz, BlockState state) {
        BlockPos pos = at(dx, dy, dz);
        level.setBlock(pos, state, 2);
        placed.push(pos.immutable());
        ledgerDirty = true;
    }

    /**
     * Queues a decoration block: it is placed within the next ticks (a time budget per tick keeps big room looks from
     * stalling the server) and goes on the ledger like any other block. Never use it for anything the game needs.
     */
    public void setLater(int dx, int dy, int dz, BlockState state) {
        if (released) {
            return;
        }
        Object[] entry = {at(dx, dy, dz), state};
        if (level.getServer().isSameThread()) {
            later.add(entry);
        } else {
            incoming.add(entry); // from the look generator thread
        }
        synchronized (Arena.class) {
            if (!PENDING.contains(this)) {
                PENDING.add(this);
            }
            ensureTicker();
        }
    }

    private static void ensureTicker() {
        if (!tickerOn) {
            tickerOn = true;
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> drainAll());
        }
    }

    /** True while decoration is still being generated or placed. */
    public boolean decorating() {
        return !later.isEmpty() || !incoming.isEmpty() || looks.get() > 0;
    }

    /** Called by Deco.async round a look generated off the server thread. */
    public void lookStarted() {
        looks.incrementAndGet();
    }

    public void lookDone() {
        looks.decrementAndGet();
    }

    private long decoNanos;
    private long decoMax;

    private final java.util.Set<Long> requested = new java.util.HashSet<>();
    private final java.util.Map<Long, Long> loadedAt = new java.util.HashMap<>();

    /**
     * True when pos's chunk, and the neighbouring chunk(s) a block on its edge updates, are loaded and settled;
     * otherwise asks for them to load (in the background) and returns false. Placing a block on a chunk edge updates the
     * shape of its neighbour across the edge: if that chunk were not loaded yet, the server would load (or generate) it
     * on the spot, which cost 20-200 ms per block in fresh chunks.
     */
    private boolean ready(BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        if (!readyChunk(cx, cz)) {
            return false;
        }
        int lx = pos.getX() & 15, lz = pos.getZ() & 15;
        int nx = lx == 0 ? -1 : (lx == 15 ? 1 : 0);
        int nz = lz == 0 ? -1 : (lz == 15 ? 1 : 0);
        boolean ok = true;
        if (nx != 0) {
            ok &= readyChunk(cx + nx, cz);
        }
        if (nz != 0) {
            ok &= readyChunk(cx, cz + nz);
        }
        if (nx != 0 && nz != 0) {
            ok &= readyChunk(cx + nx, cz + nz);
        }
        return ok;
    }

    private boolean readyChunk(int cx, int cz) {
        long key = net.minecraft.world.level.ChunkPos.pack(cx, cz);
        if (level.getChunkSource().getChunkNow(cx, cz) != null) {
            // A chunk that was only just loaded is still being set up (light); give it a second first.
            Long seen = loadedAt.get(key);
            long now = level.getGameTime();
            if (seen == null) {
                if (!requested.contains(key)) {
                    return true; // it was loaded before we asked
                }
                loadedAt.put(key, now);
                return false;
            }
            return now - seen >= 20;
        }
        if (requested.add(key)) {
            level.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL, new net.minecraft.world.level.ChunkPos(cx, cz), 1);
        }
        return false;
    }

    /** Chunk radius kept loaded round the next free slot's origin (a room's own blocks stay well inside it). */
    private static final int WARM_RADIUS = 7;
    private static ServerLevel warmLevel;
    private static int warmSlot = -1;
    private static volatile int warmNext = -1;

    /**
     * Keeps the chunks of the slot the next room will take loaded (and generated), so its build never has to load or
     * generate chunks on the spot; building into fresh chunks cost 60-220 ms in one tick. Refreshed every 10 s.
     */
    static synchronized void warm(ServerLevel level, int slot) {
        warmLevel = level;
        warmSlot = slot;
        refreshWarm();
        ensureTicker();
    }

    private static void refreshWarm() {
        if (warmLevel == null || warmSlot < 0) {
            return;
        }
        int cx = (SPACING * (warmSlot + 1)) >> 4;
        warmLevel.getChunkSource().addTicketWithRadius(net.minecraft.server.level.TicketType.PORTAL,
            new net.minecraft.world.level.ChunkPos(cx, 0), WARM_RADIUS);
    }

    private static void drainAll() {
        if (warmNext >= 0) {
            warmSlot = warmNext;
            warmNext = -1;
            refreshWarm();
        } else if (warmLevel != null && warmLevel.getGameTime() % 200 == 0) {
            refreshWarm();
        }
        List<Arena> arenas;
        synchronized (Arena.class) {
            if (PENDING.isEmpty()) {
                return;
            }
            arenas = new ArrayList<>(PENDING);
        }
        long started = System.nanoTime();
        long until = started + BUDGET_NANOS;
        for (Arena arena : arenas) {
            if (arena.clearing) {
                int waiting = 0;
                while (!arena.placed.isEmpty() && System.nanoTime() < until && waiting < 256) {
                    BlockPos pos = arena.placed.pop();
                    if (!arena.ready(pos)) {
                        arena.placed.addLast(pos);
                        waiting++;
                        continue;
                    }
                    arena.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                }
                if (arena.placed.isEmpty()) {
                    arena.finishClearing();
                }
                continue;
            }
            for (Object[] in; (in = arena.incoming.poll()) != null;) {
                arena.later.add(in);
            }
            long t0 = System.nanoTime();
            int waiting = 0;
            while (!arena.later.isEmpty() && System.nanoTime() < until && waiting <= 64) {
                for (int i = 0; i < 16 && !arena.later.isEmpty(); i++) {
                    Object[] next = arena.later.poll();
                    BlockPos pos = (BlockPos) next[0];
                    if (!arena.ready(pos)) {
                        arena.later.add(next); // its chunk is loading; try again later
                        if (++waiting > 64) {
                            break;
                        }
                        continue;
                    }
                    if (!arena.level.getBlockState(pos).isAir()) {
                        continue; // never over a block the room placed
                    }
                    long b0 = System.nanoTime();
                    arena.level.setBlock(pos, (BlockState) next[1], 2);
                    long b1 = System.nanoTime() - b0;
                    if (b1 > 20_000_000L) {
                        LOGGER.info("slow block {} {} {} ms", pos, next[1], b1 / 1_000_000);
                    }
                    arena.placed.push(pos);
                    arena.ledgerDirty = true;
                }
            }
            long spent = System.nanoTime() - t0;
            arena.decoNanos += spent;
            arena.decoMax = Math.max(arena.decoMax, spent);
            if (arena.later.isEmpty() && arena.incoming.isEmpty() && arena.looks.get() == 0) {
                LOGGER.info("Decoration placed in slot {}: {} ms of tick time, at most {} ms in one tick", arena.slot,
                    arena.decoNanos / 1_000_000, arena.decoMax / 1_000_000);
                arena.decoNanos = 0;
                arena.decoMax = 0;
                arena.flushLedger();
                synchronized (Arena.class) {
                    PENDING.remove(arena);
                }
            }
        }
    }

    private java.nio.file.Path ledger() {
        return ledgers == null ? null : ledgers.resolve("slot-" + slot + ".bin");
    }

    /** Writes the list of placed blocks to disk if it changed (called every few seconds by the run). */
    public void flushLedger() {
        java.nio.file.Path file = ledger();
        if (!ledgerDirty || file == null) {
            return;
        }
        ledgerDirty = false;
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(placed.size() * Long.BYTES);
        placed.forEach(pos -> buffer.putLong(pos.asLong()));
        try {
            java.nio.file.Files.createDirectories(ledgers);
            java.nio.file.Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            java.nio.file.Files.write(temporary, buffer.array());
            java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not write the arena ledger {}", file, failure);
        }
    }

    /** At start: clears every block a room left behind last time (from the ledgers), then forgets the ledgers. */
    public static void clearLeftovers(ServerLevel level, java.nio.file.Path dir) {
        ledgers = dir;
        warm(level, 0);
        if (!java.nio.file.Files.isDirectory(dir)) {
            return;
        }
        try (var files = java.nio.file.Files.list(dir)) {
            for (java.nio.file.Path file : files.toList()) {
                if (!file.getFileName().toString().endsWith(".bin")) {
                    java.nio.file.Files.deleteIfExists(file);
                    continue;
                }
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(file));
                int cleared = 0;
                while (buffer.remaining() >= Long.BYTES) {
                    BlockPos pos = BlockPos.of(buffer.getLong());
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
                        cleared++;
                    }
                }
                java.nio.file.Files.delete(file);
                LOGGER.info("Cleared {} blocks a room left behind ({})", cleared, file.getFileName());
            }
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not clear the arenas' leftovers", failure);
        }
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Adds an entity the room made (a mob, a target); it is removed with the arena. */
    public <T extends net.minecraft.world.entity.Entity> T spawn(T entity, double dx, double dy, double dz) {
        entity.snapTo(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, 0F, 0F);
        entity.addTag("arcade_room");
        level.addFreshEntity(entity);
        spawned.add(entity);
        return entity;
    }

    /** Removes one block a room placed (it stays remembered; clearing it again later is harmless). */
    public void clearBlock(int dx, int dy, int dz) {
        level.setBlock(at(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
    }

    /** True when pos lies in this arena's slot. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= origin.getX() - SPACING / 2 && pos.getX() < origin.getX() + SPACING / 2;
    }

    public void put(ServerPlayer player, double dx, double dy, double dz, float yaw) {
        player.teleportTo(level, origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, Set.of(), yaw, 0F, false);
        player.setDeltaMovement(0, 0, 0);
        player.resetFallDistance();
    }

    /** Clears everything the room placed and frees the slot. */
    public void release() {
        released = true;
        later.clear();
        incoming.clear();
        synchronized (Arena.class) {
            PENDING.remove(this);
        }
        spawned.forEach(net.minecraft.world.entity.Entity::discard);
        spawned.clear();
        // Arrows and drops left lying around in the slot go too.
        level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(origin.getX() - SPACING / 2.0, BASE_Y - 64,
                -SPACING / 2.0, origin.getX() + SPACING / 2.0, BASE_Y + 64, SPACING / 2.0),
            e -> e instanceof net.minecraft.world.entity.projectile.Projectile || e instanceof net.minecraft.world.entity.item.ItemEntity
                || e.entityTags().contains("arcade_room"))
            .forEach(net.minecraft.world.entity.Entity::discard);
        if (stopping) {
            // The ledger stays on disk; the next start clears this arena.
            ledgerDirty = true;
            flushLedger();
            return;
        }
        if (placed.size() <= SMALL_CLEAR) {
            clearAll();
            return;
        }
        // Big rooms (decorated ones) are taken down over the next ticks, within the same time budget as decoration;
        // the slot stays taken (and the ledger on disk) until the last block is gone.
        clearing = true;
        synchronized (Arena.class) {
            if (!PENDING.contains(this)) {
                PENDING.add(this);
            }
            ensureTicker();
        }
    }

    private static final int SMALL_CLEAR = 3000;
    private boolean clearing;

    private void clearAll() {
        while (!placed.isEmpty()) {
            level.setBlock(placed.pop(), Blocks.AIR.defaultBlockState(), 2 | 16);
        }
        finishClearing();
    }

    private void finishClearing() {
        clearing = false;
        try {
            if (ledger() != null) {
                java.nio.file.Files.deleteIfExists(ledger());
            }
        } catch (java.io.IOException failure) {
            LOGGER.warn("Could not remove the arena ledger {}", ledger(), failure);
        }
        synchronized (Arena.class) {
            PENDING.remove(this);
            USED.clear(slot);
        }
    }
}
