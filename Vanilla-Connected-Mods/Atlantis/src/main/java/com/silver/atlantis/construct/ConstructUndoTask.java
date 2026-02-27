package com.silver.atlantis.construct;

import com.silver.atlantis.AtlantisMod;
import com.silver.atlantis.construct.undo.UndoMetadataIO;
import com.silver.atlantis.construct.undo.UndoPaths;
import com.silver.atlantis.construct.undo.UndoRunMetadata;
import com.silver.atlantis.construct.undo.UndoFileFormat;
import com.silver.atlantis.construct.mixin.ServerChunkManagerAccessor;
import com.silver.atlantis.construct.state.ConstructRunState;
import com.silver.atlantis.construct.state.ConstructRunStateIO;
import com.silver.atlantis.construct.state.ConstructStatePaths;
import com.silver.atlantis.protect.ProtectionManager;
import com.silver.atlantis.spawn.bounds.ActiveConstructBounds;
import com.silver.atlantis.spawn.service.ProximityMobManager;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

final class ConstructUndoTask implements ConstructJob {

    private enum Stage {
        LOAD_METADATA,
        LOAD_UNDO_ASYNC,
        PREPARE_MISSING_CHUNKS,
        WAIT_FOR_TARGET_CHUNKS_UNLOAD,
        APPLY_UNDO_ASYNC,
        DELETE_RUN_ASYNC,
        DONE
    }

    private final UUID requesterId;
    private final ConstructConfig config;
    private final MinecraftServer server;
    private final String requestedRunId;
    private final Executor ioExecutor;

    private Stage stage = Stage.LOAD_METADATA;

    private UndoRunMetadata metadata;
    private ServerWorld world;
    private Path runDir;
    private Path undoFile;

    private int passIndex;

    private CompletableFuture<UndoFileFormat.UndoDataIndexed> pendingUndoData;
    private CompletableFuture<UndoChunkPlan> pendingChunkPlan;
    private CompletableFuture<Void> pendingApply;
    private CompletableFuture<Void> pendingDelete;
    private UndoFileFormat.UndoDataIndexed loadedUndoData;
    private UndoChunkPlan loadedChunkPlan;

    private final ConstructChunkGenerationScheduler chunkGenerationScheduler = new ConstructChunkGenerationScheduler(31);

    private static final int CHUNK_PREP_BUDGET_PER_TICK = 1;
    private static final int UNLOAD_FORCE_INTERVAL_TICKS = 5;
    private static final int UNLOAD_STATUS_LOG_INTERVAL_TICKS = 20;
    private static final int UNLOAD_FORCE_CHUNK_BUDGET = 8;
    private static final int MIN_TICKET_LEVEL = 0;
    private static final int MAX_TICKET_LEVEL = 40;
    private static final List<ChunkTicketType> DISCOVERED_TICKET_TYPES = discoverChunkTicketTypes();

    private long chunkPrepStartedAtNanos;
    private int chunkPrepExpectedMissingCount;
    private int chunkPrepStatusLogTicks;
    private int unloadWaitTicks;
    private Set<Long> unloadChunkKeys = Set.of();
    private final Set<Long> chunkPrepObservedLoadedKeys = new java.util.HashSet<>();
    private long[] unloadChunkKeysArray = new long[0];
    private int unloadChunkKeysCursor;

    private CompletableFuture<List<ChunkPos>> pendingMissingAfterUnload;

    private boolean protectionUnregistered;
    private boolean undoPreflightDone;
    private boolean spawnPauseAcquired;
    private Integer undoMinX;
    private Integer undoMinY;
    private Integer undoMinZ;
    private Integer undoMaxX;
    private Integer undoMaxY;
    private Integer undoMaxZ;

    ConstructUndoTask(ServerCommandSource source, ConstructConfig config, MinecraftServer server, String runIdOrNull, Executor ioExecutor) {
        this.requesterId = source.getPlayer() != null ? source.getPlayer().getUuid() : null;
        this.config = config;
        this.server = server;
        this.requestedRunId = runIdOrNull;
        this.ioExecutor = ioExecutor;

        send("Undo started.");
    }

    String getRunId() {
        if (metadata != null) {
            return metadata.runId();
        }
        return requestedRunId;
    }

