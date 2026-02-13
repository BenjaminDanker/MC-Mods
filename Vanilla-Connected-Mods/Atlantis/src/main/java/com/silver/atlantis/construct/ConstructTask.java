package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.undo.UndoEntry;
import com.silver.atlantis.construct.undo.UndoMetadataIO;
import com.silver.atlantis.construct.undo.UndoPaths;
import com.silver.atlantis.construct.undo.UndoRunMetadata;
import com.silver.atlantis.construct.undo.UndoSliceFile;
import com.silver.atlantis.construct.state.ConstructRunState;
import com.silver.atlantis.construct.state.ConstructRunStateIO;
import com.silver.atlantis.construct.state.ConstructStatePaths;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.fabric.FabricAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.concurrency.LazyReference;
import com.sk89q.worldedit.util.SideEffect;
import com.sk89q.worldedit.util.SideEffectSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;

import com.sk89q.worldedit.world.block.BlockTypes;
import com.sk89q.worldedit.world.block.BaseBlock;
import com.silver.atlantis.protect.ProtectionCollector;
import com.silver.atlantis.protect.ProtectionFileIO;
import com.silver.atlantis.protect.ProtectionManager;
import com.silver.atlantis.protect.ProtectionPaths;
import org.enginehub.linbus.format.snbt.LinStringIO;
import org.enginehub.linbus.tree.LinCompoundTag;

