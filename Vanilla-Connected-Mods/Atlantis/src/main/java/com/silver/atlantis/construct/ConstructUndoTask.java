package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.undo.UndoMetadataIO;
import com.silver.atlantis.construct.undo.UndoPaths;
import com.silver.atlantis.construct.undo.UndoRunMetadata;
import com.silver.atlantis.construct.undo.UndoSliceFile;
import com.silver.atlantis.construct.state.ConstructRunState;
import com.silver.atlantis.construct.state.ConstructRunStateIO;
import com.silver.atlantis.construct.state.ConstructStatePaths;
import com.silver.atlantis.protect.ProtectionManager;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.extension.input.ParserContext;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.internal.cui.CUIEvent;
import com.sk89q.worldedit.session.SessionKey;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.util.SideEffectSet;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.sk89q.worldedit.extension.input.InputParseException;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BlockTypes;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.block.Blocks;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.enginehub.linbus.format.snbt.LinStringIO;
import org.enginehub.linbus.tree.LinCompoundTag;
import org.enginehub.linbus.tree.impl.LinTagReader;

import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.stream.Stream;

final class ConstructUndoTask implements ConstructJob {

    private enum Stage {
        LOAD_METADATA,
        CLEANUP_MOBS,
        CLEANUP_ITEMS,
        LOAD_SLICE,
        PRELOAD_CHUNKS,
        PREPASS_CLEAR_FLUIDS,
        APPLY_SLICE,
        POST_FORCE_FLUID_UPDATES,
        POST_CLEANUP_ARTIFACTS,
        POST_CLEANUP_FORCE_FLUID_TICKS,
        DELETE_RUN_ASYNC,
        WAIT_BETWEEN_SLICES,
        DONE
    }

    private final UUID requesterId;
    private final ConstructConfig config;
    private final MinecraftServer server;
    private final String runId;
    private final Executor ioExecutor;

    private UndoRunMetadata metadata;
    private ServerWorld world;

    private BlockPos undoCenter;

    private List<? extends net.minecraft.entity.Entity> entitiesToProcess;
    private int entitiesProcessedIndex;

    private Path runDir;

    private Path currentSliceFile;

    private int sliceIndex;
    private UndoSliceFile.UndoSliceDataIndexed currentSlice;
    private int entryIndex;

    private int prepassEntryIndex;
    private long prepassFluidsCleared;
    private long lastPrepassProgressNanos;

    private BaseBlock[] blockPalette;
    private boolean[] blockPaletteIsAir;
    private LinCompoundTag[] nbtPalette;
    private final Map<Long, BaseBlock> blockWithNbtCache = new HashMap<>();

    private long sliceAttempted;
    private long sliceApplied;
    private long sliceSkipped;
    private long sliceDeferred;

    String getRunId() {
        return runId;
    }
    private long sliceFailedFalse;
    private long sliceFailedException;
    private long lastProgressNanos;

    // Lightweight profiling to catch occasional multi-second hitches.
    private long lastSlowLogNanos;
    private static final long SLOW_LOG_COOLDOWN_NANOS = 5_000_000_000L;
    private static final long SLOW_OP_NANOS = 50_000_000L; // 50ms
    private long lastTickStartNanos;
    private double adaptiveWorkScale = 1.0d;

    private static final int UNDO_FLUSH_EVERY_BLOCKS_DEFAULT = 2048;
    private int undoFlushEveryBlocks;
    private int undoSinceLastFlush;

    // Preload chunks for each slice so undo doesn't trigger expensive synchronous loads per-block.
    private Deque<ChunkPos> chunksToLoad;
    private final Set<Long> chunkTicketKeys = new HashSet<>();
    private final it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<CompletableFuture<?>> activeChunkLoads = new it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap<>();

    private static final ChunkTicketType PRELOAD_TICKET_TYPE = ChunkTicketType.FORCED;
    private static final int PRELOAD_TICKET_LEVEL = 2;

    private boolean sliceSampleLogged;

    private enum ApplyPass {
        NON_AIR,
        AIR
    }

    private ApplyPass applyPass = ApplyPass.NON_AIR;

    private EditSession editSession;
    private ParserContext parserContext;

    private static final Actor UNDO_ACTOR = new SystemActor();

    // We need NETWORK updates so clients actually see the undo, and POI updates to avoid POI mismatch spam.
    // Keep other heavy side effects disabled for speed.
    private static final SideEffectSet UNDO_SIDE_EFFECTS = SideEffectSet.none()
        .with(SideEffect.NETWORK, SideEffect.State.ON)
        .with(SideEffect.POI_UPDATE, SideEffect.State.ON);

    private static final BaseBlock WE_AIR = BlockTypes.AIR.getDefaultState().toBaseBlock();

    private final Map<String, BaseBlock> parsedBlockStringCache = new HashMap<>();
    private final Map<String, LinCompoundTag> parsedNbtCache = new HashMap<>();
    private final Set<String> badNbtLogged = new HashSet<>();

    private Stage stage = Stage.LOAD_METADATA;

    private boolean undoFullyCompleted;
    private boolean protectionUnregistered;
    private boolean fluidTickGuardActive;
    private boolean fluidTickGuardUsingRunBounds;

    private int waitTicks;

    private CompletableFuture<DeleteResult> pendingDelete;

    // After restoring blocks, force neighbor updates on water/lava blocks that were actually restored
    // by undo (fast mode disables lots of neighbor updates).
    private LongArrayList postFluidQueue;
    private int postFluidIndex;
    private long postFluidUpdated;
    private long postFluidQueueDropped;
    private long lastPostFluidProgressNanos;
    private final BlockPos.Mutable fluidPos = new BlockPos.Mutable();

    // Hard cap to prevent large builds from accumulating millions of queued fluid positions,
    // which can cause huge memory pressure and long-lasting tick lag.
    private static final int POST_FLUID_QUEUE_MAX = 200_000;

    // Cleanup pass: remove blocks that can appear after paste due to physics/growth
    // but were never part of the schematic and therefore were never recorded in undo.
    private static final int MAX_ARTIFACT_REMOVALS_PER_TICK = 2048;
    private int cleanupX;
    private int cleanupY;
    private int cleanupZ;
    private boolean cleanupInit;
    private long cleanupScanned;
    private long cleanupRemoved;
    private long lastCleanupProgressNanos;
    private LongArrayList cleanupFluidQueue;
    private int cleanupFluidIndex;
    private final BlockPos.Mutable cleanupPos = new BlockPos.Mutable();
    private final BlockPos.Mutable neighborPos = new BlockPos.Mutable();

    ConstructUndoTask(ServerCommandSource source, ConstructConfig config, MinecraftServer server, String runIdOrNull, Executor ioExecutor) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = config;
        this.server = server;
        this.runId = runIdOrNull;
        this.ioExecutor = ioExecutor;