    @Override
    public boolean tick(MinecraftServer ignored) {
        if (stage == Stage.DONE) {
            return true;
        }

        switch (stage) {
            case LOAD_METADATA -> loadMetadata();
            case LOAD_UNDO_ASYNC -> loadUndoAsync();
            case PREPARE_MISSING_CHUNKS -> prepareMissingChunks();
            case WAIT_FOR_TARGET_CHUNKS_UNLOAD -> waitForTargetChunksUnload();
            case APPLY_UNDO_ASYNC -> applyUndoAsync();
            case DELETE_RUN_ASYNC -> deleteRunAsync();
            case DONE -> {
                return true;
            }
        }

        return stage == Stage.DONE;
    }

    private void loadMetadata() {
        String runId = requestedRunId;
        if (runId == null || runId.isBlank()) {
            runId = UndoPaths.findLatestUsableRunIdOrNull();
        }

        if (runId == null || runId.isBlank()) {
            send("No undo history found.");
            stage = Stage.DONE;
            return;
        }

        runDir = UndoPaths.runDir(runId);
        Path metadataFile = UndoPaths.metadataFile(runDir);
        if (!Files.exists(metadataFile)) {
            send("Undo metadata not found for run: " + runId);
            stage = Stage.DONE;
            return;
        }

        try {
            metadata = UndoMetadataIO.read(metadataFile);
        } catch (Exception e) {
            send("Failed to read undo metadata: " + rootMessage(e));
            stage = Stage.DONE;
            return;
        }

        world = server.getWorld(net.minecraft.registry.RegistryKey.of(
            net.minecraft.registry.RegistryKeys.WORLD,
            Identifier.of(metadata.dimension())
        ));
        if (world == null) {
            send("World not loaded for dimension: " + metadata.dimension());
            stage = Stage.DONE;
            return;
        }

        String expectedUndoFileName = UndoPaths.constructUndoFile(runDir).getFileName().toString();
        if (metadata.undoFiles() == null
            || metadata.undoFiles().size() != 1
            || !expectedUndoFileName.equals(metadata.undoFiles().getFirst())) {
            send("Invalid undo metadata for single-pass mode. Expected exactly one undo file: " + expectedUndoFileName);
            stage = Stage.DONE;
            return;
        }
        undoFile = runDir.resolve(expectedUndoFileName);
        if (!Files.exists(undoFile)) {
            send("Undo file not found for run: " + expectedUndoFileName);
            stage = Stage.DONE;
            return;
        }
        passIndex = 0;
        protectionUnregistered = false;

        loadUndoBoundsFromRunState();

        send(String.format(Locale.ROOT,
            "Undo run '%s' in %s (%d undo file(s)).",
            metadata.runId(), metadata.dimension(), 1
        ));

        stage = Stage.LOAD_UNDO_ASYNC;
    }

