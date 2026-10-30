package com.silver.arcade.realm;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A slot in the void world where one room is built: slots sit 512 blocks apart along +x at y=100, far enough that
 * one room never shows in another's view.
 *
 * <p>A room is built off the live world. From {@link #take} until the room is committed the arena only records what the
 * room places (set, fill, setLater, clearBlock) in memory; spawns and players moving in wait too. {@link #commit} hands the
 * recorded blocks to a worker thread, which settles block shapes and writes them into the saved data of the (unloaded)
 * chunks. Back on the server thread tickets are added; once every touched chunk is loaded, lit and entity-ticking, the
 * waiting spawns and moves run and the arena is ready ({@link #decorating} turns false). Releasing a ready arena removes
 * the tickets, waits for the chunks to unload and clears the room's blocks from their saved data the same way.
 *
 * <p>Every position placed is kept on a ledger, also on disk (world/arcade-arenas/slot-N.bin) until the slot is cleared,
 * so whatever a crash or a stop left behind is cleared on the next start. Blocks a room changes while it is played are
 * ordinary live {@code setBlock}s.
 */
public final class Arena {
    public static final int SPACING = 512;
    public static final int BASE_Y = 100;
    private static final BitSet USED = new BitSet();

    private enum Phase { STAGING, WRITING, LOADING, READY, RELEASING, FREE }

    private final ServerLevel level;
    private final int slot;
    private final BlockPos origin;
    private final List<net.minecraft.world.entity.Entity> spawned = new ArrayList<>();
    private volatile Phase phase = Phase.STAGING;
    private String label = "";

    // --- staging
    /** What the room itself placed (server thread only). */
    private Long2ObjectOpenHashMap<BlockState> solid = new Long2ObjectOpenHashMap<>();
    /** Decoration, possibly from the look thread: placed only where the room left air. */
    private Long2ObjectOpenHashMap<BlockState> deco = new Long2ObjectOpenHashMap<>();
    private final List<Runnable> deferred = new ArrayList<>();
    private boolean commitRequested;

    // --- the ledger: committed positions (written to disk by the writer) and positions placed live afterwards
    private volatile long[] committed = new long[0];
    private final LongArrayList live = new LongArrayList();
    private int liveFlushed;
    private boolean ledgerWritten;
    private final LongOpenHashSet touched = new LongOpenHashSet();
    private LongArrayList touchedList = new LongArrayList();
    private final List<ChunkPos> ticketAt = new ArrayList<>();

    // --- live placement (blocks the room's chunks could not take as saved data, and anything set later)
    private final Deque<Object[]> later = new ArrayDeque<>();
    private final ConcurrentLinkedQueue<Object[]> incoming = new ConcurrentLinkedQueue<>();
    private final Deque<Object[]> fallback = new ArrayDeque<>();
    private final AtomicInteger looks = new AtomicInteger();
    private volatile boolean released;

    // --- timing
    private final long takenAt = System.nanoTime();
    private long stagingNanos;
    private long serverNanos;
    private volatile long lookFirst;
    private volatile long lookLast;
    private long writeStartedAt;
    private volatile long writeNanos;
    private volatile long shapeNanos;
    private volatile int shapesFixed;
    private volatile int shapesReacting;
    private volatile int chunkCount;
    private volatile int blockCount;
    private int fallbackBlocks;
    private long loadStartedAt;
    private long loadingTicks;
    private int readyStreak;
    private long releaseStartedAt;
    private long releaseServerNanos;
    private long unloadWaitStartedAt;
    private volatile long clearNanos;
    private boolean clearStarted;
    private boolean thrownAway;
    private long lastWaitLog;

    private static final List<Arena> ACTIVE = new ArrayList<>();
    private static boolean tickerOn;
    /** Time per server tick spent placing queued blocks live, all arenas together. */
    private static final long BUDGET_NANOS = 12_000_000L;
    private static final int CELL = 5;
    private static final int TICKET_RADIUS = 4;
    private static java.nio.file.Path ledgers;
    /** Set while the server stops: arenas then keep their ledgers instead of clearing. */
    public static volatile boolean stopping;
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("arcade");

    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "arcade-chunks");
        thread.setDaemon(true);
        return thread;
    });

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
        Arena arena = new Arena(level, slot);
        ACTIVE.add(arena);
        ensureTicker();
        return arena;
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

    /** A name for the log lines about this arena (room and tier). */
    public void label(String text) {
        this.label = text;
    }

    private long key(int dx, int dy, int dz) {
        return BlockPos.asLong(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
    }

    private boolean staging() {
        return phase == Phase.STAGING;
    }

    public void set(int dx, int dy, int dz, BlockState state) {
        if (staging()) {
            solid.put(key(dx, dy, dz), state);
            return;
        }
        BlockPos pos = at(dx, dy, dz);
        level.setBlock(pos, state, 2);
        placedLive(pos);
    }

    private void placedLive(BlockPos pos) {
        live.add(pos.asLong());
        touched.add(ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4));
    }

    /**
     * A decoration block: while the room is being staged it joins the staged blocks (but never replaces a block the
     * room itself placed); afterwards it is placed live within the next ticks. Safe to call from the look thread.
     */
    public void setLater(int dx, int dy, int dz, BlockState state) {
        if (released) {
            return;
        }
        if (staging()) {
            synchronized (deco) {
                deco.putIfAbsent(key(dx, dy, dz), state);
            }
            return;
        }
        Object[] entry = {at(dx, dy, dz), state, Boolean.TRUE};
        if (level.getServer().isSameThread()) {
            later.add(entry);
        } else {
            incoming.add(entry); // from the look generator thread
        }
    }

    private static volatile long tickStart;
    private static volatile Thread serverThread;

    /** Debug aid (only while config/arcade-lightcheck.txt exists): logs the server thread's stack when a tick runs past 40 ms. */
    private static void startTickWatcher() {
        Thread watcher = new Thread(() -> {
            long reported = 0;
            while (true) {
                try {
                    Thread.sleep(8);
                } catch (InterruptedException stop) {
                    return;
                }
                long started = tickStart;
                Thread server = serverThread;
                if (server != null && started != 0 && started != reported && System.nanoTime() - started > 40_000_000L) {
                    reported = started;
                    StringBuilder out = new StringBuilder();
                    int n = 0;
                    for (StackTraceElement frame : server.getStackTrace()) {
                        if (n++ < 30) {
                            out.append("\n    at ").append(frame);
                        }
                    }
                    LOGGER.info("Arcade: tick running for {} ms, server thread is in:{}", (System.nanoTime() - started) / 1_000_000, out);
                }
            }
        }, "arcade-tick-watch");
        watcher.setDaemon(true);
        watcher.start();
    }

    private static void ensureTicker() {
        if (!tickerOn) {
            tickerOn = true;
            if (java.nio.file.Files.exists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("arcade-lightcheck.txt"))) {
                startTickWatcher();
            }
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.START_SERVER_TICK.register(server -> {
                serverThread = Thread.currentThread();
                tickStart = System.nanoTime();
            });
            net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
                drainAll();
                long took = (System.nanoTime() - tickStart) / 1_000_000;
                tickStart = 0;
                if (took > 50) {
                    synchronized (Arena.class) {
                        StringBuilder where = new StringBuilder();
                        for (Arena a : ACTIVE) {
                            where.append(' ').append(a.label).append(':').append(a.phase);
                            if (a.phase == Phase.READY) {
                                where.append('+').append(server.getTickCount() - a.readyTick);
                            }
                        }
                        LOGGER.info("Arcade: slow tick {} ms; arenas:{}", took, where);
                    }
                }
            });
        }
    }

    /** True until the room is completely ready: written, loaded, lit, its players moved in, its decoration placed. */
    public boolean decorating() {
        if (released) {
            return false;
        }
        return phase != Phase.READY || !later.isEmpty() || !incoming.isEmpty() || looks.get() > 0 || !fallback.isEmpty();
    }

    /** True once the room is ready (also after it was released: it no longer waits for anything). */
    public boolean ready() {
        return phase == Phase.READY && looks.get() == 0;
    }

    /** Called by Deco.async round a look generated off the server thread. */
    public void lookStarted() {
        if (looks.incrementAndGet() == 1 && lookFirst == 0) {
            lookFirst = System.nanoTime();
        }
    }

    public void lookDone() {
        if (looks.decrementAndGet() == 0) {
            lookLast = System.nanoTime();
        }
    }

    /** The room's own build is done: once the looks are too, the blocks are written and the room made ready. */
    public void commit() {
        if (commitRequested) {
            return;
        }
        commitRequested = true;
        stagingNanos = System.nanoTime() - takenAt;
        serverNanos += stagingNanos;
    }

    /** Runs {@code action} once the room is ready (at once when it already is). */
    public void whenReady(Runnable action) {
        if (phase == Phase.READY) {
            action.run();
        } else {
            deferred.add(action);
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // the per-tick work of every arena

    private static void drainAll() {
        List<Arena> arenas;
        synchronized (Arena.class) {
            if (ACTIVE.isEmpty()) {
                return;
            }
            arenas = new ArrayList<>(ACTIVE);
        }
        long until = System.nanoTime() + BUDGET_NANOS;
        for (Arena arena : arenas) {
            long t0 = System.nanoTime();
            try {
                arena.step(until);
            } catch (RuntimeException failure) {
                LOGGER.error("ALERT: arena slot {} failed in its tick", arena.slot, failure);
            }
            long spent = System.nanoTime() - t0;
            if (spent > 15_000_000L) {
                LOGGER.warn("Arcade: slot {} ({}) used {} ms of one tick in phase {}", arena.slot, arena.label, spent / 1_000_000, arena.phase);
            }
            if (arena.phase == Phase.RELEASING || arena.released) {
                arena.releaseServerNanos += spent;
            } else {
                arena.serverNanos += spent;
            }
        }
    }

    private void step(long until) {
        switch (phase) {
            case STAGING -> {
                if (commitRequested && looks.get() == 0 && !released) {
                    startWrite();
                }
            }
            case LOADING -> {
                if (!released) {
                    checkReady(until);
                }
            }
            case READY -> {
                if (!released) {
                    placeLive(until);
                }
            }
            case RELEASING -> releaseStep();
            default -> { }
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // commit: write the staged blocks into the saved data of the unloaded chunks (worker thread)

    private void startWrite() {
        phase = Phase.WRITING;
        writeStartedAt = System.nanoTime();
        Long2ObjectOpenHashMap<BlockState> roomBlocks = solid;
        Long2ObjectOpenHashMap<BlockState> decoration = deco;
        solid = new Long2ObjectOpenHashMap<>(0);
        deco = new Long2ObjectOpenHashMap<>(0);
        net.minecraft.server.MinecraftServer server = level.getServer();
        WRITER.execute(() -> {
            try {
                writeJob(server, roomBlocks, decoration);
            } catch (Throwable failure) {
                LOGGER.error("ALERT: writing the room of slot {} failed; placing it live instead", slot, failure);
                server.execute(() -> writeFailed(roomBlocks, decoration));
            }
        });
    }

    private static final class Group {
        final LongArrayList pos = new LongArrayList();
        final List<BlockState> states = new ArrayList<>();
        boolean solid;
    }

    private void writeJob(net.minecraft.server.MinecraftServer server, Long2ObjectOpenHashMap<BlockState> all,
            Long2ObjectOpenHashMap<BlockState> decoration) throws Exception {
        long t0 = System.nanoTime();
        // decoration goes where the room left air (or nothing)
        for (Long2ObjectMap.Entry<BlockState> e : decoration.long2ObjectEntrySet()) {
            BlockState there = all.get(e.getLongKey());
            if (there == null || there.isAir()) {
                all.put(e.getLongKey(), e.getValue());
            }
        }
        long t1 = System.nanoTime();
        int[] shapes = Shapes.settle(all);
        shapesReacting = shapes[0];
        shapesFixed = shapes[1];
        shapeNanos = System.nanoTime() - t1;
        if (java.nio.file.Files.exists(net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("arcade-lightcheck.txt"))) {
            collectLightSamples(all);
        }
        // the ledger first: a crash while the chunks are written still leaves a list of what to clear
        long[] keys = all.keySet().toLongArray();
        committed = keys;
        writeLedger(keys);
        Long2ObjectOpenHashMap<Group> groups = new Long2ObjectOpenHashMap<>();
        for (Long2ObjectMap.Entry<BlockState> e : all.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            long chunk = ChunkPos.pack(BlockPos.getX(k) >> 4, BlockPos.getZ(k) >> 4);
            Group g = groups.computeIfAbsent(chunk, c -> new Group());
            g.pos.add(k);
            g.states.add(e.getValue());
            if (!e.getValue().isAir()) {
                g.solid = true;
            }
        }
        groups.values().removeIf(g -> !g.solid); // chunks that would only get air stay as they are
        blockCount = 0;
        groups.values().forEach(g -> blockCount += g.pos.size());
        chunkCount = groups.size();
        LongArrayList chunkKeys = new LongArrayList(groups.keySet());
        // which of them are loaded right now? (asked of the server thread)
        LongOpenHashSet loaded = server.submit(() -> {
            LongOpenHashSet out = new LongOpenHashSet();
            for (long c : chunkKeys) {
                if (isTracked(level, ChunkPos.unpack(c))) {
                    out.add(c);
                }
            }
            return out;
        }).get(30, TimeUnit.SECONDS);
        // read all, edit, write
        Long2ObjectOpenHashMap<java.util.concurrent.CompletableFuture<java.util.Optional<net.minecraft.nbt.CompoundTag>>> reads = new Long2ObjectOpenHashMap<>();
        for (long c : chunkKeys) {
            if (!loaded.contains(c)) {
                reads.put(c, ChunkEdit.read(level, ChunkPos.unpack(c)));
            }
        }
        List<java.util.concurrent.CompletableFuture<?>> writes = new ArrayList<>();
        LongArrayList written = new LongArrayList();
        for (long c : chunkKeys) {
            if (loaded.contains(c)) {
                continue;
            }
            ChunkPos cp = ChunkPos.unpack(c);
            var existing = reads.get(c).join();
            net.minecraft.nbt.CompoundTag nbt = existing.isPresent() ? existing.get().copy() : ChunkEdit.fresh(level, cp);
            Group g = groups.get(c);
            ChunkEdit.apply(nbt, g.pos.toLongArray(), g.states.toArray(new BlockState[0]), g.pos.size());
            writes.add(ChunkEdit.write(level, cp, nbt));
            written.add(c);
        }
        java.util.concurrent.CompletableFuture.allOf(writes.toArray(new java.util.concurrent.CompletableFuture<?>[0])).join();
        writeNanos = System.nanoTime() - t0;
        server.execute(() -> writeFinished(groups, loaded, written));
    }

    // --- light check (only while config/arcade-lightcheck.txt exists): a few lamps, open spots and covered spots, read back once ready
    private long[] lcEmit = new long[0];
    private int[] lcEmission = new int[0];
    private long[] lcOpen = new long[0];
    private long[] lcCovered = new long[0];
    private long[] lcShape = new long[0];

    private void collectLightSamples(Long2ObjectOpenHashMap<BlockState> all) {
        LongArrayList emit = new LongArrayList();
        List<Integer> emission = new ArrayList<>();
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap top = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        top.defaultReturnValue(Integer.MIN_VALUE);
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap topAny = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        topAny.defaultReturnValue(Integer.MIN_VALUE);
        int seen = 0;
        LongArrayList shapeSamples = new LongArrayList();
        int reactSeen = 0;
        for (Long2ObjectMap.Entry<BlockState> e : all.long2ObjectEntrySet()) {
            long k = e.getLongKey();
            BlockState st = e.getValue();
            if (st.getLightEmission() > 0 && !st.isAir() && (seen++ % 7 == 0) && emit.size() < 12) {
                emit.add(k);
                emission.add(st.getLightEmission());
            }
            if (!st.isAir() && Shapes.reacts(st.getBlock()) && (reactSeen++ % 5 == 0) && shapeSamples.size() < 300) {
                shapeSamples.add(k);
            }
            if (!st.isAir()) {
                long anyCol = BlockPos.asLong(BlockPos.getX(k), 0, BlockPos.getZ(k));
                topAny.put(anyCol, Math.max(topAny.get(anyCol), BlockPos.getY(k)));
            }
            if (st.canOcclude()) {
                long col = BlockPos.asLong(BlockPos.getX(k), 0, BlockPos.getZ(k));
                top.put(col, Math.max(top.get(col), BlockPos.getY(k)));
            }
        }
        LongArrayList open = new LongArrayList();
        LongArrayList covered = new LongArrayList();
        int n = 0;
        for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry e : top.long2IntEntrySet()) {
            long col = e.getLongKey();
            int x = BlockPos.getX(col), z = BlockPos.getZ(col), y = e.getIntValue();
            if (n++ % 53 != 0) {
                continue;
            }
            // open: just above the highest solid block of a column that has nothing above (no state above it either)
            if (open.size() < 10 && topAny.get(col) == y) {
                open.add(BlockPos.asLong(x, y + 1, z));
            }
            // covered: an empty spot two below that highest block with a solid block of its own column between
            if (covered.size() < 10 && !all.containsKey(BlockPos.asLong(x, y - 2, z)) && y - 2 > BASE_Y - 30
                && all.containsKey(BlockPos.asLong(x, y - 1, z)) && all.get(BlockPos.asLong(x, y - 1, z)).canOcclude()) {
                covered.add(BlockPos.asLong(x, y - 2, z));
            }
        }
        lcEmit = emit.toLongArray();
        lcEmission = emission.stream().mapToInt(Integer::intValue).toArray();
        lcOpen = open.toLongArray();
        lcOpen = lcOpen.length > 0 ? lcOpen : new long[0];
        lcCovered = covered.toLongArray();
        lcShape = shapeSamples.toLongArray();
    }

    private int shapeDebug;

    private void reportLight() {
        var block = net.minecraft.world.level.LightLayer.BLOCK;
        var sky = net.minecraft.world.level.LightLayer.SKY;
        int emitOk = 0, openOk = 0, coveredOk = 0;
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < lcEmit.length; i++) {
            BlockPos p = BlockPos.of(lcEmit[i]);
            int got = level.getBrightness(block, p);
            if (got >= lcEmission[i]) {
                emitOk++;
            } else {
                detail.append(" lamp@").append(p.toShortString()).append("=").append(got).append("/").append(lcEmission[i]);
            }
        }
        for (long k : lcOpen) {
            BlockPos p = BlockPos.of(k);
            int got = level.getBrightness(sky, p);
            if (got == 15) {
                openOk++;
            } else {
                detail.append(" open@").append(p.toShortString()).append("=").append(got);
            }
        }
        for (long k : lcCovered) {
            BlockPos p = BlockPos.of(k);
            int got = level.getBrightness(sky, p);
            if (got < 15) {
                coveredOk++;
            } else {
                detail.append(" covered@").append(p.toShortString()).append("=").append(got);
            }
        }
        int shapeOk = 0;
        for (long k : lcShape) {
            BlockPos p = BlockPos.of(k);
            BlockState there = level.getBlockState(p);
            BlockState vanilla = net.minecraft.world.level.block.Block.updateFromNeighbourShapes(there, level, p);
            if (vanilla == there || vanilla.getBlock() != there.getBlock()) {
                shapeOk++;
            } else {
                detail.append(" shape@").append(p.toShortString()).append(" ").append(there).append(" -> ").append(vanilla);
                if (shapeDebug++ < 2) {
                    for (int dy = -3; dy <= 3; dy++) {
                        detail.append(" [y").append(dy).append(' ').append(level.getBlockState(p.offset(0, dy, 0))).append(']');
                    }
                }
            }
        }
        LOGGER.info("Arcade shape check {} slot {}: {}/{} sampled shape-sensitive blocks already match vanilla's own neighbour update", label, slot, shapeOk, lcShape.length);
        LOGGER.info("Arcade light check {} slot {}: lamps lit {}/{}, open spots sky 15 {}/{}, covered spots dark {}/{}{}", label, slot, emitOk, lcEmit.length, openOk,
            lcOpen.length, coveredOk, lcCovered.length, detail.length() == 0 ? "" : " MISMATCH:" + detail);
    }

    private static java.lang.reflect.Field pendingUnloadsField;
    private static boolean pendingUnloadsLooked;

    /** Chunks the chunk map has taken off its books but is still saving (private field, read by reflection; server thread only). */
    @SuppressWarnings("unchecked")
    private static boolean unloading(ServerLevel level, long key) {
        try {
            if (!pendingUnloadsLooked) {
                pendingUnloadsLooked = true;
                pendingUnloadsField = net.minecraft.server.level.ChunkMap.class.getDeclaredField("pendingUnloads");
                pendingUnloadsField.setAccessible(true);
            }
            if (pendingUnloadsField != null) {
                return ((it.unimi.dsi.fastutil.longs.Long2ObjectMap<?>) pendingUnloadsField.get(level.getChunkSource().chunkMap)).containsKey(key);
            }
        } catch (ReflectiveOperationException | RuntimeException failure) {
            LOGGER.warn("Arcade: cannot see the chunk map's pending unloads ({}); chunks count as unloaded one second after they leave the map", failure.toString());
            pendingUnloadsField = null;
        }
        return false;
    }

    /** True when the server has any holder for the chunk (loaded, loading, or being saved after its unload). */
    private static boolean isTracked(ServerLevel level, ChunkPos pos) {
        return level.getChunkSource().chunkMap.getUpdatingChunkIfPresent(pos.pack()) != null
            || level.getChunkSource().getChunkNow(pos.x(), pos.z()) != null || unloading(level, pos.pack());
    }

    private void writeLedger(long[] keys) throws java.io.IOException {
        java.nio.file.Path file = ledger();
        if (file == null) {
            return;
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(keys.length * Long.BYTES);
        for (long k : keys) {
            buffer.putLong(k);
        }
        java.nio.file.Files.createDirectories(ledgers);
        java.nio.file.Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        java.nio.file.Files.write(temporary, buffer.array());
        java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        ledgerWritten = true;
    }

    /** Back on the server thread: the chunks are written. */
    private void writeFinished(Long2ObjectOpenHashMap<Group> groups, LongOpenHashSet loadedAtCheck, LongArrayList written) {
        long t0 = System.nanoTime();
        for (long c : groups.keySet()) {
            touched.add(c);
        }
        touchedList = new LongArrayList(touched);
        // chunks that were loaded at the check, or got loaded since: their blocks are placed live
        for (long c : groups.keySet()) {
            boolean viaLive = loadedAtCheck.contains(c) || (isTracked(level, ChunkPos.unpack(c)) && written.contains(c));
            if (viaLive) {
                Group g = groups.get(c);
                for (int i = 0; i < g.pos.size(); i++) {
                    fallback.add(new Object[] {BlockPos.of(g.pos.getLong(i)), g.states.get(i), Boolean.FALSE});
                }
                fallbackBlocks += g.pos.size();
            }
        }
        if (released) {
            // thrown away while it was being written: clear what was written, straight away (nothing is loaded)
            fallback.clear();
            beginRelease();
            serverNanos += System.nanoTime() - t0;
            return;
        }
        addTickets();
        phase = Phase.LOADING;
        loadStartedAt = System.nanoTime();
        serverNanos += System.nanoTime() - t0;
    }

    private void writeFailed(Long2ObjectOpenHashMap<BlockState> roomBlocks, Long2ObjectOpenHashMap<BlockState> decoration) {
        // Emergency path: everything goes in live (budgeted), chunks loaded by a ticket on each touched cell.
        for (Long2ObjectMap.Entry<BlockState> e : decoration.long2ObjectEntrySet()) {
            BlockState there = roomBlocks.get(e.getLongKey());
            if (there == null || there.isAir()) {
                roomBlocks.put(e.getLongKey(), e.getValue());
            }
        }
        for (Long2ObjectMap.Entry<BlockState> e : roomBlocks.long2ObjectEntrySet()) {
            fallback.add(new Object[] {BlockPos.of(e.getLongKey()), e.getValue(), Boolean.FALSE});
            touched.add(ChunkPos.pack(BlockPos.getX(e.getLongKey()) >> 4, BlockPos.getZ(e.getLongKey()) >> 4));
        }
        committed = roomBlocks.keySet().toLongArray();
        try {
            writeLedger(committed);
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not write the arena ledger", failure);
        }
        touchedList = new LongArrayList(touched);
        if (released) {
            fallback.clear();
            beginRelease();
            return;
        }
        addTickets();
        phase = Phase.LOADING;
        loadStartedAt = System.nanoTime();
    }

    // ------------------------------------------------------------------------------------------------------
    // tickets: whole 5x5 cells of chunks get entity-ticking level, so every touched chunk ticks. The DRAGON ticket type
    // loads and simulates, never expires and is never saved (a PORTAL ticket is saved with the world and would reload the
    // room's chunks after a stop), so the arena owns exactly how long its chunks stay.

    private void addTickets() {
        LongOpenHashSet cells = new LongOpenHashSet();
        for (long c : touchedList) {
            ChunkPos cp = ChunkPos.unpack(c);
            cells.add(ChunkPos.pack(Math.floorDiv(cp.x(), CELL), Math.floorDiv(cp.z(), CELL)));
        }
        ticketAt.clear();
        for (long cell : cells) {
            ChunkPos cp = ChunkPos.unpack(cell);
            ticketAt.add(new ChunkPos(cp.x() * CELL + CELL / 2, cp.z() * CELL + CELL / 2));
        }
        for (ChunkPos pos : ticketAt) {
            level.getChunkSource().addTicketWithRadius(TicketType.DRAGON, pos, TICKET_RADIUS);
        }
    }

    private void removeTickets() {
        for (ChunkPos pos : ticketAt) {
            level.getChunkSource().removeTicketWithRadius(TicketType.DRAGON, pos, TICKET_RADIUS);
        }
        ticketAt.clear();
    }

    // ------------------------------------------------------------------------------------------------------
    // loading: wait until every touched chunk is full, lit and entity-ticking

    private long readyTick;
    private String notReady;

    private boolean chunksReady() {
        notReady = null;
        for (long c : touchedList) {
            ChunkPos cp = ChunkPos.unpack(c);
            var chunk = level.getChunkSource().getChunkNow(cp.x(), cp.z());
            if (chunk == null) {
                notReady = "not loaded " + cp;
                return false;
            }
            if (!level.isPositionEntityTicking(new BlockPos(cp.x() * 16 + 8, BASE_Y, cp.z() * 16 + 8))) {
                notReady = "not entity ticking " + cp;
                return false;
            }
            if (!chunk.isLightCorrect()) {
                notReady = "not lit " + cp;
                return false;
            }
        }
        return true;
    }

    private void checkReady(long until) {
        loadingTicks++;
        placeLive(until);
        if (!fallback.isEmpty()) {
            readyStreak = 0;
            return;
        }
        boolean chunksOk = chunksReady();
        boolean timedOut = loadingTicks > 20 * 90;
        if (loadingTicks % 200 == 0 && !chunksOk) {
            LOGGER.warn("Arcade: slot {} ({}) still waiting after {} s: {}", slot, label, loadingTicks / 20, notReady);
        }
        if (chunksOk && !level.getChunkSource().getLightEngine().hasLightWork()) {
            readyStreak++;
        } else {
            readyStreak = 0;
        }
        if (readyStreak >= 2 || timedOut) {
            if (timedOut && !chunksOk) {
                LOGGER.error("ALERT: slot {} ({}) is not completely ready after 90 s ({}); moving the players in anyway", slot, label, notReady);
            }
            becomeReady();
        }
    }

    private void becomeReady() {
        long t0 = System.nanoTime();
        phase = Phase.READY;
        readyTick = level.getServer().getTickCount();
        List<Runnable> run = new ArrayList<>(deferred);
        deferred.clear();
        for (Runnable action : run) {
            try {
                action.run();
            } catch (RuntimeException failure) {
                LOGGER.error("ALERT: a deferred spawn or move failed in slot {}", slot, failure);
            }
        }
        long now = System.nanoTime();
        serverNanos += now - t0;
        long looksMs = lookFirst == 0 ? 0 : (Math.max(lookLast, lookFirst) - lookFirst) / 1_000_000;
        LOGGER.info("Arcade build {} slot {}: staging {} ms (server thread), looks {} ms, shapes {} ms ({} blocks react, {} fixed), nbt write {} ms (off-thread, {} chunks, {} blocks, {} placed live), "
            + "chunk load-to-ready {} ms, total {} ms until players move in, server thread {} ms", label, slot, stagingNanos / 1_000_000, looksMs,
            shapeNanos / 1_000_000, shapesReacting, shapesFixed, writeNanos / 1_000_000, chunkCount, blockCount, fallbackBlocks,
            (now - loadStartedAt) / 1_000_000, (now - takenAt) / 1_000_000, serverNanos / 1_000_000);
        if (lcEmit.length + lcOpen.length + lcCovered.length + lcShape.length > 0) {
            reportLight();
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // live placement: what a loaded chunk took no saved data for, and decoration set after the room was ready

    private void placeLive(long until) {
        for (Object[] in; (in = incoming.poll()) != null;) {
            later.add(in);
        }
        placeFrom(fallback, until, false);
        placeFrom(later, until, true);
    }

    private void placeFrom(Deque<Object[]> queue, long until, boolean record) {
        int waiting = 0;
        int budget = 0;
        while (!queue.isEmpty() && waiting <= 64) {
            if ((++budget & 15) == 0 && System.nanoTime() >= until) {
                return;
            }
            Object[] next = queue.poll();
            BlockPos pos = (BlockPos) next[0];
            if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) {
                queue.add(next); // its chunk is loading; try again later
                waiting++;
                continue;
            }
            if (next[2] == Boolean.TRUE && !level.getBlockState(pos).isAir()) {
                continue; // never over a block the room placed
            }
            level.setBlock(pos, (BlockState) next[1], 2 | 16);
            if (record) {
                placedLive(pos);
            }
        }
    }

    private java.nio.file.Path ledger() {
        return ledgers == null ? null : ledgers.resolve("slot-" + slot + ".bin");
    }

    /** Appends the positions placed live since the last call to the ledger file (called every few seconds by the run). */
    public void flushLedger() {
        java.nio.file.Path file = ledger();
        if (file == null || !ledgerWritten || liveFlushed >= live.size() || phase == Phase.FREE) {
            return;
        }
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate((live.size() - liveFlushed) * Long.BYTES);
        for (int i = liveFlushed; i < live.size(); i++) {
            buffer.putLong(live.getLong(i));
        }
        try {
            java.nio.file.Files.write(file, buffer.array(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            liveFlushed = live.size();
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not write the arena ledger {}", file, failure);
        }
    }

    /** Every position the room placed (for checks): the committed ledger and what was placed live since. */
    public List<BlockPos> positions() {
        List<BlockPos> out = new ArrayList<>();
        for (long k : committed) {
            out.add(BlockPos.of(k));
        }
        for (long k : live) {
            out.add(BlockPos.of(k));
        }
        return out;
    }

    // ------------------------------------------------------------------------------------------------------
    // at start: clear what a stop or a crash left behind

    /** At start: clears every block a room left behind last time (from the ledgers), then forgets the ledgers. */
    public static void clearLeftovers(ServerLevel level, java.nio.file.Path dir) {
        ledgers = dir;
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
                LongArrayList keys = new LongArrayList();
                while (buffer.remaining() >= Long.BYTES) {
                    keys.add(buffer.getLong());
                }
                int cleared = clearSaved(level, keys, null, true);
                java.nio.file.Files.delete(file);
                LOGGER.info("Cleared {} blocks a room left behind ({})", cleared, file.getFileName());
            }
        } catch (java.io.IOException failure) {
            LOGGER.error("ALERT: could not clear the arenas' leftovers", failure);
        }
    }

    /**
     * Sets the listed positions to air in the saved data of their chunks (only chunks in {@code only}, when given). A
     * chunk that is loaded is cleared through the world instead (only possible from the server thread). Blocks; call
     * from a worker thread, or at start. Returns how many blocks it cleared.
     */
    private static int clearSaved(ServerLevel level, LongArrayList keys, LongOpenHashSet only, boolean serverThread) {
        Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
        for (long k : keys) {
            long chunk = ChunkPos.pack(BlockPos.getX(k) >> 4, BlockPos.getZ(k) >> 4);
            if (only == null || only.contains(chunk)) {
                byChunk.computeIfAbsent(chunk, c -> new LongArrayList()).add(k);
            }
        }
        int cleared = 0;
        BlockState air = Blocks.AIR.defaultBlockState();
        List<java.util.concurrent.CompletableFuture<?>> writes = new ArrayList<>();
        for (Long2ObjectMap.Entry<LongArrayList> e : byChunk.long2ObjectEntrySet()) {
            ChunkPos cp = ChunkPos.unpack(e.getLongKey());
            LongArrayList positions = e.getValue();
            if (serverThread && isTracked(level, cp)) {
                for (long k : positions) {
                    BlockPos pos = BlockPos.of(k);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlock(pos, air, 2 | 16);
                        cleared++;
                    }
                }
                continue;
            }
            var existing = ChunkEdit.read(level, cp).join();
            if (existing.isEmpty()) {
                continue; // never written: nothing there
            }
            net.minecraft.nbt.CompoundTag nbt = existing.get().copy();
            BlockState[] states = new BlockState[positions.size()];
            java.util.Arrays.fill(states, air);
            ChunkEdit.apply(nbt, positions.toLongArray(), states, positions.size());
            writes.add(ChunkEdit.write(level, cp, nbt));
            cleared += positions.size();
        }
        java.util.concurrent.CompletableFuture.allOf(writes.toArray(new java.util.concurrent.CompletableFuture<?>[0])).join();
        return cleared;
    }

    // ------------------------------------------------------------------------------------------------------

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
                    set(x, y, z, state);
                }
            }
        }
    }

    /** Adds an entity the room made (a mob, a target); it is removed with the arena. While staging it joins the room once ready. */
    public <T extends net.minecraft.world.entity.Entity> T spawn(T entity, double dx, double dy, double dz) {
        entity.snapTo(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, 0F, 0F);
        entity.addTag("arcade_room");
        spawned.add(entity);
        if (phase == Phase.READY) {
            level.addFreshEntity(entity);
        } else {
            deferred.add(() -> level.addFreshEntity(entity));
        }
        return entity;
    }

    /** Removes one block a room placed (it stays remembered; clearing it again later is harmless). */
    public void clearBlock(int dx, int dy, int dz) {
        if (staging()) {
            solid.put(key(dx, dy, dz), Blocks.AIR.defaultBlockState());
            return;
        }
        level.setBlock(at(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
    }

    /** True when pos lies in this arena's slot. */
    public boolean contains(BlockPos pos) {
        return pos.getX() >= origin.getX() - SPACING / 2 && pos.getX() < origin.getX() + SPACING / 2;
    }

    /** Moves a player in (while the room is staged: once it is ready, by then the player must still be online). */
    public void put(ServerPlayer player, double dx, double dy, double dz, float yaw) {
        if (phase != Phase.READY && phase != Phase.RELEASING) {
            UUID id = player.getUUID();
            deferred.add(() -> {
                ServerPlayer there = level.getServer().getPlayerList().getPlayer(id);
                if (there != null) {
                    move(there, dx, dy, dz, yaw);
                }
            });
            return;
        }
        move(player, dx, dy, dz, yaw);
    }

    private void move(ServerPlayer player, double dx, double dy, double dz, float yaw) {
        player.teleportTo(level, origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz, Set.of(), yaw, 0F, false);
        player.setDeltaMovement(0, 0, 0);
        player.resetFallDistance();
    }

    // ------------------------------------------------------------------------------------------------------
    // release

    /** Clears everything the room placed and frees the slot (a room still being prepared is simply thrown away). */
    public void release() {
        if (released) {
            return;
        }
        long t0 = System.nanoTime();
        released = true;
        releaseStartedAt = t0;
        unloadWaitStartedAt = t0;
        later.clear();
        incoming.clear();
        fallback.clear();
        deferred.clear();
        spawned.forEach(net.minecraft.world.entity.Entity::discard);
        spawned.clear();
        thrownAway = phase != Phase.READY;
        if (stopping) {
            // The ledger stays on disk; the next start clears this arena.
            flushLedger();
            return;
        }
        switch (phase) {
            case STAGING -> {
                solid = new Long2ObjectOpenHashMap<>(0);
                deco = new Long2ObjectOpenHashMap<>(0);
                finish(t0);
            }
            case WRITING -> { } // the write finishes first, then writeFinished starts the release
            case LOADING, READY -> {
                // Arrows and drops left lying around in the slot go too.
                level.getEntities((net.minecraft.world.entity.Entity) null, new net.minecraft.world.phys.AABB(origin.getX() - SPACING / 2.0, BASE_Y - 64,
                        -SPACING / 2.0, origin.getX() + SPACING / 2.0, BASE_Y + 64, SPACING / 2.0),
                    e -> e instanceof net.minecraft.world.entity.projectile.Projectile || e instanceof net.minecraft.world.entity.item.ItemEntity
                        || e.entityTags().contains("arcade_room"))
                    .forEach(net.minecraft.world.entity.Entity::discard);
                beginRelease();
            }
            default -> { }
        }
        releaseServerNanos += System.nanoTime() - t0;
    }

    private void beginRelease() {
        removeTickets();
        flushLedger();
        touchedList = new LongArrayList(touched);
        phase = Phase.RELEASING;
        unloadWaitStartedAt = System.nanoTime();
        clearStarted = false;
        synchronized (Arena.class) {
            if (!ACTIVE.contains(this)) {
                ACTIVE.add(this);
            }
        }
    }

    private void releaseStep() {
        if (clearStarted || stopping) {
            return;
        }
        for (long c : touchedList) {
            ChunkPos cp = ChunkPos.unpack(c);
            if (isTracked(level, cp)) {
                long now = System.nanoTime();
                if (now - Math.max(lastWaitLog, unloadWaitStartedAt) > 30_000_000_000L) {
                    lastWaitLog = now;
                    LOGGER.warn("Arcade: slot {} ({}) waits for chunk {} to unload ({} s)", slot, label, cp, (now - unloadWaitStartedAt) / 1_000_000_000L);
                }
                return;
            }
        }
        clearStarted = true;
        long[] committedKeys = committed;
        long[] liveKeys = live.toLongArray();
        long[] touchedKeys = touched.toLongArray();
        long unloaded = System.nanoTime();
        net.minecraft.server.MinecraftServer server = level.getServer();
        WRITER.execute(() -> {
            long t0 = System.nanoTime();
            try {
                LongArrayList keys = new LongArrayList(committedKeys);
                keys.addElements(keys.size(), liveKeys);
                clearSaved(level, keys, new LongOpenHashSet(touchedKeys), false);
            } catch (Throwable failure) {
                LOGGER.error("ALERT: clearing slot {} failed; its ledger stays for the next start", slot, failure);
                server.execute(() -> {
                    phase = Phase.FREE;
                    synchronized (Arena.class) {
                        ACTIVE.remove(this);
                    }
                });
                return;
            }
            clearNanos = System.nanoTime() - t0;
            server.execute(() -> finish(unloaded));
        });
    }

    private void finish(long unloadedAt) {
        long t0 = System.nanoTime();
        try {
            if (ledger() != null) {
                java.nio.file.Files.deleteIfExists(ledger());
            }
        } catch (java.io.IOException failure) {
            LOGGER.warn("Could not remove the arena ledger {}", ledger(), failure);
        }
        phase = Phase.FREE;
        synchronized (Arena.class) {
            ACTIVE.remove(this);
            USED.clear(slot);
        }
        releaseServerNanos += System.nanoTime() - t0;
        long now = System.nanoTime();
        LOGGER.info("Arcade release {} slot {}: {}server thread {} ms, unload wait {} ms, nbt clear {} ms (off-thread), {} ms until the slot was free",
            label, slot, thrownAway ? "thrown away before ready, " : "", releaseServerNanos / 1_000_000,
            Math.max(0, unloadedAt - unloadWaitStartedAt) / 1_000_000, clearNanos / 1_000_000, (now - releaseStartedAt) / 1_000_000);
    }
}
