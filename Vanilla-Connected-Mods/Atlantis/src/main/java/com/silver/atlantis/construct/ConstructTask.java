package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.state.ConstructRunState;
import com.silver.atlantis.construct.state.ConstructRunStateIO;
import com.silver.atlantis.construct.state.ConstructStatePaths;
import com.silver.atlantis.construct.undo.UndoEntry;
import com.silver.atlantis.construct.undo.UndoMetadataIO;
import com.silver.atlantis.construct.undo.UndoPaths;
import com.silver.atlantis.construct.undo.UndoRunMetadata;
import com.silver.atlantis.construct.undo.UndoFileFormat;
import com.silver.atlantis.construct.mixin.ServerChunkManagerAccessor;
import com.silver.atlantis.protect.ProtectionCollector;
import com.silver.atlantis.protect.ProtectionFileIO;
import com.silver.atlantis.protect.ProtectionManager;
import com.silver.atlantis.protect.ProtectionPaths;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.service.ProximityMobManager;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class ConstructTask implements ConstructJob {

    private enum Stage {
        SCAN_INPUT,
        LOAD_SCHEMATIC_ASYNC,
        PREPARE_MISSING_CHUNKS,
        WAIT_FOR_TARGET_CHUNKS_UNLOAD,
        APPLY_SCHEMATIC_ASYNC,
        MERGE_PROTECTION,
        FINALIZE,
        DONE
    }

    private final UUID requesterId;
    private final ConstructConfig config;
    private final ServerWorld world;
    private final BlockPos targetCenter;
    private final Executor ioExecutor;

    private final String undoRunId;
    private final Path undoRunDir;
    private final Path runStateFile;
    private final Path schematicFilePath;

    private final ProtectionCollector protectionCollector;
    private final List<String> undoFiles = new ArrayList<>();

    private Stage stage = Stage.SCAN_INPUT;
    private int passIndex;

    private CompletableFuture<SpongeV3Schematic> pendingSchematic;
    private CompletableFuture<ChunkPlan> pendingChunkPlan;
    private CompletableFuture<PassApplyResult> pendingApply;
    private SpongeV3Schematic loadedSchematic;
    private ChunkPlan loadedChunkPlan;
    private boolean preflightDone;

    private long pendingSchematicStartedAtNanos;

    private final ConstructChunkGenerationScheduler chunkGenerationScheduler = new ConstructChunkGenerationScheduler(31);

    private static final int CHUNK_PREP_BUDGET_PER_TICK = 4;
    private static final int UNLOAD_FORCE_INTERVAL_TICKS = 5;
    private static final int UNLOAD_STATUS_LOG_INTERVAL_TICKS = 20;
    private static final int UNLOAD_FORCE_CHUNK_BUDGET = 8;
    private static final int APPLY_STATUS_LOG_INTERVAL_TICKS = 100;
    private static final int PROTECTION_MERGE_ADD_BUDGET_PER_TICK = 25_000;
    private static final int PROTECTION_MERGE_STATUS_LOG_INTERVAL_TICKS = 20;
    private static final int MIN_TICKET_LEVEL = 0;
    private static final int MAX_TICKET_LEVEL = 40;
    private static final List<ChunkTicketType> DISCOVERED_TICKET_TYPES = discoverChunkTicketTypes();

    private long chunkPrepStartedAtNanos;
    private int chunkPrepExpectedMissingCount;
    private int chunkPrepStatusLogTicks;
    private int unloadWaitTicks;
    private int applyStatusLogTicks;
    private int protectionMergeStatusLogTicks;
    private long applyStartedAtNanos;
    private long protectionMergeStartedAtNanos;
    private Set<Long> unloadChunkKeys = Set.of();
    private final Set<Long> chunkPrepObservedLoadedKeys = new HashSet<>();
    private long[] unloadChunkKeysArray = new long[0];
    private int unloadChunkKeysCursor;
    private Iterator<Long> pendingPlacedMergeIterator;
    private Iterator<Long> pendingInteriorMergeIterator;
    private PassApplyResult pendingProtectionMergeResult;
    private int pendingPlacedMergeRemaining;
    private int pendingInteriorMergeRemaining;

    private CompletableFuture<List<ChunkPos>> pendingMissingAfterUnload;

    private BlockPos pasteAnchorTo;
    private BlockPos overallMin;
    private BlockPos overallMax;

    private boolean runCompleted;
    private boolean protectionRegistered;
    private boolean spawnPauseAcquired;

    ConstructTask(ServerCommandSource source, ConstructConfig config, ServerWorld world, BlockPos targetCenter, Executor ioExecutor) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = config;
        this.world = world;
        this.targetCenter = targetCenter;
        this.ioExecutor = ioExecutor;

        this.undoRunId = String.valueOf(System.currentTimeMillis());
        this.undoRunDir = UndoPaths.runDir(undoRunId);
        this.runStateFile = ConstructStatePaths.stateFile(undoRunDir);
        this.schematicFilePath = AtlantisSchematicPaths.schematicFile();

        this.protectionCollector = new ProtectionCollector(
            undoRunId,
            world.getRegistryKey().getValue().toString()
        );

        this.passIndex = 0;
        this.runCompleted = false;
        this.protectionRegistered = false;

        try {
            Files.createDirectories(undoRunDir);
            Files.createDirectories(UndoPaths.undoBaseDir());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize undo directory", e);
        }

        writeUndoMetadataSafely();
        persistRunStateAsync("SCAN_INPUT");
        send(source.getServer(), "Construct started. Run ID: " + undoRunId);
    }

    ConstructTask(ServerCommandSource source, ConstructConfig config, ServerWorld world, ConstructRunState resumeState, Executor ioExecutor) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = config.withYOffsetBlocks(resumeState.yOffsetBlocks());
        this.world = world;
        this.targetCenter = new BlockPos(resumeState.rawCenterX(), resumeState.rawCenterY(), resumeState.rawCenterZ());
        this.ioExecutor = ioExecutor;

        this.undoRunId = resumeState.runId();
        this.undoRunDir = UndoPaths.runDir(undoRunId);
        this.runStateFile = ConstructStatePaths.stateFile(undoRunDir);
        this.schematicFilePath = AtlantisSchematicPaths.schematicFile();

        this.protectionCollector = new ProtectionCollector(
            undoRunId,
            world.getRegistryKey().getValue().toString()
        );

        this.passIndex = Math.max(0, resumeState.nextPassIndex());
        this.runCompleted = resumeState.completed();
        this.protectionRegistered = false;

        if (resumeState.anchorToX() != null && resumeState.anchorToY() != null && resumeState.anchorToZ() != null) {
            this.pasteAnchorTo = new BlockPos(resumeState.anchorToX(), resumeState.anchorToY(), resumeState.anchorToZ());
        }
        if (resumeState.overallMinX() != null && resumeState.overallMinY() != null && resumeState.overallMinZ() != null
            && resumeState.overallMaxX() != null && resumeState.overallMaxY() != null && resumeState.overallMaxZ() != null) {
            this.overallMin = new BlockPos(resumeState.overallMinX(), resumeState.overallMinY(), resumeState.overallMinZ());
            this.overallMax = new BlockPos(resumeState.overallMaxX(), resumeState.overallMaxY(), resumeState.overallMaxZ());
        }

        preloadExistingUndoFiles();
        send(source.getServer(), "Resuming construct run " + undoRunId + " at pass=" + (passIndex + 1));
    }

    String getRunId() {
        return undoRunId;
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

    @Override
    public boolean tick(MinecraftServer server) {
        long budgetNanos = Math.max(1_000_000L, config.tickTimeBudgetNanos());
        long deadline = System.nanoTime() + budgetNanos;

        while (System.nanoTime() < deadline && stage != Stage.DONE) {
            switch (stage) {
                case SCAN_INPUT -> {
                    if (!Files.exists(schematicFilePath) || !Files.isRegularFile(schematicFilePath)) {
                        send(server, "Schematic not found: " + schematicFilePath.getFileName());
                        stage = Stage.DONE;
                        break;
                    }

                    if (passIndex >= 1) {
                        stage = Stage.FINALIZE;
                        break;
                    }

                    persistRunStateAsync("LOAD_SCHEMATIC_ASYNC");
                    stage = Stage.LOAD_SCHEMATIC_ASYNC;
                }

                case LOAD_SCHEMATIC_ASYNC -> {
                    if (passIndex >= 1) {
                        stage = Stage.FINALIZE;
                        break;
                    }

                    if (loadedSchematic == null) {
                        if (pendingSchematic == null) {
                            send(server, "Loading schematic " + schematicFilePath.getFileName() + " (Sponge v3 native reader)");
                            pendingSchematicStartedAtNanos = System.nanoTime();
                            pendingSchematic = CompletableFuture.supplyAsync(() -> {
                                try {
                                    return SpongeV3Schematic.load(schematicFilePath);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            }, ioExecutor);
                            return false;
                        }

                        if (!pendingSchematic.isDone()) {
                            return false;
                        }

                        SpongeV3Schematic schematic;
                        try {
                            schematic = pendingSchematic.join();
                        } catch (Exception e) {
                            send(server, "Failed to load schematic " + schematicFilePath.getFileName() + ": " + rootMessage(e));
                            pendingSchematic = null;
                            stage = Stage.DONE;
                            break;
                        }
                        loadedSchematic = schematic;
                        pendingSchematic = null;
                        long loadElapsedMs = (System.nanoTime() - pendingSchematicStartedAtNanos) / 1_000_000L;
                        send(server, "Loaded schematic in " + loadElapsedMs + "ms (thread=" + Thread.currentThread().getName() + ")");
                    }

                    if (pasteAnchorTo == null) {
                        BlockPos centeredTarget = new BlockPos(
                            targetCenter.getX(),
                            targetCenter.getY() + config.yOffsetBlocks(),
                            targetCenter.getZ()
                        );
                        pasteAnchorTo = loadedSchematic.computeCenteredAnchor(centeredTarget);
                        send(server, "Construct center: x=" + centeredTarget.getX() + " y=" + centeredTarget.getY() + " z=" + centeredTarget.getZ());
                    }

                    var placement = loadedSchematic.computePlacementBounds(pasteAnchorTo);
                    updateOverallBounds(placement.minX(), placement.minY(), placement.minZ(), placement.maxX(), placement.maxY(), placement.maxZ());
                    
                    if (ejectPlayersFromBuildArea(server, placement.minX(), placement.minY(), placement.minZ(), placement.maxX(), placement.maxY(), placement.maxZ())) {
                        return false;
                    }

                    if (!preflightDone) {
                        runConstructPreflight(placement.minX(), placement.minZ(), placement.maxX(), placement.maxZ());
                        preflightDone = true;
                    }

                    if (loadedChunkPlan == null && pendingChunkPlan == null) {
                        SpongeV3Schematic schematicForPlan = loadedSchematic;
                        pendingChunkPlan = CompletableFuture.supplyAsync(() -> buildChunkPlan(schematicForPlan), ioExecutor);
                        return false;
                    }

                    if (loadedChunkPlan == null) {
                        if (!pendingChunkPlan.isDone()) {
                            return false;
                        }

                        try {
                            loadedChunkPlan = pendingChunkPlan.join();
                        } catch (Exception e) {
                            send(server, "Failed to prepare target chunks for schematic: " + rootMessage(e));
                            pendingChunkPlan = null;
                            stage = Stage.DONE;
                            break;
                        }
                        pendingChunkPlan = null;
                    }

                    if (!loadedChunkPlan.missingChunks.isEmpty()) {
                        send(server, "Preparing " + loadedChunkPlan.missingChunks.size() + " missing chunk(s) for schematic pass");
                        chunkPrepStartedAtNanos = System.nanoTime();
                        chunkPrepExpectedMissingCount = loadedChunkPlan.missingChunks.size();
                        chunkPrepStatusLogTicks = 0;
                        chunkGenerationScheduler.reset(loadedChunkPlan.missingChunks);
                        chunkPrepObservedLoadedKeys.clear();
                        setUnloadChunkKeys(Set.of());
                        pendingMissingAfterUnload = null;
                        stage = Stage.PREPARE_MISSING_CHUNKS;
                        return false;
                    }

                    setUnloadChunkKeys(loadedChunkPlan.targetChunkKeys);
                    pendingMissingAfterUnload = null;
                    int loadedTargetChunks = countLoadedTargetChunks(unloadChunkKeys);
                    if (loadedTargetChunks > 0) {
                        send(server, "Waiting for " + loadedTargetChunks + " target chunk(s) to unload before schematic apply");
                        persistRunStateAsync("WAIT_FOR_TARGET_CHUNKS_UNLOAD");
                        stage = Stage.WAIT_FOR_TARGET_CHUNKS_UNLOAD;
                        return false;
                    }

                    SpongeV3Schematic schematicToApply = loadedSchematic;
                    int currentPass = passIndex;
                    send(server, "Applying schematic pass");
                    applyStatusLogTicks = 0;
                    applyStartedAtNanos = System.nanoTime();
                    pendingApply = CompletableFuture.supplyAsync(() -> {
                        long startedAt = System.nanoTime();
                        AtlantisMod.LOGGER.info("[construct:{}] apply start schematic pass thread={}", undoRunId, Thread.currentThread().getName());
                        PassApplyResult result = applyPass(currentPass, schematicToApply);
                        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                        AtlantisMod.LOGGER.info("[construct:{}] apply done schematic pass elapsed={}ms chunks={} writes={} undoEntries={}",
                            undoRunId,
                            elapsedMs,
                            result.chunkCount,
                            result.writeCount,
                            result.undoEntryCount
                        );
                        return result;
                    }, ioExecutor);
                    stage = Stage.APPLY_SCHEMATIC_ASYNC;
                }

                case PREPARE_MISSING_CHUNKS -> {
                    if (loadedSchematic == null || loadedChunkPlan == null) {
                        stage = Stage.LOAD_SCHEMATIC_ASYNC;
                        break;
                    }

                    var placement = loadedSchematic.computePlacementBounds(pasteAnchorTo);
                    if (ejectPlayersFromBuildArea(server, placement.minX(), placement.minY(), placement.minZ(), placement.maxX(), placement.maxY(), placement.maxZ())) {
                        return false;
                    }

                    boolean prepDone = chunkGenerationScheduler.tickGenerate(world, CHUNK_PREP_BUDGET_PER_TICK);
                    Set<Long> observedLoadedNow = chunkGenerationScheduler.consumeObservedLoadedChunkKeys();
                    if (!observedLoadedNow.isEmpty()) {
                        chunkPrepObservedLoadedKeys.addAll(observedLoadedNow);
                    }

                    Set<Long> recentlyGeneratedNow = chunkGenerationScheduler.consumeRecentlyGeneratedChunkKeys();
                    Set<Long> immediateUnloadKeys = mergeChunkKeySets(recentlyGeneratedNow, observedLoadedNow);
                    if (!immediateUnloadKeys.isEmpty()) {
                        markLoadedTargetChunksNeedSaving(immediateUnloadKeys);
                        forceUnloadChunkKeysNow(immediateUnloadKeys);
                    }

                    if (!prepDone) {
                        chunkPrepStatusLogTicks++;
                        if (chunkPrepStatusLogTicks % 20 == 0) {
                            long elapsedMs = (System.nanoTime() - chunkPrepStartedAtNanos) / 1_000_000L;
                            send(server, "Chunk prep running: elapsed=" + elapsedMs + "ms, pending=" + chunkGenerationScheduler.pendingCount() + ", active=" + chunkGenerationScheduler.activeCount());
                        }
                        return false;
                    }

                    long elapsedMs = (System.nanoTime() - chunkPrepStartedAtNanos) / 1_000_000L;
                    send(server, "Chunk prep complete: requested=" + chunkPrepExpectedMissingCount + ", elapsed=" + elapsedMs + "ms");

                    setUnloadChunkKeys(mergeChunkKeySets(loadedChunkPlan.targetChunkKeys, chunkPrepObservedLoadedKeys));
                    chunkPrepObservedLoadedKeys.clear();
                    pendingMissingAfterUnload = null;
                    send(server, "Chunk prep unload set prepared: targetChunks=" + loadedChunkPlan.targetChunkKeys.size() + ", unloadSet=" + unloadChunkKeys.size());

                    // Non-blocking path: mark loaded targets dirty and verify NBT after unload.
                    markLoadedTargetChunksNeedSaving(loadedChunkPlan.targetChunkKeys);
                    send(server, "Chunk prep save pass skipped (non-blocking); will verify after unload");

                    chunkGenerationScheduler.releaseTickets(world);
                    persistRunStateAsync("WAIT_FOR_TARGET_CHUNKS_UNLOAD");
                    stage = Stage.WAIT_FOR_TARGET_CHUNKS_UNLOAD;
                    return false;
                }

                case WAIT_FOR_TARGET_CHUNKS_UNLOAD -> {
                    if (loadedSchematic == null) {
                        stage = Stage.LOAD_SCHEMATIC_ASYNC;
                        break;
                    }

                    var placement = loadedSchematic.computePlacementBounds(pasteAnchorTo);
                    if (ejectPlayersFromBuildArea(server, placement.minX(), placement.minY(), placement.minZ(), placement.maxX(), placement.maxY(), placement.maxZ())) {
                        return false;
                    }

                    int loadedTargetChunks = loadedChunkPlan != null
                        ? countLoadedTargetChunks(unloadChunkKeys)
                        : 0;
                    if (loadedTargetChunks > 0) {
                        unloadWaitTicks++;
                        if (unloadWaitTicks % UNLOAD_FORCE_INTERVAL_TICKS == 0) {
                            forceUnloadTargetChunks(unloadChunkKeys);
                        }
                        if (unloadWaitTicks % UNLOAD_STATUS_LOG_INTERVAL_TICKS == 0) {
                            send(server, "Unload wait running: loadedChunks=" + loadedTargetChunks + ", unloadSet=" + unloadChunkKeys.size() + ", waitTicks=" + unloadWaitTicks);
                        }
                        return false;
                    }
                    unloadWaitTicks = 0;

                    if (loadedChunkPlan != null) {
                        if (pendingMissingAfterUnload == null) {
                            Set<Long> keysToVerify = loadedChunkPlan.targetChunkKeys;
                            pendingMissingAfterUnload = CompletableFuture.supplyAsync(() -> findMissingChunkNbts(keysToVerify), ioExecutor);
                            return false;
                        }

                        if (!pendingMissingAfterUnload.isDone()) {
                            return false;
                        }

                        List<ChunkPos> missingAfterUnload;
                        try {
                            missingAfterUnload = pendingMissingAfterUnload.join();
                        } catch (Exception e) {
                            send(server, "Chunk prep verify failed: " + rootMessage(e));
                            stage = Stage.DONE;
                            return false;
                        } finally {
                            pendingMissingAfterUnload = null;
                        }

                        if (!missingAfterUnload.isEmpty()) {
                            send(server, "Chunk prep failed: " + missingAfterUnload.size() + " chunk(s) still missing NBT after unload");
                            stage = Stage.DONE;
                            return false;
                        }
                    }

                    int currentPass = passIndex;
                    SpongeV3Schematic schematicToApply = loadedSchematic;
                    send(server, "Applying schematic pass");
                    applyStatusLogTicks = 0;
                    applyStartedAtNanos = System.nanoTime();
                    pendingApply = CompletableFuture.supplyAsync(() -> applyPass(currentPass, schematicToApply), ioExecutor);
                    stage = Stage.APPLY_SCHEMATIC_ASYNC;
                }

                case APPLY_SCHEMATIC_ASYNC -> {
                    if (pendingApply == null) {
                        stage = Stage.LOAD_SCHEMATIC_ASYNC;
                        break;
                    }

                    if (!pendingApply.isDone()) {
                        applyStatusLogTicks++;
                        if (applyStatusLogTicks % APPLY_STATUS_LOG_INTERVAL_TICKS == 0) {
                            long elapsedMs = (System.nanoTime() - applyStartedAtNanos) / 1_000_000L;
                            send(server, "Schematic apply running: elapsed=" + elapsedMs + "ms");
                        }
                        if (loadedSchematic != null && pasteAnchorTo != null) {
                            var placement = loadedSchematic.computePlacementBounds(pasteAnchorTo);
                            ejectPlayersFromBuildArea(server, placement.minX(), placement.minY(), placement.minZ(), placement.maxX(), placement.maxY(), placement.maxZ());
                        }
                        return false;
                    }

                    PassApplyResult result;
                    try {
                        result = pendingApply.join();
                    } catch (Exception e) {
                        send(server, "Construct apply failed: " + rootMessage(e));
                        AtlantisMod.LOGGER.error("Construct apply failed", e);
                        pendingApply = null;
                        stage = Stage.DONE;
                        break;
                    }

                    pendingApply = null;
                    applyStatusLogTicks = 0;
                    applyStartedAtNanos = 0L;
                    loadedSchematic = null;
                    loadedChunkPlan = null;
                    pendingChunkPlan = null;
                    preflightDone = false;
                    unloadWaitTicks = 0;
                    setUnloadChunkKeys(Set.of());
                    pendingMissingAfterUnload = null;
                    beginProtectionMerge(result);
                    persistRunStateAsync("MERGE_PROTECTION");
                    stage = Stage.MERGE_PROTECTION;
                    return false;
                }

                case MERGE_PROTECTION -> {
                    if (!tickProtectionMerge(server)) {
                        return false;
                    }

                    PassApplyResult result = pendingProtectionMergeResult;
                    pendingProtectionMergeResult = null;
                    pendingPlacedMergeIterator = null;
                    pendingInteriorMergeIterator = null;
                    protectionMergeStatusLogTicks = 0;
                    protectionMergeStartedAtNanos = 0L;

                    if (result != null && result.undoFileName != null) {
                        undoFiles.add(result.undoFileName);
                        writeUndoMetadataSafely();
                    }

                    passIndex = 1;
                    persistRunStateAsync("FINALIZE");
                    stage = Stage.FINALIZE;
                }

                case FINALIZE -> {
                    finalizeProtectionOnce();
                    markRunCompletedAsync();
                    releaseSpawnPauseForce();
                    send(server, "Construct complete.");
                    stage = Stage.DONE;
                }

                case DONE -> {
                    return true;
                }
            }
        }

        return stage == Stage.DONE;
    }

    @Override
    public void onError(MinecraftServer server, Throwable throwable) {
        releaseChunkGenerationTickets();
        releaseSpawnPauseForce();
        AtlantisMod.LOGGER.error("Construct crashed", throwable);
        send(server, "Construct crashed: " + rootMessage(throwable));
    }

    @Override
    public void onCancel(MinecraftServer server, String reason) {
        releaseChunkGenerationTickets();
        releaseSpawnPauseForce();
        if (reason != null && !reason.isBlank()) {
            send(server, "Construct cancelled: " + reason);
        } else {
            send(server, "Construct cancelled.");
        }
    }

    private PassApplyResult applyPass(int passIndex, SpongeV3Schematic schematic) {
        if (pasteAnchorTo == null) {
            throw new IllegalStateException("Paste anchor not initialized");
        }
        Path undoFile = UndoPaths.constructUndoFile(undoRunDir);
        SpongeV3Schematic.StreamApplyResult streamResult;
        String undoFileName;
        try (UndoFileFormat.StreamWriter undoWriter = UndoFileFormat.openStreamWriter(undoFile, world.getRegistryKey().getValue().toString())) {
            streamResult = schematic.streamApply(world, pasteAnchorTo, undoWriter::write);
            undoFileName = undoFile.getFileName().toString();
        } catch (Exception e) {
            AtlantisMod.LOGGER.error("Failed to persist undo for construct pass", e);
            throw new IllegalStateException("Failed to persist undo for construct pass", e);
        }

        return new PassApplyResult(
            undoFileName,
            streamResult.placedKeys(),
            streamResult.interiorKeys(),
            streamResult.chunkCount(),
            streamResult.writeCount(),
            streamResult.undoEntryCount()
        );
    }

    private void beginProtectionMerge(PassApplyResult result) {
        pendingProtectionMergeResult = result;
        pendingPlacedMergeIterator = result != null && result.placedKeys != null
            ? result.placedKeys.iterator()
            : null;
        pendingInteriorMergeIterator = result != null && result.interiorKeys != null
            ? result.interiorKeys.iterator()
            : null;
        pendingPlacedMergeRemaining = result != null && result.placedKeys != null
            ? result.placedKeys.size()
            : 0;
        pendingInteriorMergeRemaining = result != null && result.interiorKeys != null
            ? result.interiorKeys.size()
            : 0;
        protectionMergeStatusLogTicks = 0;
        protectionMergeStartedAtNanos = System.nanoTime();

        int total = pendingPlacedMergeRemaining + pendingInteriorMergeRemaining;
        int estimatedTicks = total <= 0
            ? 0
            : (int) Math.ceil(total / (double) Math.max(1, PROTECTION_MERGE_ADD_BUDGET_PER_TICK));
        AtlantisMod.LOGGER.info(
            "[construct:{}] protection merge start: placed={} interior={} total={} budgetPerTick={} estimatedTicks≈{}",
            undoRunId,
            pendingPlacedMergeRemaining,
            pendingInteriorMergeRemaining,
            total,
            PROTECTION_MERGE_ADD_BUDGET_PER_TICK,
            estimatedTicks
        );
    }

    private boolean tickProtectionMerge(MinecraftServer server) {
        if (pendingProtectionMergeResult == null) {
            return true;
        }

        int budget = PROTECTION_MERGE_ADD_BUDGET_PER_TICK;

        while (budget > 0 && pendingPlacedMergeIterator != null && pendingPlacedMergeIterator.hasNext()) {
            protectionCollector.addPlaced(pendingPlacedMergeIterator.next());
            if (pendingPlacedMergeRemaining > 0) {
                pendingPlacedMergeRemaining--;
            }
            budget--;
        }

        while (budget > 0 && pendingInteriorMergeIterator != null && pendingInteriorMergeIterator.hasNext()) {
            protectionCollector.addInterior(pendingInteriorMergeIterator.next());
            if (pendingInteriorMergeRemaining > 0) {
                pendingInteriorMergeRemaining--;
            }
            budget--;
        }

        boolean placedDone = pendingPlacedMergeIterator == null || !pendingPlacedMergeIterator.hasNext();
        boolean interiorDone = pendingInteriorMergeIterator == null || !pendingInteriorMergeIterator.hasNext();
        if (placedDone && interiorDone) {
            AtlantisMod.LOGGER.info("[construct:{}] protection merge complete: placed={} interior={}",
                undoRunId,
                pendingPlacedMergeRemaining,
                pendingInteriorMergeRemaining
            );
            return true;
        }

        protectionMergeStatusLogTicks++;
        if (protectionMergeStatusLogTicks % PROTECTION_MERGE_STATUS_LOG_INTERVAL_TICKS == 0) {
            long elapsedMs = (System.nanoTime() - protectionMergeStartedAtNanos) / 1_000_000L;
            send(server,
                "Protection merge running: elapsed=" + elapsedMs
                    + "ms, stage=" + (placedDone ? "interior" : "placed")
                    + ", remainingPlaced=" + pendingPlacedMergeRemaining
                    + ", remainingInterior=" + pendingInteriorMergeRemaining);
        }
        return false;
    }

    private void updateOverallBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (overallMin == null || overallMax == null) {
            overallMin = new BlockPos(minX, minY, minZ);
            overallMax = new BlockPos(maxX, maxY, maxZ);
            return;
        }

        overallMin = new BlockPos(
            Math.min(overallMin.getX(), minX),
            Math.min(overallMin.getY(), minY),
            Math.min(overallMin.getZ(), minZ)
        );
        overallMax = new BlockPos(
            Math.max(overallMax.getX(), maxX),
            Math.max(overallMax.getY(), maxY),
            Math.max(overallMax.getZ(), maxZ)
        );
    }

    private int countLoadedTargetChunks(Set<Long> targetChunkKeys) {
        if (targetChunkKeys == null || targetChunkKeys.isEmpty()) {
            return 0;
        }

        int loaded = 0;
        for (long key : targetChunkKeys) {
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                loaded++;
            }
        }
        return loaded;
    }

    private void markLoadedTargetChunksNeedSaving(Set<Long> targetChunkKeys) {
        if (targetChunkKeys == null || targetChunkKeys.isEmpty()) {
            return;
        }

        int saved = 0;
        for (long key : targetChunkKeys) {
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                continue;
            }

            ((ServerChunkManagerAccessor) world.getChunkManager().chunkLoadingManager).atlantis$markChunkNeedsSaving(new ChunkPos(chunkX, chunkZ));
            saved++;
        }

        if (saved > 0) {
            AtlantisMod.LOGGER.info("[construct:{}] requested immediate save for {} loaded target chunk(s).", undoRunId, saved);
        }
    }

    private ChunkPlan buildChunkPlan(SpongeV3Schematic schematic) {
        if (schematic == null || pasteAnchorTo == null) {
            return new ChunkPlan(Set.of(), List.of());
        }
        var prepass = schematic.runPrepass(pasteAnchorTo);
        Set<Long> targetChunkKeys = prepass.targetChunkKeys();

        List<ChunkPos> missingChunks = new ArrayList<>();
        missingChunks.addAll(findMissingChunkNbts(targetChunkKeys));

        return new ChunkPlan(targetChunkKeys, List.copyOf(missingChunks));
    }

    private List<ChunkPos> findMissingChunkNbts(Set<Long> targetChunkKeys) {
        List<ChunkPos> missingChunks = new ArrayList<>();
        if (targetChunkKeys == null || targetChunkKeys.isEmpty()) {
            return missingChunks;
        }

        for (long key : targetChunkKeys) {
            ChunkPos chunkPos = new ChunkPos(ChunkPos.getPackedX(key), ChunkPos.getPackedZ(key));
            if (!UnloadedChunkNbtEditor.hasChunkNbt(world, chunkPos)) {
                missingChunks.add(chunkPos);
            }
        }
        return missingChunks;
    }

    private void releaseChunkGenerationTickets() {
        try {
            chunkGenerationScheduler.releaseTickets(world);
        } catch (Exception ignored) {
        }
    }

    private void setUnloadChunkKeys(Set<Long> keys) {
        if (keys == null || keys.isEmpty()) {
            unloadChunkKeys = Set.of();
            unloadChunkKeysArray = new long[0];
            unloadChunkKeysCursor = 0;
            return;
        }

        unloadChunkKeys = Set.copyOf(keys);
        unloadChunkKeysArray = unloadChunkKeys.stream().mapToLong(Long::longValue).toArray();
        unloadChunkKeysCursor = 0;
    }

    private void forceUnloadTargetChunks(Set<Long> targetChunkKeys) {
        if (targetChunkKeys == null || targetChunkKeys.isEmpty()) {
            return;
        }

        if (unloadChunkKeysArray.length == 0) {
            // Fallback: should normally be kept in sync via setUnloadChunkKeys().
            setUnloadChunkKeys(targetChunkKeys);
        }

        int budget = Math.min(UNLOAD_FORCE_CHUNK_BUDGET, unloadChunkKeysArray.length);
        for (int i = 0; i < budget; i++) {
            int idx = unloadChunkKeysCursor++;
            if (idx < 0) {
                idx = 0;
                unloadChunkKeysCursor = 1;
            }

            long key = unloadChunkKeysArray[idx % unloadChunkKeysArray.length];
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);

            world.setChunkForced(chunkX, chunkZ, false);

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            for (ChunkTicketType ticketType : DISCOVERED_TICKET_TYPES) {
                for (int level = MIN_TICKET_LEVEL; level <= MAX_TICKET_LEVEL; level++) {
                    try {
                        world.getChunkManager().removeTicket(ticketType, pos, level);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private void forceUnloadChunkKeysNow(Set<Long> chunkKeys) {
        if (chunkKeys == null || chunkKeys.isEmpty()) {
            return;
        }

        for (long key : chunkKeys) {
            int chunkX = ChunkPos.getPackedX(key);
            int chunkZ = ChunkPos.getPackedZ(key);

            world.setChunkForced(chunkX, chunkZ, false);

            ChunkPos pos = new ChunkPos(chunkX, chunkZ);
            for (ChunkTicketType ticketType : DISCOVERED_TICKET_TYPES) {
                for (int level = MIN_TICKET_LEVEL; level <= MAX_TICKET_LEVEL; level++) {
                    try {
                        world.getChunkManager().removeTicket(ticketType, pos, level);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    private static List<ChunkTicketType> discoverChunkTicketTypes() {
        List<ChunkTicketType> types = new ArrayList<>();
        for (Field field : ChunkTicketType.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!ChunkTicketType.class.isAssignableFrom(field.getType())) {
                continue;
            }
            try {
                field.setAccessible(true);
                Object value = field.get(null);
                if (value instanceof ChunkTicketType ticketType) {
                    types.add(ticketType);
                }
            } catch (Exception ignored) {
            }
        }

        AtlantisMod.LOGGER.info("[construct] discovered {} chunk ticket type(s) for unload forcing.", types.size());
        return List.copyOf(types);
    }

    private boolean ejectPlayersFromBuildArea(MinecraftServer server, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int configuredMargin = Math.max(0, config.playerEjectMarginBlocks());
        int viewDistanceChunks = server.getPlayerManager().getViewDistance();
        int simulationDistanceChunks = server.getPlayerManager().getSimulationDistance();
        int chunkLoadRadiusBlocks = Math.max(viewDistanceChunks, simulationDistanceChunks) * 16;
        int margin = Math.max(configuredMargin, chunkLoadRadiusBlocks + 32);
        int offset = Math.max(64, config.playerEjectTeleportOffsetBlocks() + chunkLoadRadiusBlocks);

        Box box = new Box(
            minX - margin,
            minY - margin,
            minZ - margin,
            maxX + margin + 1,
            maxY + margin + 1,
            maxZ + margin + 1
        );

        int ejectedCount = 0;
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getEntityWorld() != world) {
                continue;
            }

            if (!box.contains(player.getX(), player.getY(), player.getZ())) {
                continue;
            }

            double px = player.getX();
            double pz = player.getZ();
            double dxLeft = Math.abs(px - minX);
            double dxRight = Math.abs(px - maxX);
            double dzFront = Math.abs(pz - minZ);
            double dzBack = Math.abs(pz - maxZ);

            double best = dxLeft;
            int tx = minX - margin - offset;
            int tz = (int) Math.floor(pz);

            if (dxRight < best) {
                best = dxRight;
                tx = maxX + margin + offset;
                tz = (int) Math.floor(pz);
            }
            if (dzFront < best) {
                best = dzFront;
                tx = (int) Math.floor(px);
                tz = minZ - margin - offset;
            }
            if (dzBack < best) {
                tx = (int) Math.floor(px);
                tz = maxZ + margin + offset;
            }

            int safeY = Math.max(world.getBottomY() + 1, player.getBlockY());
            BlockPos target = new BlockPos(tx, safeY, tz);

            player.teleport(
                world,
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                Set.of(PositionFlag.DELTA_X, PositionFlag.DELTA_Y, PositionFlag.DELTA_Z),
                player.getYaw(),
                player.getPitch(),
                true
            );
            ejectedCount++;
        }

        if (ejectedCount > 0) {
            AtlantisMod.LOGGER.info("[construct:{}] ejected {} player(s) from keepout (margin={} offset={} view={} sim={})",
                undoRunId,
                ejectedCount,
                margin,
                offset,
                viewDistanceChunks,
                simulationDistanceChunks
            );
            return true;
        }
        return false;
    }

    private void runConstructPreflight(int minX, int minZ, int maxX, int maxZ) {
        acquireSpawnPauseIfNeeded();

        ActiveConstructBounds bounds = new ActiveConstructBounds(
            undoRunId,
            world.getRegistryKey().getValue().toString(),
            overallMin.getX(),
            overallMin.getY(),
            overallMin.getZ(),
            overallMax.getX(),
            overallMax.getY(),
            overallMax.getZ()
        );
        ProximityMobManager.getInstance().clearWithinBounds(world, bounds);
        AtlantisMod.LOGGER.info("[construct:{}] preflight: custom spawning paused and Atlantis mobs cleared in construct bounds.", undoRunId);

        forceReleaseChunksInBounds(minX, minZ, maxX, maxZ);
    }

    private void forceReleaseChunksInBounds(int minX, int minZ, int maxX, int maxZ) {
        int minChunkX = minX >> 4;
        int maxChunkX = maxX >> 4;
        int minChunkZ = minZ >> 4;
        int maxChunkZ = maxZ >> 4;

        int total = 0;
        int loadedBefore = 0;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                total++;
                if (world.getChunkManager().isChunkLoaded(chunkX, chunkZ)) {
                    loadedBefore++;
                }
                world.setChunkForced(chunkX, chunkZ, false);
            }
        }

        AtlantisMod.LOGGER.info("[construct:{}] preflight: force-release requested for {} chunk(s), loadedBefore={}", undoRunId, total, loadedBefore);
    }

    private void acquireSpawnPauseIfNeeded() {
        if (spawnPauseAcquired) {
            return;
        }
        ProximityMobManager.getInstance().acquireExternalPause("construct:" + undoRunId);
        spawnPauseAcquired = true;
    }

    private void releaseSpawnPauseIfNeeded() {
        if (!spawnPauseAcquired) {
            return;
        }
        ProximityMobManager.getInstance().releaseExternalPause("construct:" + undoRunId);
        spawnPauseAcquired = false;
    }

    public void releaseSpawnPauseForce() {
        // Defensive: Always release the pause token for this construct, regardless of flag state
        String token = "construct:" + undoRunId;
        ProximityMobManager.getInstance().releaseExternalPause(token);
        spawnPauseAcquired = false;
        AtlantisMod.LOGGER.info("[construct:{}] spawn pause force-released token={}", undoRunId, token);
    }

    private void finalizeProtectionOnce() {
        if (protectionRegistered) {
            return;
        }

        if (protectionCollector.isEmpty()) {
            protectionRegistered = true;
            return;
        }

        var entry = protectionCollector.buildEntry();
        ProtectionManager.INSTANCE.register(entry);

        // Write protection file asynchronously to avoid blocking server thread with large I/O
        CompletableFuture.runAsync(() -> {
            try {
                Path file = ProtectionPaths.protectionFileForRun(undoRunId);
                ProtectionFileIO.write(file, entry);
            } catch (Exception e) {
                AtlantisMod.LOGGER.warn("Failed to persist protection for run {}: {}", undoRunId, e.getMessage());
            }
        }, ioExecutor);

        protectionRegistered = true;
    }

    private void markRunCompletedAsync() {
        if (runCompleted) {
            return;
        }
        runCompleted = true;
        persistRunStateAsync("DONE");
    }

    private void persistRunStateAsync(String stageName) {
        ConstructRunState snapshot = new ConstructRunState(
            ConstructRunState.CURRENT_VERSION,
            undoRunId,
            world.getRegistryKey().getValue().toString(),
            parseRunIdMillisSafe(undoRunId),
            targetCenter.getX(),
            targetCenter.getY(),
            targetCenter.getZ(),
            config.yOffsetBlocks(),
            "SOLIDS",
            passIndex,
            stageName,
            pasteAnchorTo != null ? pasteAnchorTo.getX() : null,
            pasteAnchorTo != null ? pasteAnchorTo.getY() : null,
            pasteAnchorTo != null ? pasteAnchorTo.getZ() : null,
            overallMin != null ? overallMin.getX() : null,
            overallMin != null ? overallMin.getY() : null,
            overallMin != null ? overallMin.getZ() : null,
            overallMax != null ? overallMax.getX() : null,
            overallMax != null ? overallMax.getY() : null,
            overallMax != null ? overallMax.getZ() : null,
            runCompleted
        );

        CompletableFuture.runAsync(() -> {
            try {
                ConstructRunStateIO.write(runStateFile, snapshot);
            } catch (Exception e) {
                AtlantisMod.LOGGER.debug("Failed to persist construct run state: {}", e.getMessage());
            }
        }, ioExecutor);
    }

    private void preloadExistingUndoFiles() {
        try {
            Path metadataFile = UndoPaths.metadataFile(undoRunDir);
            if (Files.exists(metadataFile)) {
                UndoRunMetadata metadata = UndoMetadataIO.read(metadataFile);
                if (metadata != null && metadata.undoFiles() != null) {
                    undoFiles.addAll(metadata.undoFiles());
                }
            }
        } catch (Exception ignored) {
        }
    }

    private void writeUndoMetadataSafely() {
        try {
            UndoRunMetadata metadata = new UndoRunMetadata(
                UndoRunMetadata.CURRENT_VERSION,
                undoRunId,
                world.getRegistryKey().getValue().toString(),
                parseRunIdMillisSafe(undoRunId),
                config.yOffsetBlocks(),
                targetCenter.getX(),
                targetCenter.getY(),
                targetCenter.getZ(),
                List.copyOf(undoFiles)
            );
            UndoMetadataIO.write(UndoPaths.metadataFile(undoRunDir), metadata);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to write undo metadata for run {}: {}", undoRunId, e.getMessage());
        }
    }

    private void send(MinecraftServer server, String message) {
        AtlantisMod.LOGGER.info("[construct:{}] {}", undoRunId, message);

        if (requesterId != null && server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requesterId);
            if (player != null) {
                player.sendMessage(Text.literal(message), false);
            }
        }
    }

    private static long parseRunIdMillisSafe(String runId) {
        try {
            return Long.parseLong(runId);
        } catch (Exception ignored) {
            return System.currentTimeMillis();
        }
    }

    private static String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String rootMessage(Throwable throwable) {
        Throwable root = throwable;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String msg = root.getMessage();
        return (msg == null || msg.isBlank()) ? root.getClass().getSimpleName() : msg;
    }

    private static Set<Long> mergeChunkKeySets(Set<Long> first, Set<Long> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return Set.of();
        }
        if (first == null || first.isEmpty()) {
            return Set.copyOf(second);
        }
        if (second == null || second.isEmpty()) {
            return Set.copyOf(first);
        }
        Set<Long> merged = new HashSet<>(first);
        merged.addAll(second);
        return Set.copyOf(merged);
    }

    private static final class BlockPlacement {
        private final int x;
        private final int y;
        private final int z;
        private final String blockString;
        private final String blockEntitySnbt;

        private BlockPlacement(int x, int y, int z, String blockString, String blockEntitySnbt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockString = blockString;
            this.blockEntitySnbt = blockEntitySnbt;
        }
    }

    private static final class PassApplyResult {
        private final String undoFileName;
        private final Set<Long> placedKeys;
        private final Set<Long> interiorKeys;
        private final int chunkCount;
        private final int writeCount;
        private final int undoEntryCount;

        private PassApplyResult(String undoFileName, Set<Long> placedKeys, Set<Long> interiorKeys, int chunkCount, int writeCount, int undoEntryCount) {
            this.undoFileName = undoFileName;
            this.placedKeys = placedKeys;
            this.interiorKeys = interiorKeys;
            this.chunkCount = chunkCount;
            this.writeCount = writeCount;
            this.undoEntryCount = undoEntryCount;
        }
    }

    private static final class ChunkPlan {
        private final Set<Long> targetChunkKeys;
        private final List<ChunkPos> missingChunks;

        private ChunkPlan(Set<Long> targetChunkKeys, List<ChunkPos> missingChunks) {
            this.targetChunkKeys = targetChunkKeys;
            this.missingChunks = missingChunks;
        }
    }
}