import java.nio.file.Files;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class ConstructTask implements ConstructJob {

    // Construct is intentionally single-pass (like manual `//paste -a`).
    // Fluids (water/lava) are pasted as normal blocks; no extra fluid recompute pulses.

    private enum Stage {
        SCAN_SLICES,
        LOAD_SLICE_ASYNC,
        PRELOAD_CHUNKS,
        PASTE,
        CLEAR_BARRIERS,
        WAIT_BETWEEN_SLICES,
        CLEANUP_MOBS,
        CLEANUP_ITEMS,
        DONE
    }

    private final UUID requesterId;
    private final ConstructConfig config;
    private final ServerWorld world;
    private final BlockPos targetCenter;
    private final Executor ioExecutor;

    // Persistent construct progress (so a restart can resume without starting over)
    private final Path runStateFile;
    private boolean runCompleted;

    // Protection bookkeeping (filled during paste; registered on completion)
    private final ProtectionCollector protectionCollector;
    private boolean protectionRegistered;

    // Undo persistence (only for edits performed by this mod)
    private final boolean undoEnabled;
    private final String undoRunId;
    private final Path undoRunDir;
    private final List<String> undoSliceFiles = new ArrayList<>();
    private Long2ObjectOpenHashMap<UndoEntry> undoThisSlice = new Long2ObjectOpenHashMap<>();

    // Computed once from the first slice so the overall build is centered on targetCenter.
    // Reused for all slices to preserve their relative offsets.
    private BlockVector3 pasteAnchorTo;

    // Union of all slice regions in world coordinates.
    private BlockVector3 overallMin;
    private BlockVector3 overallMax;

    private Stage stage = Stage.SCAN_SLICES;

    private List<SchematicSlice> slices;
    private int sliceIndex = 0;

    private CompletableFuture<Clipboard> pendingClipboard;

    private EditSession pasteEditSession;
    private com.sk89q.worldedit.world.World pasteWeWorld;
    private Clipboard pasteClipboard;
    private BlockVector3 pasteShift;
    private int pasteMinX;
    private int pasteMaxX;
    private int pasteMinY;
    private int pasteMaxY;
    private int pasteMinZ;
    private int pasteMaxZ;
    private int pasteX;
    private int pasteY;
    private int pasteZ;
    private String pasteSliceName;
    private int pasteSinceLastFlush;
    private int pasteFlushEveryBlocks;

    // Barrier clearing after paste (keeps barriers solid during paste so fluids don't invade mid-build).
    private boolean sawBarrierThisRun;
    private boolean barrierClearInitialized;
    private int barrierMinX;
    private int barrierMaxX;
    private int barrierMinY;
    private int barrierMaxY;
    private int barrierMinZ;
    private int barrierMaxZ;
    private int barrierX;
    private int barrierY;
    private int barrierZ;
    private int barrierSinceLastFlush;
    private final BlockPos.Mutable barrierScanPos = new BlockPos.Mutable();

    // Note: fluid-specific update queues are intentionally not used by construct.


    private final Set<Long> loadedChunkKeys = new HashSet<>();
    private final Long2ObjectOpenHashMap<CompletableFuture<?>> activeChunkLoads = new Long2ObjectOpenHashMap<>();
    private final Set<Long> chunkTicketKeys = new HashSet<>();

    private static final ChunkTicketType PRELOAD_TICKET_TYPE = ChunkTicketType.FORCED;
    private static final int PRELOAD_TICKET_LEVEL = 2;

    // Make paste look incremental: send updates to clients and keep POI consistent,
    // while avoiding heavy neighbor/physics effects.
    private static final SideEffectSet PASTE_SIDE_EFFECTS = SideEffectSet.none()
        .with(SideEffect.NETWORK, SideEffect.State.ON)
        .with(SideEffect.POI_UPDATE, SideEffect.State.ON);

    private static final int PASTE_FLUSH_EVERY_BLOCKS_DEFAULT = 8192;

    private List<? extends Entity> entitiesToProcess;
    private int entitiesProcessedIndex;

    private Deque<ChunkPos> chunksToLoad;

    private int waitTicks = 0;

    // Last known valid X/Z where a player was outside the protected construct area.
    // Used to return them to where they came from before trying generic fallback destinations.
    private final Map<UUID, BlockPos> lastKnownOutsideByPlayer = new HashMap<>();

    // Progress logging (useful when the command invoker disconnects).
    private long lastProgressLogNanos;
    private long lastTickStartNanos;
    private double adaptiveWorkScale = 1.0d;

    ConstructTask(ServerCommandSource source, ConstructConfig config, ServerWorld world, BlockPos targetCenter, Executor ioExecutor) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = config;
        this.world = world;
        this.targetCenter = targetCenter;
        this.ioExecutor = ioExecutor;

        send(source.getServer(), "Construct started. /findflat center: x=" + targetCenter.getX() + " y=" + targetCenter.getY() + " z=" + targetCenter.getZ() + " (yOffset=" + config.yOffsetBlocks() + ")");

        boolean enabled = true;
        String runId = String.valueOf(System.currentTimeMillis());
        Path runDir = UndoPaths.runDir(runId);
        try {
            Files.createDirectories(runDir);
            Files.createDirectories(UndoPaths.undoBaseDir());
        } catch (Exception e) {
            enabled = false;
            AtlantisMod.LOGGER.warn("Undo persistence disabled: {}", e.getMessage());
        }
        this.undoEnabled = enabled;
        this.undoRunId = runId;
        this.undoRunDir = runDir;

        this.runStateFile = ConstructStatePaths.stateFile(runDir);
        this.runCompleted = false;

        this.protectionCollector = new ProtectionCollector(
            runId,
            world.getRegistryKey().getValue().toString()
        );
        this.protectionRegistered = false;

        if (undoEnabled) {
            send(source.getServer(), "Undo recording enabled. Run ID: " + undoRunId + " (saved under config/atlantis/undo/)");
            writeUndoMetadataSafely();
        }

        // Initial state checkpoint.
        persistRunStateAsync("SCAN_SLICES");
    }

    String getRunId() {
        return undoRunId;
    }

    ConstructTask(ServerCommandSource source, ConstructConfig config, ServerWorld world, ConstructRunState resumeState, Executor ioExecutor) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = new ConstructConfig(
            config.delayBetweenStagesTicks(),
            config.delayBetweenSlicesTicks(),
            config.maxChunksToLoadPerTick(),
            resumeState.yOffsetBlocks(),
            config.maxEntitiesToProcessPerTick(),
            config.playerEjectMarginBlocks(),
            config.playerEjectTeleportOffsetBlocks(),
            config.pasteFlushEveryBlocks(),
            config.tickTimeBudgetNanos(),
            config.undoTickTimeBudgetNanos(),
            config.undoFlushEveryBlocks(),
            config.maxFluidNeighborUpdatesPerTick(),
            config.expectedTickNanos(),
            config.adaptiveScaleSmoothing(),
            config.adaptiveScaleMin(),
            config.adaptiveScaleMax()
        );
        this.world = world;
        this.targetCenter = new BlockPos(resumeState.rawCenterX(), resumeState.rawCenterY(), resumeState.rawCenterZ());
        this.ioExecutor = ioExecutor;

        this.undoRunId = resumeState.runId();
        this.undoRunDir = UndoPaths.runDir(undoRunId);
        this.undoEnabled = Files.exists(undoRunDir);

        if (undoEnabled) {
            preloadExistingUndoSliceFiles();
        }

        this.runStateFile = ConstructStatePaths.stateFile(undoRunDir);
        this.runCompleted = resumeState.completed();

        this.protectionCollector = new ProtectionCollector(
            undoRunId,
            world.getRegistryKey().getValue().toString()
        );
        this.protectionRegistered = false;

        if (resumeState.anchorToX() != null && resumeState.anchorToY() != null && resumeState.anchorToZ() != null) {
            this.pasteAnchorTo = BlockVector3.at(resumeState.anchorToX(), resumeState.anchorToY(), resumeState.anchorToZ());
        }
        if (resumeState.overallMinX() != null && resumeState.overallMinY() != null && resumeState.overallMinZ() != null
            && resumeState.overallMaxX() != null && resumeState.overallMaxY() != null && resumeState.overallMaxZ() != null) {
            this.overallMin = BlockVector3.at(resumeState.overallMinX(), resumeState.overallMinY(), resumeState.overallMinZ());
            this.overallMax = BlockVector3.at(resumeState.overallMaxX(), resumeState.overallMaxY(), resumeState.overallMaxZ());
        }

        this.sliceIndex = Math.max(0, resumeState.nextSliceIndex());
        this.stage = Stage.SCAN_SLICES;

        send(source.getServer(), "Resuming construct run " + undoRunId + " at slice=" + (this.sliceIndex + 1));
    }

    private void preloadExistingUndoSliceFiles() {
        try {
            Path metadataFile = UndoPaths.metadataFile(undoRunDir);
            if (Files.exists(metadataFile)) {
                UndoRunMetadata metadata = UndoMetadataIO.read(metadataFile);
                if (metadata != null && metadata.sliceFiles() != null) {
                    undoSliceFiles.addAll(metadata.sliceFiles());
                }
            }
        } catch (Exception ignored) {
            // Fall back to scanning the folder.
        }

        if (!undoSliceFiles.isEmpty()) {
            return;
        }

        try (var stream = Files.list(undoRunDir)) {
            stream
                .filter(Files::isRegularFile)
                .map(p -> p.getFileName().toString())
                .filter(name -> name.toLowerCase(Locale.ROOT).endsWith(".atlundo"))
                .forEach(undoSliceFiles::add);
        } catch (Exception ignored) {
        }
    }

    static ConstructRunState tryLoadLatestResumableState(ServerWorld world) {
        Path base = UndoPaths.undoBaseDir();
        if (!Files.exists(base) || !Files.isDirectory(base)) {
            return null;
        }

        String dimension = world.getRegistryKey().getValue().toString();

        ConstructRunState best = null;
        long bestCreatedAt = Long.MIN_VALUE;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(base)) {
            for (Path child : stream) {
                if (!Files.isDirectory(child)) {
                    continue;
                }

                Path stateFile = ConstructStatePaths.stateFile(child);
                if (!Files.exists(stateFile)) {
                    continue;
                }

                ConstructRunState state;
                try {
                    state = ConstructRunStateIO.read(stateFile);
                } catch (Exception ignored) {
                    continue;
                }

                if (state.completed()) {
                    continue;
                }
                if (state.dimension() == null || !state.dimension().equals(dimension)) {
                    continue;
                }
                if (state.createdAtEpochMillis() >= bestCreatedAt) {
                    best = state;
                    bestCreatedAt = state.createdAtEpochMillis();
                }
            }
        } catch (Exception ignored) {
            return null;
        }

        return best;
    }

    private void persistRunStateAsync(String stageName) {
        if (runCompleted) {
            return;
        }

        Integer anchorX = (pasteAnchorTo != null) ? pasteAnchorTo.x() : null;
        Integer anchorY = (pasteAnchorTo != null) ? pasteAnchorTo.y() : null;
        Integer anchorZ = (pasteAnchorTo != null) ? pasteAnchorTo.z() : null;

        Integer minX = (overallMin != null) ? overallMin.x() : null;
        Integer minY = (overallMin != null) ? overallMin.y() : null;
        Integer minZ = (overallMin != null) ? overallMin.z() : null;
        Integer maxX = (overallMax != null) ? overallMax.x() : null;
        Integer maxY = (overallMax != null) ? overallMax.y() : null;
        Integer maxZ = (overallMax != null) ? overallMax.z() : null;

        ConstructRunState state = new ConstructRunState(
            ConstructRunState.CURRENT_VERSION,
            undoRunId,
            world.getRegistryKey().getValue().toString(),
            System.currentTimeMillis(),
            targetCenter.getX(),
            targetCenter.getY(),
            targetCenter.getZ(),
            config.yOffsetBlocks(),
            "SOLIDS",
            sliceIndex,
            stageName,
            anchorX,
            anchorY,
            anchorZ,
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ,
            false
        );

        CompletableFuture
            .runAsync(() -> {
                try {
                    ConstructRunStateIO.write(runStateFile, state);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ioExecutor)
            .exceptionally(throwable -> {
                AtlantisMod.LOGGER.warn("Failed to persist construct state {}: {}", runStateFile, throwable.getMessage());
                return null;
            });
    }

    private void markRunCompletedAsync() {
        if (runCompleted) {
            return;
        }
        runCompleted = true;

        ConstructRunState state = new ConstructRunState(
            ConstructRunState.CURRENT_VERSION,
            undoRunId,
            world.getRegistryKey().getValue().toString(),
            System.currentTimeMillis(),
            targetCenter.getX(),
            targetCenter.getY(),
            targetCenter.getZ(),
            config.yOffsetBlocks(),
            "SOLIDS",
            sliceIndex,
            "DONE",
            (pasteAnchorTo != null) ? pasteAnchorTo.x() : null,
            (pasteAnchorTo != null) ? pasteAnchorTo.y() : null,
            (pasteAnchorTo != null) ? pasteAnchorTo.z() : null,
            (overallMin != null) ? overallMin.x() : null,
            (overallMin != null) ? overallMin.y() : null,
            (overallMin != null) ? overallMin.z() : null,
            (overallMax != null) ? overallMax.x() : null,
            (overallMax != null) ? overallMax.y() : null,
            (overallMax != null) ? overallMax.z() : null,
            true
        );

        // This file is tiny; write it synchronously so the cycle tick in the same server tick
        // cannot immediately "resume" a run that just finished due to async IO lag.
        try {
            ConstructRunStateIO.write(runStateFile, state);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to finalize construct state {}: {}", runStateFile, e.getMessage());
            // Best-effort fallback.
            CompletableFuture
                .runAsync(() -> {
                    try {
                        ConstructRunStateIO.write(runStateFile, state);
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }, ioExecutor)
                .exceptionally(throwable -> {
                    AtlantisMod.LOGGER.warn("Failed to finalize construct state {}: {}", runStateFile, throwable.getMessage());
                    return null;
                });
        }
    }

    @Override
    public boolean tick(MinecraftServer server) {
        long tickStartNanos = System.nanoTime();
        updateAdaptiveWorkScale(tickStartNanos);
        long deadline = tickStartNanos + scaledBudget(config.tickTimeBudgetNanos());

        maybeLogProgress(server);

        while (System.nanoTime() < deadline && stage != Stage.DONE) {
            // Prevent players from standing in the build while it is being constructed/cleaned.
            enforcePlayersOutside();

            switch (stage) {
                case SCAN_SLICES -> {
                    Path dir = SchematicSliceScanner.defaultSchematicDir();
                    try {
                        slices = SchematicSliceScanner.scanSlices(dir);
                    } catch (Exception e) {
                        send(server, "Failed to scan .schem files in " + dir + ": " + e.getMessage());
                        AtlantisMod.LOGGER.error("Failed to scan .schem files", e);
                        // Avoid a resume loop (cycle will keep trying to resume the latest run).
                        // If scanning fails, the safest behavior is to finalize the run and require a new /construct.
                        markRunCompletedAsync();
                        stage = Stage.DONE;
                        break;
                    }

                    if (slices.isEmpty()) {
                        send(server, "No .schem files found in: " + dir);
                        // Avoid a resume loop when the schematics directory is empty.
                        markRunCompletedAsync();
                        stage = Stage.DONE;
                        break;
                    }

                    send(server, "Found " + slices.size() + " slice(s) in " + dir + ". Starting paste...");

                    // Resume safeguard: if we already wrote undo slice files before the restart,
                    // skip ahead so we don't re-paste completed slices.
                    if (undoEnabled) {
                        while (sliceIndex < slices.size()) {
                            Path undoFile = UndoPaths.sliceUndoFile(undoRunDir, sliceIndex);
                            if (!Files.exists(undoFile)) {
                                break;
                            }
                            sliceIndex++;
                        }
                    }

                    persistRunStateAsync("LOAD_SLICE_ASYNC");
                    stage = Stage.LOAD_SLICE_ASYNC;
                }

                case LOAD_SLICE_ASYNC -> {
                    if (sliceIndex >= slices.size()) {
                        // Clear barrier carve markers once at the very end.
                        if (sawBarrierThisRun) {
                            stage = Stage.CLEAR_BARRIERS;
                            waitTicks = config.delayBetweenStagesTicks();
                            break;
                        }

                        // Post-build cleanup (order: players kept out continuously -> remove mobs -> remove dropped items).
                        stage = Stage.CLEANUP_MOBS;
                        waitTicks = config.delayBetweenStagesTicks();
                        break;
                    }

                    if (pendingClipboard == null) {
                        // New map per slice so async persistence can keep using the old one.
                        // (Clearing would race with the IO thread.)
                        undoThisSlice = new Long2ObjectOpenHashMap<>();

                        SchematicSlice slice = slices.get(sliceIndex);
                        String name = slice.path().getFileName().toString();
                        send(server, "Loading slice " + (sliceIndex + 1) + "/" + slices.size() + ": " + name);

                        persistRunStateAsync("LOAD_SLICE_ASYNC");

                        pendingClipboard = CompletableFuture.supplyAsync(() -> {
                            try {
                                return WorldEditSchematicPaster.loadClipboard(slice.path());
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }, ioExecutor);
                        break;
                    }

                    if (!pendingClipboard.isDone()) {
                        // Wait for IO completion; don't spin.
                        return false;
                    }

                    Clipboard clipboard;
                    try {
                        clipboard = pendingClipboard.join();
                    } catch (Exception e) {
                        send(server, "Failed to load slice: " + e.getMessage());
                        AtlantisMod.LOGGER.error("Failed to load schematic slice", e);
                        pendingClipboard = null;
                        sliceIndex++;
                        stage = Stage.WAIT_BETWEEN_SLICES;
                        waitTicks = config.delayBetweenSlicesTicks();
                        break;
                    }

                    pendingClipboard = CompletableFuture.completedFuture(clipboard);
                    stage = Stage.PRELOAD_CHUNKS;
                    waitTicks = config.delayBetweenStagesTicks();
                }

                case PRELOAD_CHUNKS -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }

                    Clipboard clipboard = pendingClipboard != null ? pendingClipboard.getNow(null) : null;
                    if (clipboard == null) {
                        stage = Stage.LOAD_SLICE_ASYNC;
                        break;
                    }

                    if (pasteAnchorTo == null) {
                        BlockPos centeredTarget = new BlockPos(
                            targetCenter.getX(),
                            targetCenter.getY() + config.yOffsetBlocks(),
                            targetCenter.getZ()
                        );

                        pasteAnchorTo = WorldEditSchematicPaster.computeCenteredAnchorTo(clipboard, centeredTarget);
                        // Keep chat simple: only report the center once.
                        send(server, "Construct center: x=" + centeredTarget.getX() + " y=" + centeredTarget.getY() + " z=" + centeredTarget.getZ());
                        AtlantisMod.LOGGER.info(String.format(Locale.ROOT,
                            "Construct anchor computed from slice_000: to=[%d,%d,%d] adjustedCenter=[%d,%d,%d] rawCenter=[%d,%d,%d] yOffset=%d",
                            pasteAnchorTo.x(), pasteAnchorTo.y(), pasteAnchorTo.z(),
                            centeredTarget.getX(), centeredTarget.getY(), centeredTarget.getZ(),
                            targetCenter.getX(), targetCenter.getY(), targetCenter.getZ(),
                            config.yOffsetBlocks()
                        ));

                        persistRunStateAsync("PRELOAD_CHUNKS");
                    }

                    if (chunksToLoad == null) {
                        // Preload only the chunks this slice will actually touch at its paste position.
                        // Also: only preload chunks we haven't already loaded in this construct run.
                        var placement = WorldEditSchematicPaster.computePlacement(clipboard, pasteAnchorTo);
                        var region = placement.region();

                        updateOverallBounds(region.worldMin(), region.worldMax());

                        int minX = region.worldMin().x();
                        int maxX = region.worldMax().x();
                        int minZ = region.worldMin().z();
                        int maxZ = region.worldMax().z();

                        int minChunkX = minX >> 4;
                        int maxChunkX = maxX >> 4;
                        int minChunkZ = minZ >> 4;
                        int maxChunkZ = maxZ >> 4;

                        chunksToLoad = new ArrayDeque<>();
                        activeChunkLoads.clear();
                        for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                            for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                                ChunkPos pos = new ChunkPos(cx, cz);
                                long key = ChunkPos.toLong(pos.x, pos.z);
                                if (loadedChunkKeys.contains(key)) {
                                    continue;
                                }
                                chunksToLoad.add(pos);
                            }
                        }

                        if (chunksToLoad.isEmpty()) {
                            // Nothing new to load: don't spam chat and don't waste ticks here.
                            chunksToLoad = null;
                            stage = Stage.PASTE;
                            waitTicks = config.delayBetweenStagesTicks();
                            break;
                        }

                        send(server, String.format(Locale.ROOT,
                            "Preloading chunks for slice %d/%d (%d new chunks)...",
                            (sliceIndex + 1),
                            slices.size(),
                            chunksToLoad.size()
                        ));
                    }

                    // Request a few chunk loads per tick without blocking the server thread.
                    int requestedThisTick = 0;
                    int maxChunksThisTick = scaledCap(config.maxChunksToLoadPerTick());
                    while (!chunksToLoad.isEmpty() && requestedThisTick < maxChunksThisTick && System.nanoTime() < deadline) {
                        ChunkPos pos = chunksToLoad.removeFirst();
                        long key = ChunkPos.toLong(pos.x, pos.z);

                        CompletableFuture<?> fut = world.getChunkManager().addChunkLoadingTicket(PRELOAD_TICKET_TYPE, pos, PRELOAD_TICKET_LEVEL);
                        activeChunkLoads.put(key, fut);
                        chunkTicketKeys.add(key);
                        loadedChunkKeys.add(key);
                        requestedThisTick++;
                    }

                    if (requestedThisTick > 0 && com.silver.atlantis.spawn.SpawnMobConfig.DIAGNOSTIC_LOGS) {
                        AtlantisMod.LOGGER.info(
                            "[ConstructTickets] runId={} slice={}/{} addedThisTick={} totalActiveLoads={} totalTicketKeys={}",
                            undoRunId,
                            (sliceIndex + 1),
                            slices.size(),
                            requestedThisTick,
                            activeChunkLoads.size(),
                            chunkTicketKeys.size()
                        );
                    }

                    // Poll completion; do not proceed to paste until all requested chunks are actually loaded.
                    var it = activeChunkLoads.long2ObjectEntrySet().fastIterator();
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
                            // Ticket completed but chunk isn't visible as loaded yet; keep waiting.
                            continue;
                        }

                        it.remove();
                    }

                    if (!chunksToLoad.isEmpty() || !activeChunkLoads.isEmpty()) {
                        return false;
                    }

                    chunksToLoad = null;
                    stage = Stage.PASTE;
                    waitTicks = config.delayBetweenStagesTicks();
                }

                case PASTE -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }

                    Clipboard clipboard = pendingClipboard != null ? pendingClipboard.getNow(null) : null;
                    if (clipboard == null) {
                        stage = Stage.LOAD_SLICE_ASYNC;
                        break;
                    }

                    SchematicSlice slice = slices.get(sliceIndex);
                    String name = slice.path().getFileName().toString();

                    // Manual incremental paste (guaranteed to respect tick budget).
                    if (pasteEditSession == null) {
                        pasteSliceName = name;
                        pasteClipboard = clipboard;

                        int cfgFlush = config.pasteFlushEveryBlocks();
                        pasteFlushEveryBlocks = (cfgFlush > 0) ? cfgFlush : PASTE_FLUSH_EVERY_BLOCKS_DEFAULT;

                        var weWorld = FabricAdapter.adapt(world);
                        pasteWeWorld = weWorld;
                        pasteEditSession = WorldEdit.getInstance()
                            .newEditSessionBuilder()
                            .world(weWorld)
                            .maxBlocks(-1)
                            .build();
                        // Run without fast mode to keep behavior closer to manual pastes.
                        pasteEditSession.setFastMode(false);
                        pasteEditSession.setSideEffectApplier(PASTE_SIDE_EFFECTS);

                        BlockVector3 origin = pasteClipboard.getOrigin();
                        pasteShift = pasteAnchorTo.subtract(origin);

                        var region = pasteClipboard.getRegion();
                        var min = region.getMinimumPoint();
                        var max = region.getMaximumPoint();

                        pasteMinX = min.x();
                        pasteMaxX = max.x();
                        pasteMinY = min.y();
                        pasteMaxY = max.y();
                        pasteMinZ = min.z();
                        pasteMaxZ = max.z();

                        // World-space bounds for post-pass barrier clearing.
                        BlockVector3 worldMin = min.add(pasteShift);
                        BlockVector3 worldMax = max.add(pasteShift);
                        barrierMinX = worldMin.x();
                        barrierMaxX = worldMax.x();
                        barrierMinY = worldMin.y();
                        barrierMaxY = worldMax.y();
                        barrierMinZ = worldMin.z();
                        barrierMaxZ = worldMax.z();
                        barrierX = barrierMinX;
                        barrierY = barrierMinY;
                        barrierZ = barrierMinZ;
                        barrierSinceLastFlush = 0;

                        pasteX = pasteMinX;
                        pasteZ = pasteMinZ;
                        pasteY = pasteMinY;
                        pasteSinceLastFlush = 0;

                        send(server, "Pasting " + name + " (manual tick-sliced, like //paste -a)...");
                    }

                    boolean finished = false;
                    try {
                        while (System.nanoTime() < deadline) {
                            if (pasteX > pasteMaxX) {
                                finished = true;
                                break;
                            }

                            BlockVector3 src = BlockVector3.at(pasteX, pasteY, pasteZ);
                            BaseBlock block = pasteClipboard.getFullBlock(src);

                            // Ignore air for speed.
                            if (block.getBlockType() != BlockTypes.AIR) {
                                // Convert barriers to air immediately (same end result as paste+replace).
                                // Important: barriers are used as a "carving" signal, so they must be applied
                                // during the NON_FLUID pass even though they become AIR.
                                boolean wasBarrier = (block.getBlockType() == BlockTypes.BARRIER);
                                if (wasBarrier) {
                                    // Keep barriers during paste so they act as temporary solid plugs.
                                    // This prevents ocean/river water from flowing into carved interiors while
                                    // the slice is still being pasted over many ticks.
                                    sawBarrierThisRun = true;
                                }

                                BlockVector3 dst = src.add(pasteShift);
                                long dstKey = BlockPos.asLong(dst.x(), dst.y(), dst.z());

                                // Barrier-marked positions are treated as protected interior air-space.
                                if (wasBarrier) {
                                    protectionCollector.addInterior(dstKey);
                                }

                                BeforeSnapshot before = captureBeforeSnapshot(dst, undoEnabled);
                                if (!wasBarrier && didActuallyChange(before, block)) {
                                    protectionCollector.addPlaced(dstKey);
                                }
                                pasteEditSession.setBlock(dst, block);

                                pasteSinceLastFlush++;
                                if (pasteSinceLastFlush >= pasteFlushEveryBlocks) {
                                    pasteEditSession.flushSession();
                                    pasteEditSession.commit();
                                    pasteSinceLastFlush = 0;
                                }
                            }

                            advancePasteCursor();
                        }
                    } catch (Throwable t) {
                        send(server, "Paste failed for " + pasteSliceName + ": " + t.getMessage());
                        AtlantisMod.LOGGER.error("Paste failed", t);
                        safeClosePaste();
                        pendingClipboard = null;
                        sliceIndex++;
                        stage = Stage.WAIT_BETWEEN_SLICES;
                        waitTicks = config.delayBetweenSlicesTicks();
                        break;
                    }

                    if (!finished) {
                        // Make progress visible even when we yield mid-slice.
                        if (pasteEditSession != null && pasteSinceLastFlush > 0) {
                            try {
                                pasteEditSession.flushSession();
                                pasteEditSession.commit();
                            } catch (Exception ignored) {
                            }
                            pasteSinceLastFlush = 0;
                        }
                        return false;
                    }

                    // Finalize and persist undo.
                    try {
                        if (undoEnabled && !undoThisSlice.isEmpty()) {
                            Long2ObjectOpenHashMap<UndoEntry> toPersist = undoThisSlice;
                            // Swap immediately to free memory and avoid accidental reuse.
                            undoThisSlice = new Long2ObjectOpenHashMap<>();
                            persistSliceUndoAsync(server, sliceIndex, toPersist);
                        }
                    } catch (Throwable t) {
                        AtlantisMod.LOGGER.warn("Failed to finalize paste bookkeeping for {}: {}", pasteSliceName, t.getMessage());
                    } finally {
                        safeClosePaste();
                    }

                    send(server, "Pasted " + name + " at center x=" + targetCenter.getX() + " y=" + (targetCenter.getY() + config.yOffsetBlocks()) + " z=" + targetCenter.getZ());

                    pendingClipboard = null;
                    sliceIndex++;
                    persistRunStateAsync("WAIT_BETWEEN_SLICES");
                    stage = Stage.WAIT_BETWEEN_SLICES;
                    waitTicks = config.delayBetweenSlicesTicks();
                }

                case CLEAR_BARRIERS -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }

                    if (!sawBarrierThisRun || overallMin == null || overallMax == null) {
                        stage = Stage.CLEANUP_MOBS;
                        waitTicks = config.delayBetweenStagesTicks();
                        break;
                    }

                    if (!barrierClearInitialized) {
                        int cfgFlush = config.pasteFlushEveryBlocks();
                        pasteFlushEveryBlocks = (cfgFlush > 0) ? cfgFlush : PASTE_FLUSH_EVERY_BLOCKS_DEFAULT;

                        var weWorld = FabricAdapter.adapt(world);
                        pasteWeWorld = weWorld;
                        pasteEditSession = WorldEdit.getInstance()
                            .newEditSessionBuilder()
                            .world(weWorld)
                            .maxBlocks(-1)
                            .build();
                        pasteEditSession.setFastMode(false);
                        pasteEditSession.setSideEffectApplier(PASTE_SIDE_EFFECTS);

                        barrierMinX = overallMin.x();
                        barrierMaxX = overallMax.x();
                        barrierMinY = overallMin.y();
                        barrierMaxY = overallMax.y();
                        barrierMinZ = overallMin.z();
                        barrierMaxZ = overallMax.z();
                        barrierX = barrierMinX;
                        barrierY = barrierMinY;
                        barrierZ = barrierMinZ;
                        barrierSinceLastFlush = 0;

                        barrierClearInitialized = true;
                        send(server, "Clearing barrier carve markers (final step)...");
                    }

                    try {
                        BaseBlock air = BlockTypes.AIR.getDefaultState().toBaseBlock();

                        while (System.nanoTime() < deadline) {
                            if (barrierX > barrierMaxX) {
                                break;
                            }

                            barrierScanPos.set(barrierX, barrierY, barrierZ);
                            if (world.getBlockState(barrierScanPos).isOf(Blocks.BARRIER)) {
                                BlockVector3 dst = BlockVector3.at(barrierX, barrierY, barrierZ);
                                pasteEditSession.setBlock(dst, air);

                                barrierSinceLastFlush++;
                                if (barrierSinceLastFlush >= pasteFlushEveryBlocks) {
                                    pasteEditSession.flushSession();
                                    pasteEditSession.commit();
                                    barrierSinceLastFlush = 0;
                                }
                            }

                            // Advance scan cursor: Y fastest, then Z, then X.
                            barrierY++;
                            if (barrierY > barrierMaxY) {
                                barrierY = barrierMinY;
                                barrierZ++;
                                if (barrierZ > barrierMaxZ) {
                                    barrierZ = barrierMinZ;
                                    barrierX++;
                                }
                            }
                        }
                    } catch (Throwable t) {
                        send(server, "Barrier clear failed: " + t.getMessage());
                        AtlantisMod.LOGGER.error("Barrier clear failed", t);
                        safeClosePaste();
                        sawBarrierThisRun = false;
                        barrierClearInitialized = false;
                        stage = Stage.CLEANUP_MOBS;
                        waitTicks = config.delayBetweenStagesTicks();
                        break;
                    }

                    if (barrierX <= barrierMaxX) {
                        // Yield mid-scan; flush so players can see progress.
                        if (pasteEditSession != null && barrierSinceLastFlush > 0) {
                            try {
                                pasteEditSession.flushSession();
                                pasteEditSession.commit();
                            } catch (Exception ignored) {
                            }
                            barrierSinceLastFlush = 0;
                        }
                        return false;
                    }

                    // Done.
                    barrierClearInitialized = false;
                    safeClosePaste();
                    stage = Stage.CLEANUP_MOBS;
                    waitTicks = config.delayBetweenStagesTicks();
                }

                case WAIT_BETWEEN_SLICES -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }
                    stage = Stage.LOAD_SLICE_ASYNC;
                }

                case CLEANUP_MOBS -> {
                    if (waitTicks > 0) {
                        waitTicks--;
                        return false;
                    }

                    if (entitiesToProcess == null) {
                        send(server, "Cleanup: removing mobs in build area...");
                        entitiesToProcess = EntityCleanup.collectMobs(world,
                            EntityCleanup.cleanupBox(world, targetCenter, overallMin, overallMax, config.playerEjectMarginBlocks())
                        );
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
                        send(server, "Cleanup: removing dropped items in build area...");
                        Box cleanup = EntityCleanup.cleanupBox(world, targetCenter, overallMin, overallMax, config.playerEjectMarginBlocks());
                        entitiesToProcess = EntityCleanup.collectItemsAndXp(world, cleanup);
                        entitiesProcessedIndex = 0;
                    }

                    entitiesProcessedIndex = EntityCleanup.discardSome(entitiesToProcess, entitiesProcessedIndex, scaledCap(config.maxEntitiesToProcessPerTick()));
                    if (entitiesProcessedIndex >= entitiesToProcess.size()) {
                        entitiesToProcess = null;
                        releaseChunkTickets();

                        // Register protections after the paste/cleanup completes so all slices are included.
                        finalizeProtectionOnce();

                        send(server, "Construct complete.");
                        markRunCompletedAsync();
                        stage = Stage.DONE;
                    }
                }

                case DONE -> {
                    return true;
                }
            }
        }

        return stage == Stage.DONE;
    }

    private void maybeLogProgress(MinecraftServer server) {
        long now = System.nanoTime();
        if (lastProgressLogNanos != 0 && (now - lastProgressLogNanos) < 5_000_000_000L) {
            return;
        }
        lastProgressLogNanos = now;

        // Only log while actively running (avoid log spam after completion).
        if (stage == Stage.DONE) {
            return;
        }

        int sliceTotal = (slices != null) ? slices.size() : -1;
        int sliceNum = sliceIndex + 1;

        String extra = "";
        if (stage == Stage.PASTE && pasteClipboard != null) {
            extra = String.format(Locale.ROOT,
                " cursor=(%d,%d,%d) x=[%d..%d] y=[%d..%d] z=[%d..%d]",
                pasteX, pasteY, pasteZ,
                pasteMinX, pasteMaxX,
                pasteMinY, pasteMaxY,
                pasteMinZ, pasteMaxZ
            );
        } else if (stage == Stage.PRELOAD_CHUNKS && chunksToLoad != null) {
            extra = " remainingChunks=" + chunksToLoad.size();
        } else if (stage == Stage.WAIT_BETWEEN_SLICES) {
            extra = " waitTicks=" + waitTicks;
        }

        AtlantisMod.LOGGER.info(String.format(Locale.ROOT,
            "Construct progress: stage=%s slice=%d/%d%s",
            stage.name(),
            sliceNum,
            sliceTotal,
            extra
        ));
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
        send(server, "Construct crashed: " + throwable.getClass().getSimpleName() + ": " + (throwable.getMessage() != null ? throwable.getMessage() : "(no message)"));
        AtlantisMod.LOGGER.error("Construct crashed", throwable);

        // Best-effort cleanup: release chunk tickets so we don't keep chunks forced-loaded.
        releaseChunkTickets();
    }

    @Override
    public void onCancel(MinecraftServer server, String reason) {
        send(server, "Construct cancelled" + (reason != null && !reason.isBlank() ? (": " + reason) : "."));

        // Best-effort cleanup: close any open WorldEdit session and release chunk tickets.
        safeClosePaste();
        releaseChunkTickets();
    }

    private void releaseChunkTickets() {
        if (chunkTicketKeys.isEmpty()) {
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
            AtlantisMod.LOGGER.warn("Failed to release chunk tickets: {}", e.getMessage());
        } finally {
            if (com.silver.atlantis.spawn.SpawnMobConfig.DIAGNOSTIC_LOGS) {
                AtlantisMod.LOGGER.info(
                    "[ConstructTickets] runId={} released={} remainingActiveLoadsBeforeClear={}",
                    undoRunId,
                    ticketCount,
                    activeChunkLoads.size()
                );
            }
            chunkTicketKeys.clear();
            activeChunkLoads.clear();
        }
    }

    private void recordUndoAt(BlockVector3 dst) {
        captureBeforeSnapshot(dst, true);
    }

    private static final class BeforeSnapshot {
        private final String stateString;
        private final String nbtSnbt;

        private BeforeSnapshot(String stateString, String nbtSnbt) {
            this.stateString = stateString;
            this.nbtSnbt = nbtSnbt;
        }
    }

    private BeforeSnapshot captureBeforeSnapshot(BlockVector3 dst, boolean persistUndo) {
        if (pasteWeWorld == null) {
            return new BeforeSnapshot("minecraft:air", null);
        }

        long key = BlockPos.asLong(dst.x(), dst.y(), dst.z());

        if (persistUndo) {
            UndoEntry existing = undoThisSlice.get(key);
            if (existing != null) {
                return new BeforeSnapshot(existing.blockString(), existing.nbtSnbt());
            }
        }

        try {
            BaseBlock previous = pasteWeWorld.getFullBlock(dst);
            String beforeState = previous != null ? previous.toImmutableState().getAsString() : "minecraft:air";
            String beforeNbt = extractNbtSnbt(previous);

            if (persistUndo) {
                UndoEntry entry = new UndoEntry(dst.x(), dst.y(), dst.z(), beforeState, beforeNbt);
                undoThisSlice.put(key, entry);
            }

            return new BeforeSnapshot(beforeState, beforeNbt);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to read before-state at {},{},{}: {}", dst.x(), dst.y(), dst.z(), e.getMessage());
            return new BeforeSnapshot("minecraft:air", null);
        }
    }

    private static String extractNbtSnbt(BaseBlock block) {
        if (block == null) {
            return null;
        }

        try {
            LazyReference<LinCompoundTag> ref = block.getNbtReference();
            if (ref == null) {
                return null;
            }
            LinCompoundTag tag = ref.getValue();
            if (tag == null) {
                return null;
            }
            return LinStringIO.writeToString(tag);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean didActuallyChange(BeforeSnapshot before, BaseBlock after) {
        String afterState = (after != null) ? after.toImmutableState().getAsString() : "minecraft:air";
        if (before == null) {
            return true;
        }
        if (!Objects.equals(before.stateString, afterState)) {
            return true;
        }

        // If the block carries NBT/components, treat NBT-only differences as a change.
        String afterNbt = extractNbtSnbt(after);
        return !Objects.equals(before.nbtSnbt, afterNbt);
    }

    private void finalizeProtectionOnce() {
        if (protectionRegistered) {
            return;
        }
        protectionRegistered = true;

        if (protectionCollector.isEmpty()) {
            return;
        }

        var entry = protectionCollector.buildEntry();
        ProtectionManager.INSTANCE.register(entry);

        AtlantisMod.LOGGER.info(
            "Registered paste protection for runId={} dim={} placed={} interior={}",
            entry.id(),
            entry.dimensionId(),
            entry.placedPositions().size(),
            entry.interiorPositions().size()
        );

        // Persist so protections survive server restarts.
        // Store next to the undo run folder so `/construct undo` cleanup deletes it.
        if (undoEnabled) {
            Path file = ProtectionPaths.protectionFile(undoRunDir);
            CompletableFuture
                .runAsync(() -> {
                    try {
                        ProtectionFileIO.write(file, entry);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }, ioExecutor)
                .whenComplete((ignored, throwable) -> {
                    if (throwable != null) {
                        AtlantisMod.LOGGER.warn("Failed to persist protection file {}: {}", file, throwable.getMessage());
                    }
                });
        }
    }

    private void persistSliceUndoSafely() {
        try {
            String dimension = world.getRegistryKey().getValue().toString();

            if (undoThisSlice.isEmpty()) {
                return;
            }

            List<UndoEntry> entries = new ArrayList<>(undoThisSlice.size());
            for (UndoEntry entry : undoThisSlice.values()) {
                entries.add(entry);
            }

            Path file = UndoPaths.sliceUndoFile(undoRunDir, sliceIndex);
            UndoSliceFile.write(file, dimension, entries);
            undoSliceFiles.add(file.getFileName().toString());
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to persist undo for slice {}: {}", sliceIndex, e.getMessage());
        }
    }

    private void persistSliceUndoAsync(MinecraftServer server, int sliceIndexToPersist, Long2ObjectOpenHashMap<UndoEntry> sliceUndo) {
        final String dimension = world.getRegistryKey().getValue().toString();
        final Path file = UndoPaths.sliceUndoFile(undoRunDir, sliceIndexToPersist);
        final String fileName = file.getFileName().toString();

        CompletableFuture
            .runAsync(() -> {
                try {
                    List<UndoEntry> entries = new ArrayList<>(sliceUndo.size());
                    for (UndoEntry entry : sliceUndo.values()) {
                        entries.add(entry);
                    }
                    UndoSliceFile.write(file, dimension, entries);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ioExecutor)
            .whenComplete((ignored, throwable) -> server.execute(() -> {
                if (throwable != null) {
                    AtlantisMod.LOGGER.warn("Failed to persist undo for slice {}: {}", sliceIndexToPersist, throwable.getMessage());
                    send(server, "Warning: failed to save undo slice " + fileName + ": " + throwable.getMessage());
                    return;
                }

                undoSliceFiles.add(fileName);
                writeUndoMetadataSafely();
            }));
    }

    private void writeUndoMetadataSafely() {
        try {
            String dimension = world.getRegistryKey().getValue().toString();
            UndoRunMetadata metadata = new UndoRunMetadata(
                UndoRunMetadata.CURRENT_VERSION,
                undoRunId,
                dimension,
                System.currentTimeMillis(),
                config.yOffsetBlocks(),
                targetCenter.getX(),
                targetCenter.getY(),
                targetCenter.getZ(),
                List.copyOf(undoSliceFiles)
            );
            UndoMetadataIO.write(UndoPaths.metadataFile(undoRunDir), metadata);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to write undo metadata: {}", e.getMessage());
        }
    }

    private void updateOverallBounds(BlockVector3 min, BlockVector3 max) {
        if (overallMin == null || overallMax == null) {
            overallMin = min;
            overallMax = max;
            return;
        }

        overallMin = BlockVector3.at(
            Math.min(overallMin.x(), min.x()),
            Math.min(overallMin.y(), min.y()),
            Math.min(overallMin.z(), min.z())
        );
        overallMax = BlockVector3.at(
            Math.max(overallMax.x(), max.x()),
            Math.max(overallMax.y(), max.y()),
            Math.max(overallMax.z(), max.z())
        );
    }

    private void enforcePlayersOutside() {
        // Only enforce X/Z containment.
        // Also: we protect a conservative footprint around the /findflat center
        // so players are ejected even before all slice bounds have been computed.
        // The find algorithm searches a 510x510 area, so we assume the build fits
        // inside roughly +/-255 blocks from the center.
        int extra = Math.max(0, config.playerEjectMarginBlocks());
        int fallbackHalfSize = 255 + extra;
        int protectedMinX = targetCenter.getX() - fallbackHalfSize;
        int protectedMaxX = targetCenter.getX() + fallbackHalfSize;
        int protectedMinZ = targetCenter.getZ() - fallbackHalfSize;
        int protectedMaxZ = targetCenter.getZ() + fallbackHalfSize;

        if (overallMin != null && overallMax != null) {
            protectedMinX = Math.min(protectedMinX, overallMin.x() - extra);
            protectedMaxX = Math.max(protectedMaxX, overallMax.x() + extra);
            protectedMinZ = Math.min(protectedMinZ, overallMin.z() - extra);
            protectedMaxZ = Math.max(protectedMaxZ, overallMax.z() + extra);
        }

        double minX = protectedMinX;
        double maxX = protectedMaxX + 1;
        double minZ = protectedMinZ;
        double maxZ = protectedMaxZ + 1;

        for (ServerPlayerEntity player : world.getServer().getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != world) {
                continue;
            }

            double x = player.getX();
            double z = player.getZ();
            if (x < minX || x > maxX || z < minZ || z > maxZ) {
                lastKnownOutsideByPlayer.put(player.getUuid(), player.getBlockPos());
                continue;
            }

            teleportPlayerSafelyOutside(player, protectedMinX, protectedMaxX, protectedMinZ, protectedMaxZ);
        }
    }

    private static boolean isInsideProtectedXZ(BlockPos pos, int protectedMinX, int protectedMaxX, int protectedMinZ, int protectedMaxZ) {
        return pos.getX() >= protectedMinX
            && pos.getX() <= (protectedMaxX + 1)
            && pos.getZ() >= protectedMinZ
            && pos.getZ() <= (protectedMaxZ + 1);
    }

    private void teleportPlayerSafelyOutside(ServerPlayerEntity player, int protectedMinX, int protectedMaxX, int protectedMinZ, int protectedMaxZ) {
        int margin = Math.max(1, config.playerEjectTeleportOffsetBlocks());
        UUID playerId = player.getUuid();

        BlockPos best = null;
        BlockPos returnPos = lastKnownOutsideByPlayer.get(playerId);
        if (returnPos != null) {
            BlockPos safeReturn = findSafeTeleportPos(returnPos.getX(), returnPos.getZ());
            if (safeReturn != null && !isInsideProtectedXZ(safeReturn, protectedMinX, protectedMaxX, protectedMinZ, protectedMaxZ)) {
                best = safeReturn;
            }
        }

        int bestMobCount = Integer.MAX_VALUE;

        if (best != null) {
            bestMobCount = world.getEntitiesByClass(MobEntity.class,
                new Box(best).expand(16.0, 8.0, 16.0),
                e -> true
            ).size();
        }

        // Candidate positions just outside each side of the build.
        int px = player.getBlockX();
        int pz = player.getBlockZ();

        int minX = protectedMinX - margin;
        int maxX = protectedMaxX + margin;
        int minZ = protectedMinZ - margin;
        int maxZ = protectedMaxZ + margin;

        BlockPos[] candidates = new BlockPos[] {
            new BlockPos(minX, 0, pz),
            new BlockPos(maxX, 0, pz),
            new BlockPos(px, 0, minZ),
            new BlockPos(px, 0, maxZ)
        };

        for (BlockPos candidate : candidates) {
            BlockPos safe = findSafeTeleportPos(candidate.getX(), candidate.getZ());
            if (safe == null) {
                continue;
            }

            if (isInsideProtectedXZ(safe, protectedMinX, protectedMaxX, protectedMinZ, protectedMaxZ)) {
                continue;
            }

            int mobCount = world.getEntitiesByClass(MobEntity.class,
                new Box(safe).expand(16.0, 8.0, 16.0),
                e -> true
            ).size();

            if (mobCount < bestMobCount) {
                bestMobCount = mobCount;
                best = safe;
                if (mobCount == 0) {
                    break;
                }
            }
        }

        if (best == null) {
            // Fallback: world spawn top.
            MinecraftServer server = world.getServer();
            BlockPos spawn = (server.getSpawnPoint() != null)
                ? server.getSpawnPoint().getPos()
                : BlockPos.ORIGIN;
            BlockPos spawnSafe = findSafeTeleportPos(spawn.getX(), spawn.getZ());
            if (spawnSafe != null && !isInsideProtectedXZ(spawnSafe, protectedMinX, protectedMaxX, protectedMinZ, protectedMaxZ)) {
                best = spawnSafe;
            }
        }

        if (best == null) {
            return;
        }

        int cappedY = Math.min(best.getY(), 310);

        player.teleport(
            world,
            best.getX() + 0.5,
            cappedY,
            best.getZ() + 0.5,
            EnumSet.noneOf(PositionFlag.class),
            player.getYaw(),
            player.getPitch(),
            false
        );
        lastKnownOutsideByPlayer.put(playerId, best);
        player.sendMessage(Text.literal("Build is in progress. You were moved outside the build area."), false);
    }

    private BlockPos findSafeTeleportPos(int x, int z) {
        // Ensure we're reading real, current blocks/heightmaps (including player edits),
        // not guessing for an unloaded area.
        world.getChunk(x >> 4, z >> 4);

        // Heightmaps break with an artificial bedrock roof (they will report the roof as the "top"),
        // so instead scan down to find a solid ground block, then try to place the player above it.
        int bottomY = world.getBottomY();
        int maxTeleportFeetY = Math.min(world.getTopYInclusive() - 1, 310);
        if (maxTeleportFeetY <= bottomY) {
            return null;
        }
        int scanStartY = Math.min(world.getTopYInclusive(), world.getSeaLevel() - 2);

        int groundY = bottomY;
        for (int y = scanStartY; y >= bottomY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (!world.getBlockState(pos).getFluidState().isEmpty()) {
                continue;
            }
            if (!world.getBlockState(pos).isSolidBlock(world, pos)) {
                continue;
            }
            groundY = y;
            break;
        }

        // Candidate feet start just above ground.
        int topY = groundY + 1;

        // In vanilla, getTopY(...) is typically "the first free Y above the heightmap surface".
        // So the correct feet position is often exactly topY (with solid ground at topY-1).
        // Search a reasonable vertical band to handle odd terrain/blocks.
        for (int dy = 0; dy <= 24; dy++) {
            int y = topY + dy;
            if (y > maxTeleportFeetY) {
                break;
            }
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.up();
            BlockPos below = feet.down();

            if (!world.getBlockState(below).isSolidBlock(world, below)) {
                continue;
            }
            if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
                continue;
            }
            if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
                continue;
            }

            return feet;
        }

        // If the upward scan failed, also try slightly below the heightmap (cliffs/caves/overhangs).
        for (int dy = -1; dy >= -32; dy--) {
            int y = topY + dy;
            if (y > maxTeleportFeetY) {
                continue;
            }
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos head = feet.up();
            BlockPos below = feet.down();

            if (!world.getBlockState(below).isSolidBlock(world, below)) {
                continue;
            }
            if (!world.getBlockState(feet).getCollisionShape(world, feet).isEmpty()) {
                continue;
            }
            if (!world.getBlockState(head).getCollisionShape(world, head).isEmpty()) {
                continue;
            }

            return feet;
        }
        return null;
    }


    private void send(MinecraftServer server, String message) {
        if (requesterId != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requesterId);
            if (player != null) {
                player.sendMessage(Text.literal(message), false);
                return;
            }
        }

        AtlantisMod.LOGGER.info(message);
    }

    private void advancePasteCursor() {
        // Advance cursor: Y fastest, then Z, then X (keeps locality per column).
        pasteY++;
        if (pasteY > pasteMaxY) {
            pasteY = pasteMinY;
            pasteZ++;
            if (pasteZ > pasteMaxZ) {
                pasteZ = pasteMinZ;
                pasteX++;
            }
        }
    }

    private void safeClosePaste() {
        if (pasteEditSession != null) {
            try {
                pasteEditSession.flushSession();
                pasteEditSession.commit();
                pasteEditSession.close();
            } catch (Exception ignored) {
            }
        }

        pasteEditSession = null;
        pasteWeWorld = null;
        pasteClipboard = null;
        pasteShift = null;
        pasteSliceName = null;
        pasteSinceLastFlush = 0;
        pasteFlushEveryBlocks = 0;

        barrierClearInitialized = false;
        barrierSinceLastFlush = 0;
    }
}