    private void loadUndoAsync() {
        if (metadata == null || world == null) {
            stage = Stage.DONE;
            return;
        }

        if (passIndex < 0) {
            if (pendingDelete == null) {
                unregisterProtectionOnce();
                send("Undo complete. Removing undo history for run '" + metadata.runId() + "'...");
                pendingDelete = CompletableFuture.runAsync(this::deleteUndoRunInternal, ioExecutor);
                stage = Stage.DELETE_RUN_ASYNC;
            }
            return;
        }

        if (loadedUndoData != null) {
            if (!undoPreflightDone) {
                runUndoPreflight(loadedUndoData);
                undoPreflightDone = true;
            }

            if (loadedChunkPlan == null && pendingChunkPlan == null) {
                UndoFileFormat.UndoDataIndexed data = loadedUndoData;
                pendingChunkPlan = CompletableFuture.supplyAsync(() -> buildUndoChunkPlan(data), ioExecutor);
                return;
            }

            if (loadedChunkPlan == null) {
                if (!pendingChunkPlan.isDone()) {
                    return;
                }

                try {
                    loadedChunkPlan = pendingChunkPlan.join();
                } catch (Exception e) {
                    send("Failed to prepare target chunks for undo pass: " + rootMessage(e));
                    pendingChunkPlan = null;
                    stage = Stage.DONE;
                    return;
                }
                pendingChunkPlan = null;
            }

            if (!loadedChunkPlan.missingChunks.isEmpty()) {
                send("Preparing " + loadedChunkPlan.missingChunks.size() + " missing chunk(s) for undo pass");
                chunkPrepStartedAtNanos = System.nanoTime();
                chunkPrepExpectedMissingCount = loadedChunkPlan.missingChunks.size();
                chunkPrepStatusLogTicks = 0;
                chunkGenerationScheduler.reset(loadedChunkPlan.missingChunks);
                chunkPrepObservedLoadedKeys.clear();
                setUnloadChunkKeys(Set.of());
                pendingMissingAfterUnload = null;
                stage = Stage.PREPARE_MISSING_CHUNKS;
                return;
            }

            ejectPlayersFromBounds(loadedUndoData.minX(), loadedUndoData.minY(), loadedUndoData.minZ(), loadedUndoData.maxX(), loadedUndoData.maxY(), loadedUndoData.maxZ());
            setUnloadChunkKeys(loadedChunkPlan.targetChunkKeys);
            pendingMissingAfterUnload = null;
            int loadedTargetChunks = countLoadedTargetChunks(unloadChunkKeys);
            if (loadedTargetChunks > 0) {
                send("Waiting for " + loadedTargetChunks + " target chunk(s) to unload before undo pass");
                stage = Stage.WAIT_FOR_TARGET_CHUNKS_UNLOAD;
                return;
            }

            UndoFileFormat.UndoDataIndexed dataToApply = loadedUndoData;
            send("Applying undo pass");
            pendingApply = CompletableFuture.runAsync(() -> {
                long startedAt = System.nanoTime();
                AtlantisMod.LOGGER.info("[undo:{}] apply start pass thread={}", getRunId(), Thread.currentThread().getName());
                UndoApplyStats stats = applyUndoPass(dataToApply);
                long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
                AtlantisMod.LOGGER.info("[undo:{}] apply done pass elapsed={}ms chunks={} writes={}", getRunId(), elapsedMs, stats.chunkCount, stats.writeCount);
            }, ioExecutor);
            stage = Stage.APPLY_UNDO_ASYNC;
            return;
        }

        if (!Files.exists(undoFile)) {
            send("Undo file missing during run: " + undoFile.getFileName());
            stage = Stage.DONE;
            return;
        }

        if (pendingUndoData == null) {
            Path fileToLoad = undoFile;
            send("Loading undo file " + fileToLoad.getFileName());
            pendingUndoData = CompletableFuture.supplyAsync(() -> {
                try {
                    return UndoFileFormat.readIndexed(fileToLoad);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }, ioExecutor);
            return;
        }

        if (!pendingUndoData.isDone()) {
            return;
        }

        UndoFileFormat.UndoDataIndexed undoData;
        try {
            undoData = pendingUndoData.join();
        } catch (Exception e) {
            send("Failed to read undo file: " + rootMessage(e));
            pendingUndoData = null;
            stage = Stage.DONE;
            return;
        }
        pendingUndoData = null;

        if (!metadata.dimension().equals(undoData.dimension())) {
            send("Undo file dimension mismatch.");
            stage = Stage.DONE;
            return;
        }

        loadedUndoData = undoData;
        undoPreflightDone = false;
        stage = Stage.LOAD_UNDO_ASYNC;
    }

    private void prepareMissingChunks() {
        if (loadedUndoData == null || loadedChunkPlan == null || world == null) {
            stage = Stage.LOAD_UNDO_ASYNC;
            return;
        }

        ejectPlayersFromBounds(loadedUndoData.minX(), loadedUndoData.minY(), loadedUndoData.minZ(), loadedUndoData.maxX(), loadedUndoData.maxY(), loadedUndoData.maxZ());

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
                send("Undo chunk prep running: elapsed=" + elapsedMs + "ms, pending=" + chunkGenerationScheduler.pendingCount() + ", active=" + chunkGenerationScheduler.activeCount());
            }
            return;
        }

        long elapsedMs = (System.nanoTime() - chunkPrepStartedAtNanos) / 1_000_000L;
        send("Undo chunk prep complete: requested=" + chunkPrepExpectedMissingCount + ", elapsed=" + elapsedMs + "ms");