        send("Undo started.");
    }

    @Override
    public boolean tick(MinecraftServer ignored) {
        long tickStartNanos = System.nanoTime();
        updateAdaptiveWorkScale(tickStartNanos);

        // Undo can be visually overwhelming; throttle per-tick work via config so changes stream in slower.
        long configured = config.undoTickTimeBudgetNanos();
        long baseBudget = config.tickTimeBudgetNanos();
        long undoBudget = configured > 0 ? Math.min(configured, baseBudget) : Math.min(baseBudget, 3_000_000L);
        long deadline = tickStartNanos + scaledBudget(Math.max(250_000L, undoBudget));

        while (System.nanoTime() < deadline && stage != Stage.DONE) {
            switch (stage) {
                case LOAD_METADATA -> {
                    String runToLoad = runId;
                    if (runToLoad == null) {
                        runToLoad = UndoPaths.findLatestRunIdOrNull();
                        if (runToLoad == null || runToLoad.isBlank()) {
                            send("No undo history found.");
                            stage = Stage.DONE;
                            break;
                        }
                    }

                    Path runDir = UndoPaths.runDir(runToLoad);
                    Path metadataFile = UndoPaths.metadataFile(runDir);
                    if (!Files.exists(metadataFile)) {
                        send("Undo metadata not found: " + metadataFile);
                        stage = Stage.DONE;
                        break;
                    }

                    try {
                        metadata = UndoMetadataIO.read(metadataFile);
                    } catch (Exception e) {
                        send("Failed to read undo metadata: " + e.getMessage());
                        stage = Stage.DONE;
                        break;
                    }

                    // Keep the resolved directory so we can delete it after a successful undo.
                    this.runDir = runDir;

                    world = server.getWorld(net.minecraft.registry.RegistryKey.of(
                        net.minecraft.registry.RegistryKeys.WORLD,
                        net.minecraft.util.Identifier.of(metadata.dimension())
                    ));
                    if (world == null) {
                        send("World not loaded for dimension: " + metadata.dimension());
                        stage = Stage.DONE;
                        break;
                    }

                    // User convention: slice_000 is the TOP and later indices are lower.
                    // Block restore: bottom -> top (last..0)
                    sliceIndex = metadata.sliceFiles().size() - 1;
                    send(String.format(Locale.ROOT,
                        "Undo run '%s' in %s (%d slice file(s)).",
                        metadata.runId(), metadata.dimension(), metadata.sliceFiles().size()
                    ));

                    // Keep protection active during undo so players cannot enter mid-restore.
                    // We'll unregister after the undo fully completes.
                    undoFullyCompleted = false;
                    protectionUnregistered = false;

                    undoCenter = new BlockPos(metadata.rawCenterX(), metadata.rawCenterY(), metadata.rawCenterZ());
                    activateFluidTickGuardFromRunBounds();
                    stage = Stage.CLEANUP_MOBS;
                }

                case CLEANUP_MOBS -> {
                    if (entitiesToProcess == null) {
                        send("Undo: removing mobs in build area...");
                        Box cleanup = EntityCleanup.cleanupBox(world, undoCenter, null, null, config.playerEjectMarginBlocks());
                        entitiesToProcess = EntityCleanup.collectMobs(world, cleanup);
                        entitiesProcessedIndex = 0;
                    }

                    entitiesProcessedIndex = EntityCleanup.discardSome(entitiesToProcess, entitiesProcessedIndex, scaledCap(config.maxEntitiesToProcessPerTick()));
                    if (entitiesProcessedIndex >= entitiesToProcess.size()) {
                        entitiesToProcess = null;
                        stage = Stage.CLEANUP_ITEMS;
                        waitTicks = config.delayBetweenStagesTicks();
                    }
                }

                case CLEANUP_ITEMS -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }

                    if (entitiesToProcess == null) {
                        send("Undo: removing dropped items in build area...");
                        Box cleanup = EntityCleanup.cleanupBox(world, undoCenter, null, null, config.playerEjectMarginBlocks());
                        entitiesToProcess = EntityCleanup.collectItemsAndXp(world, cleanup);
                        entitiesProcessedIndex = 0;
                    }

                    entitiesProcessedIndex = EntityCleanup.discardSome(entitiesToProcess, entitiesProcessedIndex, scaledCap(config.maxEntitiesToProcessPerTick()));
                    if (entitiesProcessedIndex >= entitiesToProcess.size()) {
                        entitiesToProcess = null;
                        send("Undo: restoring blocks (bottom -> top)...");
                        stage = Stage.LOAD_SLICE;
                    }
                }

                case LOAD_SLICE -> {
                    closeEditSession();
                    // Keep chunk tickets across slices (same as construct) to avoid unload/reload thrash
                    // when consecutive Y-slices touch the same X/Z chunks.
                    currentSlice = null;
                    blockPalette = null;
                    blockPaletteIsAir = null;
                    nbtPalette = null;
                    blockWithNbtCache.clear();
                    entryIndex = 0;
                    prepassEntryIndex = 0;
                    prepassFluidsCleared = 0;
                    lastPrepassProgressNanos = 0;
                    chunksToLoad = null;

                    int cfgFlush = config.undoFlushEveryBlocks();
                    undoFlushEveryBlocks = (cfgFlush > 0) ? cfgFlush : UNDO_FLUSH_EVERY_BLOCKS_DEFAULT;
                    undoSinceLastFlush = 0;

                    sliceAttempted = 0;
                    sliceApplied = 0;
                    sliceSkipped = 0;
                    sliceDeferred = 0;
                    sliceFailedFalse = 0;
                    sliceFailedException = 0;
                    lastProgressNanos = 0;
                    sliceSampleLogged = false;
                    applyPass = ApplyPass.NON_AIR;
                    waitTicks = 0;

                    if (sliceIndex < 0) {
                        // Deleting the undo folder can take 1-2s+ on some systems (many files).
                        // Do it off-thread so the main server tick never freezes.
                        if (pendingDelete == null) {
                            send("Undo complete. Removing undo history for run '" + metadata.runId() + "'...");
                            pendingDelete = CompletableFuture.supplyAsync(this::deleteUndoRunInternal, ioExecutor);
                            undoFullyCompleted = true;
                            stage = Stage.DELETE_RUN_ASYNC;
                        }
                        break;
                    }

                    Path runDir = (this.runDir != null) ? this.runDir : UndoPaths.runDir(metadata.runId());
                    String sliceFileName = metadata.sliceFiles().get(sliceIndex);
                    Path sliceFile = runDir.resolve(sliceFileName);

                    // If the server restarted mid-undo and we already completed this slice previously,
                    // the slice file may have been deleted. Treat that as "already done" and continue.
                    if (!Files.exists(sliceFile)) {
                        currentSliceFile = null;
                        sliceIndex--;
                        break;
                    }

                    currentSliceFile = sliceFile;

                    UndoSliceFile.UndoSliceDataIndexed data;
                    try {
                        data = UndoSliceFile.readIndexed(sliceFile);
                    } catch (Exception e) {
                        send("Failed to read undo slice " + sliceFileName + ": " + e.getMessage());
                        currentSliceFile = null;
                        sliceIndex--;
                        break;
                    }

                    if (!metadata.dimension().equals(data.dimension())) {
                        send("Undo slice dimension mismatch: expected " + metadata.dimension() + " got " + data.dimension());
                        sliceIndex--;
                        break;
                    }

                    currentSlice = data;
                    updateFluidTickGuardFromSliceBoundsIfNeeded();

                    com.sk89q.worldedit.world.World weWorld = FabricAdapter.adapt(world);
                    editSession = WorldEdit.getInstance().newEditSessionBuilder()
                        .world(weWorld)
                        .maxBlocks(-1)
                        .build();
                    editSession.setFastMode(true);
                    editSession.setSideEffectApplier(UNDO_SIDE_EFFECTS);

                    parserContext = new ParserContext();
                    parserContext.setWorld(weWorld);
                    parserContext.setExtent(weWorld);
                    parserContext.setActor(UNDO_ACTOR);

                    postFluidQueue = new LongArrayList();
                    postFluidIndex = 0;
                    postFluidUpdated = 0;
                    postFluidQueueDropped = 0;
                    lastPostFluidProgressNanos = 0;

                    // Restore phase: pre-parse palettes once per slice so the apply loop only does array lookups.
                    blockPalette = new BaseBlock[currentSlice.blocks().length];
                    blockPaletteIsAir = new boolean[currentSlice.blocks().length];
                    for (int i = 0; i < currentSlice.blocks().length; i++) {
                        BaseBlock parsed = parseBlockString(currentSlice.blocks()[i]);
                        // If parsing fails, keep null and skip those entries during apply.
                        blockPalette[i] = parsed;
                        blockPaletteIsAir[i] = parsed != null && parsed.getBlockType() == com.sk89q.worldedit.world.block.BlockTypes.AIR;
                    }

                    nbtPalette = new LinCompoundTag[currentSlice.nbts().length];
                    for (int i = 0; i < currentSlice.nbts().length; i++) {
                        nbtPalette[i] = parseNbt(currentSlice.nbts()[i]);
                    }

                    send("Undoing slice " + (sliceIndex + 1) + "/" + metadata.sliceFiles().size() + " (" + currentSlice.entryCount() + " blocks)...");

                    // Helpful for debugging: show the slice bounds so you can tell whether undo is
                    // currently working on the area you're looking at.
                    String boundsMsg = String.format(Locale.ROOT,
                        "Slice bounds: x=[%d..%d] y=[%d..%d] z=[%d..%d]",
                        currentSlice.minX(), currentSlice.maxX(),
                        currentSlice.minY(), currentSlice.maxY(),
                        currentSlice.minZ(), currentSlice.maxZ()
                    );
                    if (requesterId != null) {
                        ServerPlayerEntity p = server.getPlayerManager().getPlayer(requesterId);
                        if (p != null && p.getEntityWorld() == world) {
                            int px = p.getBlockX();
                            int pz = p.getBlockZ();
                            int dx = 0;
                            if (px < currentSlice.minX()) dx = currentSlice.minX() - px;
                            else if (px > currentSlice.maxX()) dx = px - currentSlice.maxX();

                            int dz = 0;
                            if (pz < currentSlice.minZ()) dz = currentSlice.minZ() - pz;
                            else if (pz > currentSlice.maxZ()) dz = pz - currentSlice.maxZ();

                            boundsMsg += String.format(Locale.ROOT, " (you are ~%d blocks away)", (int) Math.floor(Math.hypot(dx, dz)));
                        }
                    }
                    send(boundsMsg);

                    send("Undo: preloading chunks for this slice...");
                    stage = Stage.PRELOAD_CHUNKS;
                }

                case PRELOAD_CHUNKS -> {
                    if (world == null || currentSlice == null) {
                        stage = Stage.LOAD_SLICE;
                        break;
                    }

                    if (chunksToLoad == null) {
                        chunksToLoad = new ArrayDeque<>();
                        int minChunkX = currentSlice.minX() >> 4;
                        int maxChunkX = currentSlice.maxX() >> 4;
                        int minChunkZ = currentSlice.minZ() >> 4;
                        int maxChunkZ = currentSlice.maxZ() >> 4;
                        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                                chunksToLoad.addLast(new ChunkPos(cx, cz));
                            }
                        }
                    }

                    int maxToStart = scaledPositiveCap(config.maxChunksToLoadPerTick());
                    int started = 0;

                    while (System.nanoTime() < deadline && started < maxToStart && chunksToLoad != null && !chunksToLoad.isEmpty()) {
                        ChunkPos pos = chunksToLoad.pollFirst();
                        if (pos == null) {
                            break;
                        }

                        long key = ChunkPos.toLong(pos.x, pos.z);
                        if (chunkTicketKeys.contains(key)) {
                            continue;
                        }

                        try {
                            CompletableFuture<?> fut = world.getChunkManager().addChunkLoadingTicket(PRELOAD_TICKET_TYPE, pos, PRELOAD_TICKET_LEVEL);
                            chunkTicketKeys.add(key);
                            activeChunkLoads.put(key, fut);
                            started++;
                        } catch (Exception e) {
                            AtlantisMod.LOGGER.debug("Undo chunk preload ticket failed at {}: {}", pos, e.getMessage());
                        }
                    }

                    if (started > 0 && com.silver.atlantis.spawn.SpawnMobConfig.DIAGNOSTIC_LOGS) {
                        AtlantisMod.LOGGER.info(
                            "[UndoTickets] runId={} sliceIndex={} addedThisTick={} totalActiveLoads={} totalTicketKeys={}",
                            runId,
                            sliceIndex,
                            started,
                            activeChunkLoads.size(),
                            chunkTicketKeys.size()
                        );
                    }

                    if (!activeChunkLoads.isEmpty()) {
                        var it = activeChunkLoads.long2ObjectEntrySet().fastIterator();
                        while (it.hasNext()) {
                            var entry = it.next();
                            long key = entry.getLongKey();
                            CompletableFuture<?> fut = entry.getValue();
                            if (fut == null || !fut.isDone()) {
                                continue;
                            }

                            // Ticket completed, but chunk may not be visible as loaded yet; double-check.
                            int cx = ChunkPos.getPackedX(key);
                            int cz = ChunkPos.getPackedZ(key);
                            if (world.getChunkManager().getWorldChunk(cx, cz) == null) {
                                continue;
                            }

                            it.remove();
                        }
                    }

                    boolean startedAll = (chunksToLoad == null || chunksToLoad.isEmpty());
                    if (startedAll && activeChunkLoads.isEmpty()) {
                        send("Undo: clearing water/lava at recorded positions...");
                        stage = Stage.PREPASS_CLEAR_FLUIDS;
                    }
                }

                case WAIT_BETWEEN_SLICES -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }
                    stage = Stage.LOAD_SLICE;
                }

                case PREPASS_CLEAR_FLUIDS -> {
                    if (editSession == null || currentSlice == null || blockPalette == null || blockPaletteIsAir == null) {
                        stage = Stage.LOAD_SLICE;
                        break;
                    }

                    int[] xs = currentSlice.xs();
                    int[] ys = currentSlice.ys();
                    int[] zs = currentSlice.zs();
                    int[] blockIdx = currentSlice.blockIdx();

                    int cfgFlush = config.undoFlushEveryBlocks();
                    int flushEvery = (cfgFlush > 0) ? cfgFlush : UNDO_FLUSH_EVERY_BLOCKS_DEFAULT;
                    int sinceFlush = 0;

                    while (System.nanoTime() < deadline && prepassEntryIndex < xs.length) {
                        int i = prepassEntryIndex++;

                        int bIdx = blockIdx[i];
                        if (bIdx < 0 || bIdx >= blockPalette.length) {
                            continue;
                        }

                        // Efficiency: Only clear fluids where undo will restore AIR.
                        // If undo restores a solid block, water/lava will be overwritten anyway.
                        if (!blockPaletteIsAir[bIdx]) {
                            continue;
                        }

                        fluidPos.set(xs[i], ys[i], zs[i]);
                        var fluidState = world.getFluidState(fluidPos);
                        if (fluidState.isEmpty()) {
                            continue;
                        }

                        // Only clear water/lava (including flowing variants), and only at coordinates that are
                        // already part of this undo slice (so we don't drain surrounding ocean/river water).
                        if (!fluidState.isIn(FluidTags.WATER) && !fluidState.isIn(FluidTags.LAVA)) {
                            continue;
                        }

                        try {
                            boolean ok = editSession.setBlock(BlockVector3.at(xs[i], ys[i], zs[i]), WE_AIR);
                            if (ok) {
                                prepassFluidsCleared++;
                                sinceFlush++;
                                if (sinceFlush >= flushEvery) {
                                    try {
                                        editSession.flushSession();
                                        editSession.commit();
                                    } catch (Exception e) {
                                        AtlantisMod.LOGGER.debug("Undo prepass flush failed: {}", e.getMessage());
                                    }
                                    sinceFlush = 0;
                                }
                            }
                        } catch (Exception e) {
                            AtlantisMod.LOGGER.debug("Undo prepass failed at {},{},{}: {}", xs[i], ys[i], zs[i], e.getMessage());
                        }
                    }

                    if (prepassEntryIndex >= xs.length) {
                        try {
                            editSession.flushSession();
                            editSession.commit();
                        } catch (Exception e) {
                            AtlantisMod.LOGGER.debug("Undo prepass final flush failed: {}", e.getMessage());
                        }

                        entryIndex = 0;
                        applyPass = ApplyPass.NON_AIR;
                        stage = Stage.APPLY_SLICE;
                        break;
                    }

                    long now = System.nanoTime();
                    if (lastPrepassProgressNanos == 0) {
                        lastPrepassProgressNanos = now;
                    } else if (now - lastPrepassProgressNanos > 5_000_000_000L) {
                        lastPrepassProgressNanos = now;
                        send(String.format(Locale.ROOT,
                            "Undo fluid prepass: slice %d/%d scanned=%d/%d cleared=%d",
                            (sliceIndex + 1),
                            metadata != null ? metadata.sliceFiles().size() : -1,
                            prepassEntryIndex,
                            xs.length,
                            prepassFluidsCleared
                        ));
                    }
                }

                case APPLY_SLICE -> {
                    if (editSession == null || currentSlice == null || blockPalette == null || blockPaletteIsAir == null || nbtPalette == null) {
                        stage = Stage.LOAD_SLICE;
                        break;
                    }

                    int[] xs = currentSlice.xs();
                    int[] ys = currentSlice.ys();
                    int[] zs = currentSlice.zs();
                    int[] blockIdx = currentSlice.blockIdx();
                    int[] nbtIdx = currentSlice.nbtIdx();

                    while (System.nanoTime() < deadline && entryIndex < xs.length) {
                        int i = entryIndex++;

                        sliceAttempted++;

                        int bIdx = blockIdx[i];
                        BaseBlock base = (bIdx >= 0 && bIdx < blockPalette.length) ? blockPalette[bIdx] : null;
                        if (base == null) {
                            sliceSkipped++;
                            continue;
                        }

                        // Two-pass apply to avoid temporary holes that cause fluids to flow and falling blocks to drop.
                        // Pass 1: apply all non-air blocks.
                        // Pass 2: apply air blocks.
                        boolean isAir = blockPaletteIsAir[bIdx];
                        if (applyPass == ApplyPass.NON_AIR && isAir) {
                            sliceDeferred++;
                            continue;
                        }
                        if (applyPass == ApplyPass.AIR && !isAir) {
                            sliceDeferred++;
                            continue;
                        }

                        int nIdx = nbtIdx[i];
                        BaseBlock toSet;
                        if (nIdx < 0) {
                            toSet = base;
                        } else {
                            long key = (((long) bIdx) << 32) | (nIdx & 0xffffffffL);
                            toSet = blockWithNbtCache.computeIfAbsent(key, k -> {
                                if (nIdx >= nbtPalette.length) {
                                    return base;
                                }
                                LinCompoundTag tag = nbtPalette[nIdx];
                                if (tag == null) {
                                    return base;
                                }
                                return base.toBaseBlock(LazyReference.computed(tag));
                            });
                        }

                        try {
                            // Air pass optimization: if the target is AIR and the world is already air with no fluid,
                            // skip calling WorldEdit (which can be expensive and may trigger network updates).
                            if (applyPass == ApplyPass.AIR && isAir) {
                                fluidPos.set(xs[i], ys[i], zs[i]);
                                if (world.getBlockState(fluidPos).isAir() && world.getFluidState(fluidPos).isEmpty()) {
                                    sliceSkipped++;
                                    continue;
                                }
                            }

                            if (!sliceSampleLogged) {
                                sliceSampleLogged = true;
                                try {
                                    var weWorld = parserContext.getWorld();
                                    String before = weWorld.getFullBlock(BlockVector3.at(xs[i], ys[i], zs[i])).getAsString();
                                    String want = toSet.getAsString();
                                    send(String.format(Locale.ROOT,
                                        "Undo sample @ %d,%d,%d: before=%s -> want=%s",
                                        xs[i], ys[i], zs[i], before, want
                                    ));
                                } catch (Exception e) {
                                    AtlantisMod.LOGGER.warn("Failed to sample undo before-state: {}", e.getMessage());
                                }
                            }

                            boolean ok = editSession.setBlock(BlockVector3.at(xs[i], ys[i], zs[i]), toSet);
                            if (ok) {
                                if (postFluidQueue != null && (toSet.getBlockType() == BlockTypes.WATER || toSet.getBlockType() == BlockTypes.LAVA)) {
                                    if (postFluidQueue.size() < POST_FLUID_QUEUE_MAX) {
                                        postFluidQueue.add(BlockPos.asLong(xs[i], ys[i], zs[i]));
                                    } else {
                                        postFluidQueueDropped++;
                                    }
                                }
                                sliceApplied++;

                                undoSinceLastFlush++;
                                if (undoSinceLastFlush >= undoFlushEveryBlocks) {
                                    flushUndo(false, xs.length);
                                    undoSinceLastFlush = 0;
                                }
                            } else {
                                sliceFailedFalse++;
                            }

                        } catch (Exception e) {
                            sliceFailedException++;
                            AtlantisMod.LOGGER.warn("Failed to undo block at {},{},{}: {}", xs[i], ys[i], zs[i], e.getMessage());
                        }
                    }

                    long now = System.nanoTime();
                    if (lastProgressNanos == 0) {
                        lastProgressNanos = now;
                    } else if (now - lastProgressNanos > 5_000_000_000L) {
                        lastProgressNanos = now;
                        send(String.format(Locale.ROOT,
                            "Undo progress: slice %d/%d %d/%d applied=%d skipped=%d deferred=%d failed=%d (false=%d, ex=%d)",
                            (sliceIndex + 1),
                            metadata.sliceFiles().size(),
                            entryIndex,
                            xs.length,
                            sliceApplied,
                            sliceSkipped,
                            sliceDeferred,
                            (sliceFailedFalse + sliceFailedException),
                            sliceFailedFalse,
                            sliceFailedException
                        ));
                    }

                    if (entryIndex >= xs.length) {
                        if (undoSinceLastFlush > 0) {
                            flushUndo(true, xs.length);
                            undoSinceLastFlush = 0;
                        }

                        if (applyPass == ApplyPass.NON_AIR) {
                            // Second pass: apply air blocks.
                            applyPass = ApplyPass.AIR;
                            entryIndex = 0;
                            send("Undo pass 2/2: applying air blocks...");
                            break;
                        }

                        // After restoring blocks, force restored water/lava blocks to recompute.
                        postFluidIndex = 0;
                        postFluidUpdated = 0;
                        lastPostFluidProgressNanos = 0;
                        if (postFluidQueueDropped > 0) {
                            AtlantisMod.LOGGER.warn(
                                "Undo: dropped {} fluid positions from update queue (cap={}) to avoid tick lag.",
                                postFluidQueueDropped,
                                POST_FLUID_QUEUE_MAX
                            );
                        }

                        if (postFluidQueue != null && !postFluidQueue.isEmpty()) {
                            stage = Stage.POST_FORCE_FLUID_UPDATES;
                        } else {
                            // No restored fluids; continue to post-cleanup.
                            stage = Stage.POST_CLEANUP_ARTIFACTS;
                        }
                    }
                }

                case POST_FORCE_FLUID_UPDATES -> {
                    if (currentSlice == null) {
                        stage = Stage.LOAD_SLICE;
                        break;
                    }

                    if (postFluidQueue == null || postFluidIndex >= postFluidQueue.size()) {
                        // Done; clear and move on to artifact cleanup.
                        postFluidQueue = null;
                        postFluidIndex = 0;
                        postFluidUpdated = 0;
                        stage = Stage.POST_CLEANUP_ARTIFACTS;
                        break;
                    }

                    int maxPerTick = scaledCap(config.maxFluidNeighborUpdatesPerTick());
                    if (maxPerTick <= 0) {
                        // Disabled; skip the pulse stage.
                        postFluidIndex = postFluidQueue.size();
                        break;
                    }

                    int updatedThisTick = 0;
                    while (System.nanoTime() < deadline && postFluidIndex < postFluidQueue.size() && updatedThisTick < maxPerTick) {
                        long packed = postFluidQueue.getLong(postFluidIndex++);
                        fluidPos.set(packed);

                        var state = world.getBlockState(fluidPos);
                        if (!(state.isOf(Blocks.WATER) || state.isOf(Blocks.LAVA))) {
                            continue;
                        }

                        var fluidState = state.getFluidState();
                        if (fluidState.isEmpty()) {
                            continue;
                        }

                        // Only poke "edge" fluids (adjacent to non-water/lava) to avoid scheduling
                        // ticks for massive interior oceans, which creates long-lasting tick lag.
                        boolean isWater = state.isOf(Blocks.WATER);
                        boolean touchesNonFluid = false;
                        for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN}) {
                            neighborPos.set(fluidPos).move(d);
                            var nFluid = world.getFluidState(neighborPos);
                            if (isWater) {
                                if (!nFluid.isIn(FluidTags.WATER)) {
                                    touchesNonFluid = true;
                                    break;
                                }
                            } else {
                                if (!nFluid.isIn(FluidTags.LAVA)) {
                                    touchesNonFluid = true;
                                    break;
                                }
                            }
                        }

                        if (!touchesNonFluid) {
                            continue;
                        }

                        if (!UndoFluidTickGuard.INSTANCE.shouldSuppress(world, fluidPos)) {
                            world.scheduleFluidTick(fluidPos, fluidState.getFluid(), 1);
                        }
                        updatedThisTick++;
                        postFluidUpdated++;
                    }

                    long now = System.nanoTime();
                    if (lastPostFluidProgressNanos == 0) {
                        lastPostFluidProgressNanos = now;
                    } else if (now - lastPostFluidProgressNanos > 5_000_000_000L) {
                        lastPostFluidProgressNanos = now;
                        send(String.format(Locale.ROOT,
                            "Undo fluid update pulse: slice %d/%d queued=%d updated=%d",
                            (sliceIndex + 1),
                            metadata != null ? metadata.sliceFiles().size() : -1,
                            postFluidQueue != null ? postFluidQueue.size() : 0,
                            postFluidUpdated
                        ));
                    }
                }

                case POST_CLEANUP_ARTIFACTS -> {
                    if (editSession == null || currentSlice == null) {
                        stage = Stage.LOAD_SLICE;
                        break;
                    }

                    if (!cleanupInit) {
                        cleanupInit = true;
                        cleanupX = currentSlice.minX();
                        cleanupY = currentSlice.minY();
                        cleanupZ = currentSlice.minZ();
                        cleanupScanned = 0;
                        cleanupRemoved = 0;
                        lastCleanupProgressNanos = 0;
                        cleanupFluidQueue = new LongArrayList();
                        cleanupFluidIndex = 0;
                    }

                    int removedThisTick = 0;
                    int maxArtifactRemovalsThisTick = scaledPositiveCap(MAX_ARTIFACT_REMOVALS_PER_TICK);
                    while (System.nanoTime() < deadline && cleanupX <= currentSlice.maxX()) {
                        cleanupPos.set(cleanupX, cleanupY, cleanupZ);
                        cleanupScanned++;

                        var state = world.getBlockState(cleanupPos);
                        boolean shouldRemove = false;

                        // Vines that grew after paste (not recorded in undo) can linger.
                        if (state.isOf(Blocks.VINE)
                            || state.isOf(Blocks.WEEPING_VINES)
                            || state.isOf(Blocks.WEEPING_VINES_PLANT)
                            || state.isOf(Blocks.TWISTING_VINES)
                            || state.isOf(Blocks.TWISTING_VINES_PLANT)
                            || state.isOf(Blocks.CAVE_VINES)
                            || state.isOf(Blocks.CAVE_VINES_PLANT)) {
                            shouldRemove = true;
                        }

                        // Lava flowing into water can create cobble/stone/obsidian in mid-water.
                        // Those blocks were not part of the schematic and won't be in undo.
                        if (!shouldRemove
                            && (state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.STONE) || state.isOf(Blocks.OBSIDIAN))
                            && world.getFluidState(cleanupPos.down()).isIn(FluidTags.WATER)) {
                            int waterSides = 0;
                            for (Direction d : Direction.Type.HORIZONTAL) {
                                if (world.getFluidState(cleanupPos.offset(d)).isIn(FluidTags.WATER)) {
                                    waterSides++;
                                }
                            }
                            if (waterSides >= 3) {
                                shouldRemove = true;
                            }
                        }

                        if (shouldRemove) {
                            try {
                                boolean ok = editSession.setBlock(BlockVector3.at(cleanupX, cleanupY, cleanupZ), WE_AIR);
                                if (ok) {
                                    cleanupRemoved++;

                                    // Queue nearby fluid blocks so water fills in immediately.
                                    for (Direction d : Direction.values()) {
                                        neighborPos.set(cleanupPos).move(d);
                                        if (world.getFluidState(neighborPos).isIn(FluidTags.WATER) || world.getFluidState(neighborPos).isIn(FluidTags.LAVA)) {
                                            cleanupFluidQueue.add(BlockPos.asLong(neighborPos.getX(), neighborPos.getY(), neighborPos.getZ()));
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                AtlantisMod.LOGGER.debug("Undo cleanup failed at {},{},{}: {}", cleanupX, cleanupY, cleanupZ, e.getMessage());
                            }

                            removedThisTick++;
                            if (removedThisTick >= maxArtifactRemovalsThisTick) {
                                break;
                            }
                        }

                        // Advance cursor (z -> y -> x)
                        cleanupZ++;
                        if (cleanupZ > currentSlice.maxZ()) {
                            cleanupZ = currentSlice.minZ();
                            cleanupY++;
                            if (cleanupY > currentSlice.maxY()) {
                                cleanupY = currentSlice.minY();
                                cleanupX++;
                            }
                        }
                    }

                    if (removedThisTick > 0) {
                        try {
                            editSession.flushSession();
                            editSession.commit();
                        } catch (Exception e) {
                            AtlantisMod.LOGGER.debug("Undo cleanup flush failed: {}", e.getMessage());
                        }
                    }

                    long now = System.nanoTime();
                    if (lastCleanupProgressNanos == 0) {
                        lastCleanupProgressNanos = now;
                    } else if (now - lastCleanupProgressNanos > 5_000_000_000L) {
                        lastCleanupProgressNanos = now;
                        send(String.format(Locale.ROOT,
                            "Undo cleanup: slice %d/%d scanned=%d removed=%d",
                            (sliceIndex + 1),
                            metadata != null ? metadata.sliceFiles().size() : -1,
                            cleanupScanned,
                            cleanupRemoved
                        ));
                    }

                    if (cleanupX > currentSlice.maxX()) {
                        // Done scanning this slice.
                        cleanupInit = false;
                        cleanupScanned = 0;
                        cleanupRemoved = 0;
                        lastCleanupProgressNanos = 0;

                        if (cleanupFluidQueue != null && !cleanupFluidQueue.isEmpty()) {
                            cleanupFluidIndex = 0;
                            stage = Stage.POST_CLEANUP_FORCE_FLUID_TICKS;
                        } else {
                            cleanupFluidQueue = null;
                            cleanupFluidIndex = 0;

                            closeEditSession();

                            // Slice is fully complete (restore + post cleanup). Delete its undo file so a restart
                            // won't redo work.
                            deleteCurrentSliceFileAsync();

                            sliceIndex--;
                            waitTicks = Math.max(0, config.delayBetweenSlicesTicks());
                            stage = Stage.WAIT_BETWEEN_SLICES;
                        }
                    }
                }

                case POST_CLEANUP_FORCE_FLUID_TICKS -> {
                    if (currentSlice == null) {
                        stage = Stage.LOAD_SLICE;
                        break;
                    }

                    if (cleanupFluidQueue == null || cleanupFluidIndex >= cleanupFluidQueue.size()) {
                        cleanupFluidQueue = null;
                        cleanupFluidIndex = 0;

                        closeEditSession();

                        // Slice is fully complete (restore + cleanup + forced fluid ticks). Delete its undo file so
                        // a restart won't redo work.
                        deleteCurrentSliceFileAsync();

                        sliceIndex--;
                        waitTicks = Math.max(0, config.delayBetweenSlicesTicks());
                        stage = Stage.WAIT_BETWEEN_SLICES;
                        break;
                    }

                    int maxPerTick = scaledCap(config.maxFluidNeighborUpdatesPerTick());
                    if (maxPerTick <= 0) {
                        cleanupFluidIndex = cleanupFluidQueue.size();
                        break;
                    }

                    int updatedThisTick = 0;
                    while (System.nanoTime() < deadline && cleanupFluidIndex < cleanupFluidQueue.size() && updatedThisTick < maxPerTick) {
                        long packed = cleanupFluidQueue.getLong(cleanupFluidIndex++);
                        fluidPos.set(packed);

                        var state = world.getBlockState(fluidPos);
                        var fluidState = state.getFluidState();
                        if (!fluidState.isEmpty() && !UndoFluidTickGuard.INSTANCE.shouldSuppress(world, fluidPos)) {
                            fluidState.onScheduledTick(world, fluidPos, state);
                            updatedThisTick++;
                        }
                    }
                }

                case DELETE_RUN_ASYNC -> {
                    if (pendingDelete == null) {
                        stage = Stage.DONE;
                        break;
                    }

                    if (!pendingDelete.isDone()) {
                        // Keep ticking; deletion is happening on the IO executor.
                        return false;
                    }

                    try {
                        DeleteResult result = pendingDelete.join();
                        send(result.message);
                    } catch (Exception e) {
                        AtlantisMod.LOGGER.warn("Failed to delete undo run async: {}", e.getMessage());
                        send("Failed to remove undo history for run '" + (metadata != null ? metadata.runId() : "?") + "': " + e.getMessage());
                    } finally {
                        pendingDelete = null;
                    }

                    // Now that undo is fully complete, remove the protection entry.
                    if (!protectionUnregistered && undoFullyCompleted && metadata != null) {
                        ProtectionManager.INSTANCE.unregister(metadata.runId());
                        protectionUnregistered = true;
                    }

                    stage = Stage.DONE;
                }

                case DONE -> {
                    closeEditSession();
                    releaseChunkTickets();

                    // Safety net: if we completed but didn't hit DELETE_RUN_ASYNC for some reason, unregister here.
                    if (!protectionUnregistered && undoFullyCompleted && metadata != null) {
                        ProtectionManager.INSTANCE.unregister(metadata.runId());
                        protectionUnregistered = true;
                    }
                    return true;
                }
            }
        }

        if (stage == Stage.DONE) {
            deactivateFluidTickGuard();
        }
        return stage == Stage.DONE;
    }

    private void activateFluidTickGuardFromRunBounds() {
        if (world == null || metadata == null) {
            return;
        }

        Path resolvedRunDir = (runDir != null) ? runDir : UndoPaths.runDir(metadata.runId());
        Path stateFile = ConstructStatePaths.stateFile(resolvedRunDir);
        if (!Files.exists(stateFile)) {
            return;
        }

        try {
            ConstructRunState state = ConstructRunStateIO.read(stateFile);
            if (!metadata.dimension().equals(state.dimension())) {
                return;
            }

            Integer minX = state.overallMinX();
            Integer minY = state.overallMinY();
            Integer minZ = state.overallMinZ();
            Integer maxX = state.overallMaxX();
            Integer maxY = state.overallMaxY();
            Integer maxZ = state.overallMaxZ();
            if (minX == null || minY == null || minZ == null || maxX == null || maxY == null || maxZ == null) {
                return;
            }

            UndoFluidTickGuard.INSTANCE.activate(
                world.getRegistryKey().getValue().toString(),
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ
            );
            fluidTickGuardActive = true;
            fluidTickGuardUsingRunBounds = true;
            AtlantisMod.LOGGER.info(
                "Undo fluid guard active (run bounds): dim={} x=[{}..{}] y=[{}..{}] z=[{}..{}]",
                world.getRegistryKey().getValue(),
                Math.min(minX, maxX),
                Math.max(minX, maxX),
                Math.min(minY, maxY),
                Math.max(minY, maxY),
                Math.min(minZ, maxZ),
                Math.max(minZ, maxZ)
            );
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to activate undo fluid tick guard from run state {}: {}", stateFile, e.getMessage());
        }
    }

    private void updateFluidTickGuardFromSliceBoundsIfNeeded() {
        if (fluidTickGuardUsingRunBounds || world == null || currentSlice == null) {
            return;
        }

        UndoFluidTickGuard.INSTANCE.activate(
            world.getRegistryKey().getValue().toString(),
            currentSlice.minX(),
            currentSlice.minY(),
            currentSlice.minZ(),
            currentSlice.maxX(),
            currentSlice.maxY(),
            currentSlice.maxZ()
        );
        fluidTickGuardActive = true;
        AtlantisMod.LOGGER.info(
            "Undo fluid guard active (slice bounds): dim={} x=[{}..{}] y=[{}..{}] z=[{}..{}]",
            world.getRegistryKey().getValue(),
            Math.min(currentSlice.minX(), currentSlice.maxX()),
            Math.max(currentSlice.minX(), currentSlice.maxX()),
            Math.min(currentSlice.minY(), currentSlice.maxY()),
            Math.max(currentSlice.minY(), currentSlice.maxY()),
            Math.min(currentSlice.minZ(), currentSlice.maxZ()),
            Math.max(currentSlice.minZ(), currentSlice.maxZ())
        );
    }

    private void deactivateFluidTickGuard() {
        if (!fluidTickGuardActive) {
            return;
        }

        UndoFluidTickGuard.INSTANCE.clear();
        fluidTickGuardActive = false;
        fluidTickGuardUsingRunBounds = false;
        AtlantisMod.LOGGER.info("Undo fluid guard cleared.");
    }

    private void updateAdaptiveWorkScale(long tickStartNanos) {
        if (lastTickStartNanos <= 0L) {
            lastTickStartNanos = tickStartNanos;
            return;
        }

        long expectedTickNanos = Math.max(1L, config.expectedTickNanos());
        long observedTickNanos = tickStartNanos - lastTickStartNanos;
        lastTickStartNanos = tickStartNanos;

        if (observedTickNanos <= 0L) {
            return;
        }

        double rawScale = (double) expectedTickNanos / (double) observedTickNanos;
        rawScale = clamp(rawScale, config.adaptiveScaleMin(), config.adaptiveScaleMax());

        double smoothing = clamp(config.adaptiveScaleSmoothing(), 0.0d, 1.0d);
        adaptiveWorkScale = adaptiveWorkScale + ((rawScale - adaptiveWorkScale) * smoothing);
        adaptiveWorkScale = clamp(adaptiveWorkScale, config.adaptiveScaleMin(), config.adaptiveScaleMax());
    }

    private long scaledBudget(long baseBudgetNanos) {
        long safeBase = Math.max(250_000L, baseBudgetNanos);
        return Math.max(250_000L, Math.round(safeBase * adaptiveWorkScale));
    }

    private int scaledCap(int basePerTick) {
        if (basePerTick <= 0) {
            return basePerTick;
        }
        return Math.max(1, (int) Math.round(basePerTick * adaptiveWorkScale));
    }

    private int scaledPositiveCap(int basePerTick) {
        return Math.max(1, scaledCap(basePerTick));
    }

    private static double clamp(double value, double min, double max) {
        if (min > max) {
            return value;
        }
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }

    @Override
    public void onError(MinecraftServer server, Throwable throwable) {
        send("Undo crashed: " + throwable.getClass().getSimpleName() + ": " + (throwable.getMessage() != null ? throwable.getMessage() : "(no message)"));
        AtlantisMod.LOGGER.error("Undo crashed", throwable);

        // Best-effort cleanup
        closeEditSession();
        releaseChunkTickets();
        deactivateFluidTickGuard();
    }

    @Override
    public void onCancel(MinecraftServer server, String reason) {
        send("Undo cancelled" + (reason != null && !reason.isBlank() ? (": " + reason) : "."));
        closeEditSession();
        releaseChunkTickets();
        deactivateFluidTickGuard();
    }

    private void releaseChunkTickets() {
        if (world == null || chunkTicketKeys.isEmpty()) {
            activeChunkLoads.clear();
            chunkTicketKeys.clear();
            return;
        }

        int ticketCount = chunkTicketKeys.size();

        try {
            for (long key : chunkTicketKeys) {
                int cx = ChunkPos.getPackedX(key);
                int cz = ChunkPos.getPackedZ(key);
                world.getChunkManager().removeTicket(PRELOAD_TICKET_TYPE, new ChunkPos(cx, cz), PRELOAD_TICKET_LEVEL);
            }
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to release undo chunk tickets: {}", e.getMessage());
        } finally {
            if (com.silver.atlantis.spawn.SpawnMobConfig.DIAGNOSTIC_LOGS) {
                AtlantisMod.LOGGER.info(
                    "[UndoTickets] runId={} released={} remainingActiveLoadsBeforeClear={}",
                    runId,
                    ticketCount,
                    activeChunkLoads.size()
                );
            }
            activeChunkLoads.clear();
            chunkTicketKeys.clear();
        }
    }

    private BaseBlock parseBlockString(String blockString) {
        // Legacy undo entries may have been recorded via `BaseBlock.getAsString()` which can
        // include inline NBT (e.g. `{components:{}}`). WorldEdit's block parser does not accept
        // that format, and we store NBT separately anyway.
        String sanitized = blockString;
        int nbtStart = sanitized.indexOf('{');
        if (nbtStart >= 0) {
            sanitized = sanitized.substring(0, nbtStart).trim();
        }

        BaseBlock cached = parsedBlockStringCache.get(sanitized);
        if (cached != null) {
            return cached;
        }

        BaseBlock base;
        try {
            base = WorldEdit.getInstance().getBlockFactory().parseFromInput(sanitized, parserContext);
        } catch (InputParseException e) {
            AtlantisMod.LOGGER.warn("Failed to parse undo block '{}': {}", sanitized, e.getMessage());
            return null;
        }

        parsedBlockStringCache.put(sanitized, base);
        return base;
    }

    private void deleteCurrentSliceFileAsync() {
        Path file = currentSliceFile;
        currentSliceFile = null;
        if (file == null) {
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("Failed to delete undo slice file {}: {}", file, e.getMessage());
            }
        }, ioExecutor);
    }

    private LinCompoundTag parseNbt(String nbtSnbt) {
        if (nbtSnbt == null || nbtSnbt.isBlank()) {
            return null;
        }

        String raw = nbtSnbt.trim();

        // Some legacy strings may have been persisted as a quoted SNBT payload.
        String unquoted = raw;
        if ((unquoted.startsWith("\"") && unquoted.endsWith("\""))
            || (unquoted.startsWith("'") && unquoted.endsWith("'"))) {
            if (unquoted.length() >= 2) {
                unquoted = unquoted.substring(1, unquoted.length() - 1);
            }
        }
        unquoted = unquoted.trim();

        // Common case: many vanilla block-entity NBTs include an empty `components:{}` field.
        // Some SNBT parsers are picky about it, and it's redundant when empty.
        // Remove only the *empty* form to avoid changing real data.
        if (unquoted.contains("components:{}")) {
            String cleaned = unquoted
                .replace(",components:{}", "")
                .replace("components:{},", "")
                .replace("components:{}", "");

            // Cleanup a possible trailing comma left behind.
            cleaned = cleaned.replaceAll(",\s*}", "}");
            unquoted = cleaned.trim();
        }

        // Candidate strings to try, in order.
        // - If it already looks like a compound, parse directly.
        // - If it looks like a compound *body* (e.g. "Items:[...]"), wrap with braces.
        // - If it's a list root (starts with '['), we can't apply it as a block-entity compound.
        //
        // NOTE: Some SNBT emitters use single quotes around strings; LinTagReader is stricter in
        // some cases, so we also try a conservative single-quote -> double-quote variant.
        String candidate0;
        String candidate1;
        if (unquoted.startsWith("{")) {
            candidate0 = unquoted;
            candidate1 = unquoted.replace('\'', '"');
        } else if (unquoted.startsWith("[")) {
            // Not a compound root; ignore.
            candidate0 = null;
            candidate1 = null;
        } else {
            candidate0 = "{" + unquoted + "}";
            candidate1 = candidate0.replace('\'', '"');
        }

        String[] candidates;
        if (candidate0 == null) {
            candidates = new String[] { };
        } else if (candidate0.equals(candidate1)) {
            candidates = new String[] { candidate0 };
        } else {
            candidates = new String[] { candidate0, candidate1 };
        }

        for (String candidate : candidates) {
            LinCompoundTag cached = parsedNbtCache.get(candidate);
            if (cached != null) {
                return cached;
            }

            try {
                LinCompoundTag tag = LinTagReader.readCompound(LinStringIO.readFromString(candidate));
                parsedNbtCache.put(candidate, tag);
                return tag;
            } catch (Exception ignored) {
                // Try next candidate.
            }
        }

        // Log once per *normalized* key (prevents log spam when the only differences are seeds, etc.).
        String logKey = normalizeBadNbtKey(raw);
        if (badNbtLogged.add(logKey)) {
            String preview = raw;
            if (preview.length() > 220) {
                preview = preview.substring(0, 220) + "...";
            }
            AtlantisMod.LOGGER.warn("Failed to parse undo NBT (len={}): preview='{}'", raw.length(), preview);
        }
        return null;
    }

    private static String normalizeBadNbtKey(String raw) {
        // Try to group by common stable fields so we don't spam logs for different LootTableSeed values.
        String s = raw;

        // Collapse digits to reduce uniqueness.
        s = s.replaceAll("[0-9]+", "#");

        // Prefer block entity id if present.
        int idIdx = s.indexOf("id:");
        if (idIdx >= 0) {
            int end = Math.min(s.length(), idIdx + 64);
            return "id=" + s.substring(idIdx, end);
        }

        // Prefer loot table id if present.
        int lootIdx = s.indexOf("LootTable:");
        if (lootIdx >= 0) {
            int end = Math.min(s.length(), lootIdx + 96);
            return "loot=" + s.substring(lootIdx, end);
        }

        // Fallback to a short prefix.
        if (s.length() > 96) {
            s = s.substring(0, 96);
        }
        return s;
    }

    private static final class SystemActor implements Actor {

        private static final SessionKey SESSION_KEY = new SessionKey() {
            private final UUID id = UUID.nameUUIDFromBytes("Atlantis-Construct-Undo".getBytes());

            @Override
            public UUID getUniqueId() {
                return id;
            }

            @Override
            public String getName() {
                return "AtlantisUndo";
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public boolean isPersistent() {
                return false;
            }
        };

        @Override
        public UUID getUniqueId() {
            return SESSION_KEY.getUniqueId();
        }

        @Override
        public SessionKey getSessionKey() {
            return SESSION_KEY;
        }

        @Override
        public String getName() {
            return "AtlantisUndo";
        }

        @Override
        public void printRaw(String msg) {
        }

        @Override
        public void printDebug(String msg) {
        }

        @Override
        public void print(String msg) {
        }

        @Override
        public void printError(String msg) {
        }

        @Override
        public void print(com.sk89q.worldedit.util.formatting.text.Component component) {
        }

        @Override
        public boolean canDestroyBedrock() {
            return true;
        }

        @Override
        public boolean isPlayer() {
            return false;
        }

        @Override
        public File openFileOpenDialog(String[] extensions) {
            return null;
        }

        @Override
        public File openFileSaveDialog(String[] extensions) {
            return null;
        }

        @Override
        public void dispatchCUIEvent(CUIEvent event) {
        }

        @Override
        public Locale getLocale() {
            return Locale.ROOT;
        }

        @Override
        public String[] getGroups() {
            return new String[0];
        }

        @Override
        public void checkPermission(String permission) {
        }

        @Override
        public boolean hasPermission(String permission) {
            return true;
        }
    }

    private void closeEditSession() {
        if (editSession != null) {
            long tFlush = 0;
            long tCommit = 0;
            long tClose = 0;
            try {
                long t0 = System.nanoTime();
                editSession.flushSession();
                tFlush = System.nanoTime() - t0;
            } catch (Exception ignored) {
            }
            try {
                long t0 = System.nanoTime();
                editSession.commit();
                tCommit = System.nanoTime() - t0;
            } catch (Exception ignored) {
            }
            try {
                long t0 = System.nanoTime();
                editSession.close();
                tClose = System.nanoTime() - t0;
            } catch (Exception ignored) {
            }

            if (tFlush > SLOW_OP_NANOS || tCommit > SLOW_OP_NANOS || tClose > SLOW_OP_NANOS) {
                maybeLogSlow(String.format(Locale.ROOT,
                    "Undo closeEditSession slow: flush=%.1fms commit=%.1fms close=%.1fms (slice=%d/%d)",
                    tFlush / 1_000_000.0,
                    tCommit / 1_000_000.0,
                    tClose / 1_000_000.0,
                    (sliceIndex + 1),
                    metadata != null ? metadata.sliceFiles().size() : -1
                ));
            }
            editSession = null;
        }
    }

    private void maybeLogSlow(String message) {
        long now = System.nanoTime();
        if (lastSlowLogNanos != 0 && (now - lastSlowLogNanos) < SLOW_LOG_COOLDOWN_NANOS) {
            return;
        }
        lastSlowLogNanos = now;
        AtlantisMod.LOGGER.warn(message);
    }

    private void flushUndo(boolean force, int totalEntriesInSlice) {
        if (editSession == null) {
            return;
        }

        // We always flush+commit as a pair. Even when not "force", this is only called
        // on a block-count threshold, not every tick.
        try {
            long t0 = System.nanoTime();
            editSession.flushSession();
            long dt = System.nanoTime() - t0;
            if (dt > SLOW_OP_NANOS) {
                maybeLogSlow(String.format(Locale.ROOT,
                    "Undo flushSession() slow: %.1fms (stage=%s pass=%s slice=%d/%d entry=%d/%d)",
                    dt / 1_000_000.0,
                    stage,
                    applyPass,
                    (sliceIndex + 1),
                    metadata != null ? metadata.sliceFiles().size() : -1,
                    entryIndex,
                    totalEntriesInSlice
                ));
            }

            long c0 = System.nanoTime();
            editSession.commit();
            long cdt = System.nanoTime() - c0;
            if (cdt > SLOW_OP_NANOS) {
                maybeLogSlow(String.format(Locale.ROOT,
                    "Undo commit() slow: %.1fms (stage=%s pass=%s slice=%d/%d)%s",
                    cdt / 1_000_000.0,
                    stage,
                    applyPass,
                    (sliceIndex + 1),
                    metadata != null ? metadata.sliceFiles().size() : -1,
                    force ? " (force)" : ""
                ));
            }
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Undo flush failed: {}", e.getMessage());
        }
    }

    private record DeleteResult(boolean ok, String message) {}

    private DeleteResult deleteUndoRunInternal() {
        if (metadata == null) {
            return new DeleteResult(false, "Failed to remove undo history: missing metadata.");
        }

        Path dir = (runDir != null) ? runDir : UndoPaths.runDir(metadata.runId());
        try {
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            AtlantisMod.LOGGER.warn("Failed to delete undo path {}: {}", p, e.getMessage());
                        }
                    });
                }
            }

            Path base = UndoPaths.undoBaseDir();
            // Intentionally do not maintain a latest.txt pointer; undo history is discovered by scanning directories.

            return new DeleteResult(true, "Undo history removed for run '" + metadata.runId() + "'.");
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to delete undo run {}: {}", metadata.runId(), e.getMessage());
            return new DeleteResult(false, "Failed to remove undo history for run '" + metadata.runId() + "': " + e.getMessage());
        }
    }

    private void send(String message) {
        if (requesterId != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requesterId);
            if (player != null) {
                player.sendMessage(Text.literal(message), false);
                return;
            }
        }

        AtlantisMod.LOGGER.info(message);
    }
}