        setUnloadChunkKeys(mergeChunkKeySets(loadedChunkPlan.targetChunkKeys, chunkPrepObservedLoadedKeys));
        chunkPrepObservedLoadedKeys.clear();
        pendingMissingAfterUnload = null;
        send("Undo chunk prep unload set prepared: targetChunks=" + loadedChunkPlan.targetChunkKeys.size() + ", unloadSet=" + unloadChunkKeys.size());

        // Non-blocking path: mark loaded targets dirty and verify NBT after unload.
        markLoadedTargetChunksNeedSaving(loadedChunkPlan.targetChunkKeys);
        send("Undo chunk prep save pass skipped (non-blocking); will verify after unload");

        chunkGenerationScheduler.releaseTickets(world);
        stage = Stage.WAIT_FOR_TARGET_CHUNKS_UNLOAD;
    }

    private void waitForTargetChunksUnload() {
        if (loadedUndoData == null) {
            stage = Stage.LOAD_UNDO_ASYNC;
            return;
        }

        ejectPlayersFromBounds(loadedUndoData.minX(), loadedUndoData.minY(), loadedUndoData.minZ(), loadedUndoData.maxX(), loadedUndoData.maxY(), loadedUndoData.maxZ());

        int loadedTargetChunks = loadedChunkPlan != null
            ? countLoadedTargetChunks(unloadChunkKeys)
            : 0;
        if (loadedTargetChunks > 0) {
            unloadWaitTicks++;
            if (unloadWaitTicks % UNLOAD_FORCE_INTERVAL_TICKS == 0) {
                forceUnloadTargetChunks(unloadChunkKeys);
            }
            if (unloadWaitTicks % UNLOAD_STATUS_LOG_INTERVAL_TICKS == 0) {
                send("Undo unload wait running: loadedChunks=" + loadedTargetChunks + ", unloadSet=" + unloadChunkKeys.size() + ", waitTicks=" + unloadWaitTicks);
            }
            return;
        }
        unloadWaitTicks = 0;

        if (loadedChunkPlan != null) {
            if (pendingMissingAfterUnload == null) {
                Set<Long> keysToVerify = loadedChunkPlan.targetChunkKeys;
                pendingMissingAfterUnload = CompletableFuture.supplyAsync(() -> findMissingChunkNbts(keysToVerify), ioExecutor);
                return;
            }

            if (!pendingMissingAfterUnload.isDone()) {
                return;
            }

            List<ChunkPos> missingAfterUnload;
            try {
                missingAfterUnload = pendingMissingAfterUnload.join();
            } catch (Exception e) {
                send("Undo chunk prep verify failed: " + rootMessage(e));
                stage = Stage.DONE;
                return;
            } finally {
                pendingMissingAfterUnload = null;
            }

            if (!missingAfterUnload.isEmpty()) {
                send("Undo chunk prep failed: " + missingAfterUnload.size() + " chunk(s) still missing NBT after unload");
                stage = Stage.DONE;
                return;
            }
        }

        UndoFileFormat.UndoDataIndexed dataToApply = loadedUndoData;
        send("Applying undo pass");
        pendingApply = CompletableFuture.runAsync(() -> {
            long startedAt = System.nanoTime();
            AtlantisMod.LOGGER.info("[undo:{}] apply start pass thread={}", getRunId(), Thread.currentThread().getName());
            UndoApplyStats stats = applyUndoPass(dataToApply);
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
            AtlantisMod.LOGGER.info("[undo:{}] apply done pass elapsed={}ms chunks={} writes={}", getRunId(), elapsedMs, stats.chunkCount, stats.writeCount);
        }, ioExecutor);
        stage = Stage.APPLY_UNDO_ASYNC;
    }

    private void applyUndoAsync() {
        if (pendingApply == null) {
            stage = Stage.LOAD_UNDO_ASYNC;
            return;
        }

        if (!pendingApply.isDone()) {
            if (loadedUndoData != null) {
                ejectPlayersFromBounds(
                    loadedUndoData.minX(),
                    loadedUndoData.minY(),
                    loadedUndoData.minZ(),
                    loadedUndoData.maxX(),
                    loadedUndoData.maxY(),
                    loadedUndoData.maxZ()
                );
            }
            return;
        }

        try {
            pendingApply.join();
        } catch (Exception e) {
            send("Failed to apply undo pass: " + rootMessage(e));
            AtlantisMod.LOGGER.error("Failed to apply undo pass", e);

            pendingApply = null;
            stage = Stage.WAIT_FOR_TARGET_CHUNKS_UNLOAD;
            return;
        }

        pendingApply = null;
        loadedUndoData = null;
        loadedChunkPlan = null;
        pendingChunkPlan = null;
        undoPreflightDone = false;
        unloadWaitTicks = 0;
        setUnloadChunkKeys(Set.of());
        pendingMissingAfterUnload = null;

        Path finishedUndoFile = undoFile;
        try {
            Files.deleteIfExists(finishedUndoFile);
        } catch (Exception ignored) {
        }

        passIndex = -1;
        stage = Stage.DELETE_RUN_ASYNC;
    }

    private void deleteRunAsync() {
        if (pendingDelete == null) {
            stage = Stage.DONE;
            return;
        }

        if (!pendingDelete.isDone()) {
            return;
        }

        try {
            pendingDelete.join();
        } catch (Exception ignored) {
        }

        pendingDelete = null;
        stage = Stage.DONE;

        releaseSpawnPauseIfNeeded();
    }

    @Override
    public void onError(MinecraftServer server, Throwable throwable) {
        releaseChunkGenerationTickets();
        releaseSpawnPauseIfNeeded();
        AtlantisMod.LOGGER.error("Undo crashed", throwable);
        send("Undo crashed: " + rootMessage(throwable));
    }

    @Override
    public void onCancel(MinecraftServer server, String reason) {
        releaseChunkGenerationTickets();
        releaseSpawnPauseIfNeeded();
        if (reason != null && !reason.isBlank()) {
            send("Undo cancelled: " + reason);
        } else {
            send("Undo cancelled.");
        }
    }

    private UndoApplyStats applyUndoPass(UndoFileFormat.UndoDataIndexed data) {
        Map<Long, List<UndoPlacement>> byChunk = new HashMap<>();

        int[] xs = data.xs();
        int[] ys = data.ys();
        int[] zs = data.zs();
        int[] blockIdx = data.blockIdx();
        int[] nbtIdx = data.nbtIdx();
        String[] blocks = data.blocks();
        String[] nbts = data.nbts();
        int invalidBlockPaletteRefs = 0;
        int nullBlockPaletteValues = 0;
        int invalidNbtPaletteRefs = 0;

        for (int i = 0; i < xs.length; i++) {
            int x = xs[i];
            int y = ys[i];
            int z = zs[i];

            String blockString = "minecraft:air";
            int blockPaletteIndex = blockIdx[i];
            if (blocks != null && blockPaletteIndex >= 0 && blockPaletteIndex < blocks.length) {
                blockString = blocks[blockPaletteIndex];
                if (blockString == null) {
                    nullBlockPaletteValues++;
                    blockString = "minecraft:air";
                }
            } else {
                invalidBlockPaletteRefs++;
            }

            int nbtPaletteIndex = nbtIdx[i];
            String nbtSnbt;
            if (nbtPaletteIndex < 0) {
                nbtSnbt = null;
            } else if (nbts != null && nbtPaletteIndex < nbts.length) {
                nbtSnbt = nbts[nbtPaletteIndex];
            } else {
                invalidNbtPaletteRefs++;
                nbtSnbt = null;
            }

            long key = ChunkPos.toLong(x >> 4, z >> 4);
            byChunk.computeIfAbsent(key, ignored -> new ArrayList<>())
                .add(new UndoPlacement(x, y, z, blockString, nbtSnbt));
        }

        if (invalidBlockPaletteRefs > 0 || nullBlockPaletteValues > 0 || invalidNbtPaletteRefs > 0) {
            AtlantisMod.LOGGER.warn(
                "[undo:{}] normalized invalid undo palette entries: invalidBlockRefs={}, nullBlockValues={}, invalidNbtRefs={}",
                getRunId(),
                invalidBlockPaletteRefs,
                nullBlockPaletteValues,
                invalidNbtPaletteRefs
            );
        }

        final int[] writeCount = new int[1];
        for (Map.Entry<Long, List<UndoPlacement>> entry : byChunk.entrySet()) {
            int chunkX = ChunkPos.getPackedX(entry.getKey());
            int chunkZ = ChunkPos.getPackedZ(entry.getKey());
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

            boolean mutated = UnloadedChunkNbtEditor.mutateChunk(world, chunkPos, context -> {
                for (UndoPlacement placement : entry.getValue()) {
                    context.setBlock(
                        placement.x,
                        placement.y,
                        placement.z,
                        placement.blockString,
                        placement.blockEntitySnbt
                    );
                    writeCount[0]++;
                }
            });

            if (!mutated) {
                throw new IllegalStateException("Target chunk unavailable for unloaded mutate (loaded or missing NBT): " + chunkPos.x + "," + chunkPos.z);
            }
        }

        return new UndoApplyStats(byChunk.size(), writeCount[0]);
    }

    private int countLoadedTargetChunks(Set<Long> targetChunkKeys) {
        if (world == null || targetChunkKeys == null || targetChunkKeys.isEmpty()) {
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
        if (world == null || targetChunkKeys == null || targetChunkKeys.isEmpty()) {
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
            AtlantisMod.LOGGER.info("[undo:{}] requested immediate save for {} loaded target chunk(s).", getRunId(), saved);
        }
    }

    private UndoChunkPlan buildUndoChunkPlan(UndoFileFormat.UndoDataIndexed data) {
        Set<Long> targetChunkKeys = new java.util.HashSet<>();

        int[] xs = data.xs();
        int[] ys = data.ys();
        int[] zs = data.zs();

        for (int i = 0; i < xs.length; i++) {
            int x = xs[i];
            int y = ys[i];
            int z = zs[i];

            targetChunkKeys.add(ChunkPos.toLong(x >> 4, z >> 4));
        }

        List<ChunkPos> missingChunks = new ArrayList<>();
        missingChunks.addAll(findMissingChunkNbts(targetChunkKeys));

        return new UndoChunkPlan(Set.copyOf(targetChunkKeys), List.copyOf(missingChunks));
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
        if (world == null) {
            return;
        }
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
        if (world == null || targetChunkKeys == null || targetChunkKeys.isEmpty()) {
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
        if (world == null || chunkKeys == null || chunkKeys.isEmpty()) {
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

        AtlantisMod.LOGGER.info("[undo] discovered {} chunk ticket type(s) for unload forcing.", types.size());
        return List.copyOf(types);
    }

    private void loadUndoBoundsFromRunState() {
        if (runDir == null) {
            return;
        }

        Path stateFile = ConstructStatePaths.stateFile(runDir);
        if (!Files.exists(stateFile)) {
            return;
        }

        try {
            ConstructRunState state = ConstructRunStateIO.read(stateFile);
            if (state.overallMinX() == null || state.overallMinY() == null || state.overallMinZ() == null
                || state.overallMaxX() == null || state.overallMaxY() == null || state.overallMaxZ() == null) {
                return;
            }

            undoMinX = state.overallMinX();
            undoMinY = state.overallMinY();
            undoMinZ = state.overallMinZ();
            undoMaxX = state.overallMaxX();
            undoMaxY = state.overallMaxY();
            undoMaxZ = state.overallMaxZ();
        } catch (Exception ignored) {
        }
    }

    private void ejectPlayersFromBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (world == null) {
            return;
        }

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
            AtlantisMod.LOGGER.info("[undo:{}] ejected {} player(s) from keepout (margin={} offset={} view={} sim={})",
                getRunId(),
                ejectedCount,
                margin,
                offset,
                viewDistanceChunks,
                simulationDistanceChunks
            );
        }
    }

    private void unregisterProtectionOnce() {
        if (protectionUnregistered || metadata == null) {
            return;
        }
        ProtectionManager.INSTANCE.unregister(metadata.runId());
        ProtectionManager.INSTANCE.flushPendingIndexJobs();
        protectionUnregistered = true;
    }

    private void deleteUndoRunInternal() {
        unregisterProtectionOnce();
        if (runDir == null || !Files.exists(runDir)) {
            send("Undo complete.");
            return;
        }

        try {
            try (var walk = Files.walk(runDir)) {
                walk.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception ignored) {
                    }
                });
            }
            UndoPaths.pruneUnusableRunDirectories();
            send("Undo complete.");
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to delete undo run directory {}: {}", runDir, e.getMessage());
            send("Undo complete, but could not delete undo history folder.");
        }
    }

    private void runUndoPreflight(UndoFileFormat.UndoDataIndexed undoData) {
        if (world == null || undoData == null) {
            return;
        }

        int minX = (undoMinX != null) ? undoMinX : undoData.minX();
        int minY = (undoMinY != null) ? undoMinY : undoData.minY();
        int minZ = (undoMinZ != null) ? undoMinZ : undoData.minZ();
        int maxX = (undoMaxX != null) ? undoMaxX : undoData.maxX();
        int maxY = (undoMaxY != null) ? undoMaxY : undoData.maxY();
        int maxZ = (undoMaxZ != null) ? undoMaxZ : undoData.maxZ();

        acquireSpawnPauseIfNeeded();

        ActiveConstructBounds bounds = new ActiveConstructBounds(
            getRunId() == null ? "undo" : getRunId(),
            world.getRegistryKey().getValue().toString(),
            minX,
            minY,
            minZ,
            maxX,
            maxY,
            maxZ
        );
        ProximityMobManager.getInstance().clearWithinBounds(world, bounds);
        send("Undo preflight: custom spawning paused and Atlantis mobs cleared in bounds.");

        ejectPlayersFromBounds(minX, minY, minZ, maxX, maxY, maxZ);
        forceReleaseChunksInBounds(minX, minZ, maxX, maxZ);
    }

    private void forceReleaseChunksInBounds(int minX, int minZ, int maxX, int maxZ) {
        if (world == null) {
            return;
        }

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

        send("Undo preflight: force-release requested for " + total + " chunk(s), loadedBefore=" + loadedBefore);
    }

    private void acquireSpawnPauseIfNeeded() {
        if (spawnPauseAcquired) {
            return;
        }
        ProximityMobManager.getInstance().acquireExternalPause(spawnPauseToken());
        spawnPauseAcquired = true;
    }

    private void releaseSpawnPauseIfNeeded() {
        if (!spawnPauseAcquired) {
            return;
        }
        ProximityMobManager.getInstance().releaseExternalPause(spawnPauseToken());
        spawnPauseAcquired = false;
    }

    private String spawnPauseToken() {
        String runId = getRunId();
        if (runId == null || runId.isBlank()) {
            runId = (requestedRunId == null || requestedRunId.isBlank()) ? "latest" : requestedRunId;
        }
        return "undo:" + runId;
    }

    private void send(String message) {
        AtlantisMod.LOGGER.info("[undo:{}] {}", getRunId(), message);

        if (requesterId != null && server != null) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(requesterId);
            if (player != null) {
                player.sendMessage(Text.literal(message), false);
            }
        }
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
        Set<Long> merged = new java.util.HashSet<>(first);
        merged.addAll(second);
        return Set.copyOf(merged);
    }

    private static final class UndoPlacement {
        private final int x;
        private final int y;
        private final int z;
        private final String blockString;
        private final String blockEntitySnbt;

        private UndoPlacement(int x, int y, int z, String blockString, String blockEntitySnbt) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.blockString = blockString;
            this.blockEntitySnbt = blockEntitySnbt;
        }
    }

    private static final class UndoChunkPlan {
        private final Set<Long> targetChunkKeys;
        private final List<ChunkPos> missingChunks;

        private UndoChunkPlan(Set<Long> targetChunkKeys, List<ChunkPos> missingChunks) {
            this.targetChunkKeys = targetChunkKeys;
            this.missingChunks = missingChunks;
        }
    }

    private static final class UndoApplyStats {
        private final int chunkCount;
        private final int writeCount;

        private UndoApplyStats(int chunkCount, int writeCount) {
            this.chunkCount = chunkCount;
            this.writeCount = writeCount;
        }
    }
}
