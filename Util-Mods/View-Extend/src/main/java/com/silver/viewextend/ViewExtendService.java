package com.silver.viewextend;

import com.silver.viewextend.mixin.ChunkDataS2CPacketAccessor;
import com.silver.viewextend.mixin.LightUpdateS2CPacketAccessor;
import com.silver.viewextend.mixin.ServerChunkLoadingManagerAccessor;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.ChunkLoadDistanceS2CPacket;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.PalettesFactory;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.SerializedChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

public final class ViewExtendService {
    private static final BitSet EMPTY_BIT_SET = new BitSet();
    private static final Map<Integer, byte[]> WATER_SKYLIGHT_FALLBACK_CACHE = new ConcurrentHashMap<>();
    private static final Map<Integer, int[]> RING_OFFSET_CACHE = new ConcurrentHashMap<>();
    private static final long GLOBAL_TEMPLATE_CACHE_MAX_BYTES = 96L * 1024L * 1024L;
    private static final int PRIORITY_WEIGHT = 5;
    private static final int NORMAL_WEIGHT = 1;
    private static final int LEGACY_BLOCK_REMAP_MAX_DATA_VERSION = 1518;

    private static final Map<String, String> LEGACY_BLOCK_ID_REMAP = Map.ofEntries(
            Map.entry("minecraft:grass", "minecraft:grass_block"),
            Map.entry("minecraft:grass_path", "minecraft:dirt_path"),
            Map.entry("minecraft:chain", "minecraft:iron_chain"),
            Map.entry("minecraft:lit_pumpkin", "minecraft:jack_o_lantern"),
            Map.entry("minecraft:portal", "minecraft:nether_portal"),
            Map.entry("minecraft:lit_furnace", "minecraft:furnace"),
            Map.entry("minecraft:stone_slab", "minecraft:smooth_stone_slab"),
            Map.entry("minecraft:stone_slab2", "minecraft:red_sandstone_slab"),
            Map.entry("minecraft:wooden_slab", "minecraft:oak_slab"),
            Map.entry("minecraft:wooden_door", "minecraft:oak_door"),
            Map.entry("minecraft:wooden_pressure_plate", "minecraft:oak_pressure_plate"));

    private final ViewExtendConfig config;
    private final byte[] fallbackSkyLightNibble;
    private final byte[] fallbackBlockLightNibble;
    private final byte[] oceanFallbackSkyLightNibble;
    private final Long2ObjectOpenHashMap<PlayerState> statesByPlayer = new Long2ObjectOpenHashMap<>();
    private final ConcurrentLinkedQueue<PreparedChunkTask> preparedChunkQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<NbtReadRequest> nbtReadQueue = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<GlobalChunkKey, SoftReference<PacketTemplate>> globalPacketTemplateCache = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<GlobalChunkKey> globalPacketTemplateOrder = new ConcurrentLinkedQueue<>();
    private final ConcurrentHashMap<GlobalChunkKey, Integer> globalPacketTemplateEstimatedBytes = new ConcurrentHashMap<>();
    private final AtomicLong globalPacketTemplateTotalEstimatedBytes = new AtomicLong();
    private final ExecutorService preprocessExecutor;
    private int ticks;
    private long metricsWindowStartNanos = System.nanoTime();
    private long totalCpuNanos;
    private long totalChunkPacketsSent;
    private long totalUnloadPacketsSent;
    private long totalDiskNbtReads;
    private long totalDiskNbtReadMisses;
    private long totalNetworkBytesEstimate;
    private long totalChunkLoadDistancePackets;
    private long lifetimeChunkPacketsSent;
    private long lifetimeUnloadPacketsSent;
    private long lifetimeDiskNbtReads;
    private long lifetimeDiskNbtReadMisses;
    private long lifetimeNetworkBytesEstimate;
    private long lifetimeChunkLoadDistancePackets;
    private long totalLegacyBlockIdRemaps;
    private long lifetimeLegacyBlockIdRemaps;
    private long totalSuppressedVanillaUnloads;
    private long lifetimeSuppressedVanillaUnloads;
    private long totalForcedUpgradeUnloads;
    private long lifetimeForcedUpgradeUnloads;
    private long totalUnsortedSectionInputs;
    private long lifetimeUnsortedSectionInputs;
    private long totalPreparedTasksQueued;
    private long totalPreparedTasksProcessed;
    private long totalPreparedTasksDropped;
    private long totalPayloadCacheHits;
    private long totalPayloadCacheMisses;
    private long lifetimePayloadCacheHits;
    private long lifetimePayloadCacheMisses;
    private long totalDeterministicBlockLightSections;
    private long totalDeterministicSkyLightSections;
    private long lifetimeDeterministicBlockLightSections;
    private long lifetimeDeterministicSkyLightSections;
    private long totalSkyLightNbtSections;
    private long lifetimeSkyLightNbtSections;
    private long totalSkyLightFallbackSections;
    private long lifetimeSkyLightFallbackSections;
    private long totalSkyLightWaterFallbackSections;
    private long lifetimeSkyLightWaterFallbackSections;
    private long totalBlockLightNbtSections;
    private long lifetimeBlockLightNbtSections;
    private long totalBlockLightFallbackSections;
    private long lifetimeBlockLightFallbackSections;
    private long totalQueueBackpressureDeferrals;
    private long lifetimeQueueBackpressureDeferrals;
    private long totalNbtRequestsQueued;
    private long totalNbtRequestsProcessed;
    private long lifetimeNbtRequestsQueued;
    private long lifetimeNbtRequestsProcessed;
    private long totalGlobalTemplateHits;
    private long totalGlobalTemplateMisses;
    private long lifetimeGlobalTemplateHits;
    private long lifetimeGlobalTemplateMisses;
    private long cpuEmaNanos;
    private int effectiveChunkQueueBudget;
    private int effectivePreparedProcessBudget;
    private long totalLod0Sends;
    private long totalLod1Sends;
    private long lifetimeLod0Sends;
    private long lifetimeLod1Sends;

    public ViewExtendService(ViewExtendConfig config) {
        this.config = config;
        this.fallbackSkyLightNibble = createLightLevelNibble(config.fallbackSkyLightLevel());
        this.fallbackBlockLightNibble = createLightLevelNibble(config.fallbackBlockLightLevel());
        this.oceanFallbackSkyLightNibble = createLightLevelNibble(config.oceanFallbackSkyLightLevel());
        this.effectiveChunkQueueBudget = config.maxChunksPerPlayerPerTick();
        this.effectivePreparedProcessBudget = config.maxMainThreadPreparedChunksPerTick();
        int workerThreads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("viewextend-preprocess");
            thread.setDaemon(true);
            return thread;
        };
        this.preprocessExecutor = Executors.newFixedThreadPool(workerThreads, threadFactory);
    }

    public void tick(MinecraftServer server) {
        long tickStartNanos = System.nanoTime();
        ticks++;
        recomputeEffectiveBudgets();
        if (config.unsimulatedViewDistance() <= 0 || ticks % config.tickInterval() != 0) {
            processPreparedChunkTasks(server, effectivePreparedProcessBudget);
            processNbtReadRequests(server, config.maxNbtReadsPerTick());
            long elapsed = System.nanoTime() - tickStartNanos;
            totalCpuNanos += elapsed;
            updateCpuEma(elapsed);
            maybeLogMetrics(server);
            return;
        }

        processPreparedChunkTasks(server, effectivePreparedProcessBudget);
        processNbtReadRequests(server, config.maxNbtReadsPerTick());

        List<PlayerTickTarget> targets = collectTickTargets(server);
        int[] budgets = allocatePerPlayerBudgets(targets, effectiveChunkQueueBudget);

        for (int index = 0; index < targets.size(); index++) {
            PlayerTickTarget target = targets.get(index);
            int budget = budgets[index];
            if (budget <= 0) {
                continue;
            }
            tickPlayer(server, target.world(), target.player(), budget);
        }

        processNbtReadRequests(server, config.maxNbtReadsPerTick());

        cleanupDisconnected(server);
        long elapsed = System.nanoTime() - tickStartNanos;
        totalCpuNanos += elapsed;
        updateCpuEma(elapsed);
        maybeLogMetrics(server);
    }

    private void tickPlayer(MinecraftServer server, ServerWorld world, ServerPlayerEntity player, int maxPerTick) {
        String worldKey = world.getRegistryKey().getValue().toString();
        long stateKey = stateKey(player.getUuid(), worldKey);
        PlayerState state = statesByPlayer.computeIfAbsent(stateKey, ignored -> new PlayerState(player.getUuid(), worldKey));

        int centerX = player.getChunkPos().x;
        int centerZ = player.getChunkPos().z;
        int normalDistance = world.getServer().getPlayerManager().getViewDistance();
        int totalDistance = getEffectiveTotalDistance(player, normalDistance);

        updateClientLoadDistance(player, state, totalDistance);

        boolean movedChunk = state.centerX != centerX || state.centerZ != centerZ;
        if (movedChunk) {
            state.centerX = centerX;
            state.centerZ = centerZ;
            prunePendingInsideNormal(state, centerX, centerZ, normalDistance);
        }
        unloadOutOfRange(player, state, centerX, centerZ, totalDistance, normalDistance);

        if (maxPerTick <= 0) {
            return;
        }

        int remainingBudget = maxPerTick;
        int prioritizedQueued = queueUpgradeCandidates(
                world,
                player,
                state,
                worldKey,
                centerX,
                centerZ,
                normalDistance,
                totalDistance,
                remainingBudget);
        remainingBudget -= prioritizedQueued;
        if (remainingBudget <= 0) {
            return;
        }

        if (isPreparedQueueAtHardLimit()) {
            totalQueueBackpressureDeferrals++;
            lifetimeQueueBackpressureDeferrals++;
            return;
        }

        if (state.pending.size() >= config.pendingChunksHardLimit()) {
            totalQueueBackpressureDeferrals++;
            lifetimeQueueBackpressureDeferrals++;
            return;
        }

        if (shouldDeferChunkSelection(state, movedChunk)) {
            totalQueueBackpressureDeferrals++;
            lifetimeQueueBackpressureDeferrals++;
            return;
        }

        int sentThisTick = 0;
        for (int radius = normalDistance + 1; radius <= totalDistance && sentThisTick < remainingBudget; radius++) {
            int[] offsets = ringOffsets(radius);
            for (int offsetIndex = 0; offsetIndex < offsets.length && sentThisTick < remainingBudget; offsetIndex += 2) {
                int chunkX = centerX + offsets[offsetIndex];
                int chunkZ = centerZ + offsets[offsetIndex + 1];
                sentThisTick += tryQueueCandidate(world, player, state, worldKey, centerX, centerZ, chunkX, chunkZ, false);
            }
        }
    }

    private int queueUpgradeCandidates(
            ServerWorld world,
            ServerPlayerEntity player,
            PlayerState state,
            String worldKey,
            int centerX,
            int centerZ,
            int normalDistance,
            int totalDistance,
            int maxPerTick) {
        int queued = 0;
        long[] sentSnapshot = state.sent.toLongArray();
        for (long chunkLong : sentSnapshot) {
            if (queued >= maxPerTick) {
                break;
            }

            int chunkX = ChunkPos.getPackedX(chunkLong);
            int chunkZ = ChunkPos.getPackedZ(chunkLong);
            if (!withinDistance(chunkX, chunkZ, centerX, centerZ, totalDistance)
                    || withinDistance(chunkX, chunkZ, centerX, centerZ, normalDistance)) {
                continue;
            }

            int sentLodLevel = state.sentLodByChunk.getOrDefault(chunkLong, Integer.MAX_VALUE);
            int desiredLodLevel = resolveLodLevel(centerX, centerZ, chunkX, chunkZ);
            if (desiredLodLevel >= sentLodLevel) {
                continue;
            }

            queued += tryQueueCandidate(world, player, state, worldKey, centerX, centerZ, chunkX, chunkZ, true);
        }
        return queued;
    }

    private int tryQueueCandidate(
            ServerWorld world,
            ServerPlayerEntity player,
            PlayerState state,
            String worldKey,
            int centerX,
            int centerZ,
            int chunkX,
            int chunkZ,
            boolean upgradePriority) {
        long chunkLong = ChunkPos.toLong(chunkX, chunkZ);
        
        int missingUntil = state.missingUntilTick.getOrDefault(chunkLong, 0);
        if (this.ticks < missingUntil) {
            // It's on cooldown for disk reads. But if it just finished generating and is now a full WorldChunk,
            // we bypass the cooldown so it gets sent immediately!
            if (!world.getChunkManager().isChunkLoaded(chunkX, chunkZ) || world.getChunkManager().getWorldChunk(chunkX, chunkZ) == null) {
                return 0; // Skip, it's on cooldown and not in memory
            }
        }

        int desiredLodLevel = resolveLodLevel(centerX, centerZ, chunkX, chunkZ);

        int sentLodLevel = state.sentLodByChunk.getOrDefault(chunkLong, Integer.MAX_VALUE);
        if (state.sent.contains(chunkLong) && desiredLodLevel >= sentLodLevel) {
            return 0;
        }
        if (state.sent.contains(chunkLong) && desiredLodLevel < sentLodLevel) {
            state.sent.remove(chunkLong);
            state.sentLodByChunk.remove(chunkLong);
            state.payloadCache.remove(chunkLong);
            invalidateGlobalPacketTemplatesForChunk(worldKey, chunkLong, desiredLodLevel);
        }

        int pendingLodLevel = state.pendingLodByChunk.getOrDefault(chunkLong, Integer.MAX_VALUE);
        if (state.pending.contains(chunkLong) && desiredLodLevel >= pendingLodLevel) {
            return 0;
        }
        if (state.pending.contains(chunkLong) && desiredLodLevel < pendingLodLevel) {
            state.pending.remove(chunkLong);
            state.pendingLodByChunk.remove(chunkLong);
            state.payloadCache.remove(chunkLong);
            invalidateGlobalPacketTemplatesForChunk(worldKey, chunkLong, desiredLodLevel);
        }

        GlobalChunkKey globalChunkKey = new GlobalChunkKey(worldKey, chunkLong, desiredLodLevel);
        PacketTemplate globalTemplate = getGlobalPacketTemplate(globalChunkKey);
        if (globalTemplate != null) {
            totalGlobalTemplateHits++;
            lifetimeGlobalTemplateHits++;
            sendPacketTemplate(player, globalTemplate, desiredLodLevel);
            state.sent.add(chunkLong);
            state.sentLodByChunk.put(chunkLong, desiredLodLevel);
            state.pending.remove(chunkLong);
            state.pendingLodByChunk.remove(chunkLong);
            state.unloadOutsideSinceTick.remove(chunkLong);
            return 1;
        }
        totalGlobalTemplateMisses++;
        lifetimeGlobalTemplateMisses++;

        prunePayloadCache(state);
        CachedPayload cachedPayload = getCachedPayload(state, chunkLong);
        if (cachedPayload != null) {
            if (cachedPayload.lodLevel() == desiredLodLevel) {
                PacketTemplate packetTemplate = cachedPayload.packetTemplate();
                if (packetTemplate != null) {
                    sendPacketTemplate(player, packetTemplate, desiredLodLevel);
                    totalPayloadCacheHits++;
                    lifetimePayloadCacheHits++;
                    state.sent.add(chunkLong);
                    state.sentLodByChunk.put(chunkLong, desiredLodLevel);
                    state.pending.remove(chunkLong);
                    state.pendingLodByChunk.remove(chunkLong);
                    state.unloadOutsideSinceTick.remove(chunkLong);
                    return 1;
                }
            }

            state.payloadCache.remove(chunkLong);
            invalidateGlobalPacketTemplatesForChunk(worldKey, chunkLong, desiredLodLevel);
        }

        totalPayloadCacheMisses++;
        lifetimePayloadCacheMisses++;

        state.pending.add(chunkLong);
        state.pendingLodByChunk.put(chunkLong, desiredLodLevel);
        enqueueNbtReadRequest(player.getUuid(), worldKey, new ChunkPos(chunkX, chunkZ), desiredLodLevel, upgradePriority);
        return 1;
    }

    private void enqueueNbtReadRequest(UUID playerUuid, String worldKey, ChunkPos pos, int lodLevel, boolean upgradePriority) {
        nbtReadQueue.add(new NbtReadRequest(playerUuid, worldKey, pos, lodLevel, upgradePriority));
        totalNbtRequestsQueued++;
        lifetimeNbtRequestsQueued++;
    }

        private void requestAndSendChunk(
            ServerWorld world,
            ServerPlayerEntity player,
            PlayerState state,
            String worldKey,
            ChunkPos pos,
            int lodLevel,
            boolean upgradePriority) {
        if (!upgradePriority && isPreparedQueueAtHardLimit()) {
            totalQueueBackpressureDeferrals++;
            lifetimeQueueBackpressureDeferrals++;
            state.pending.remove(pos.toLong());
            state.pendingLodByChunk.remove(pos.toLong());
            return;
        }

        if (world.getChunkManager().isChunkLoaded(pos.x, pos.z)) {
            WorldChunk loaded = world.getChunkManager().getWorldChunk(pos.x, pos.z);
            if (loaded != null) {
                sendChunkPacket(player, loaded, null, null, null);
                long packed = pos.toLong();
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.sent.add(packed);
                state.sentLodByChunk.put(packed, 0);
                state.unloadOutsideSinceTick.remove(packed);
                return;
            }
        }

        ServerChunkLoadingManager loadingManager = world.getChunkManager().chunkLoadingManager;
        totalDiskNbtReads++;
        lifetimeDiskNbtReads++;
        loadingManager.getNbt(pos).thenAccept(optionalNbt -> {
            if (optionalNbt.isEmpty()) {
                preparedChunkQueue.add(PreparedChunkTask.missing(player.getUuid(), worldKey, pos, lodLevel));
                totalPreparedTasksQueued++;
                return;
            }

            preprocessExecutor.execute(() -> {
                try {
                    NbtCompound chunkNbt = optionalNbt.get().copy();
                    applyLodToChunkNbt(chunkNbt, lodLevel);
                    int remapCount = remapLegacyBlockIds(chunkNbt);
                    preparedChunkQueue.add(PreparedChunkTask.withNbt(player.getUuid(), worldKey, pos, chunkNbt, remapCount, lodLevel));
                    totalPreparedTasksQueued++;
                } catch (Exception exception) {
                    preparedChunkQueue.add(PreparedChunkTask.failed(player.getUuid(), worldKey, pos, lodLevel));
                    totalPreparedTasksQueued++;
                }
            });
        });
    }

    private void processNbtReadRequests(MinecraftServer server, int budget) {
        int processed = 0;
        int perTickBudget = Math.max(1, budget);
        while (processed < perTickBudget) {
            NbtReadRequest request = nbtReadQueue.poll();
            if (request == null) {
                break;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(request.playerUuid());
            if (player == null || !player.isAlive()) {
                continue;
            }

            ServerWorld world = player.getEntityWorld();
            String worldKey = world.getRegistryKey().getValue().toString();
            if (!worldKey.equals(request.worldKey())) {
                continue;
            }

            long stateKey = stateKey(request.playerUuid(), request.worldKey());
            PlayerState state = statesByPlayer.get(stateKey);
            if (state == null) {
                continue;
            }

            long packed = request.pos().toLong();
            if (!state.pending.contains(packed)) {
                continue;
            }

            int desiredPendingLod = state.pendingLodByChunk.getOrDefault(packed, Integer.MAX_VALUE);
            if (desiredPendingLod != request.lodLevel()) {
                continue;
            }

            int normalDistance = world.getServer().getPlayerManager().getViewDistance();
            int totalDistance = getEffectiveTotalDistance(player, normalDistance);
            int centerX = player.getChunkPos().x;
            int centerZ = player.getChunkPos().z;

            if (!withinDistance(request.pos().x, request.pos().z, centerX, centerZ, totalDistance)
                    || withinDistance(request.pos().x, request.pos().z, centerX, centerZ, normalDistance)) {
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.unloadOutsideSinceTick.remove(packed);
                continue;
            }

            requestAndSendChunk(world, player, state, request.worldKey(), request.pos(), request.lodLevel(), request.upgradePriority());
            processed++;
            totalNbtRequestsProcessed++;
            lifetimeNbtRequestsProcessed++;
        }
    }

    private void processPreparedChunkTasks(MinecraftServer server, int budget) {
        for (int i = 0; i < budget; i++) {
            PreparedChunkTask task = preparedChunkQueue.poll();
            if (task == null) {
                return;
            }
            totalPreparedTasksProcessed++;

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(task.playerUuid());
            if (player == null || !player.isAlive()) {
                continue;
            }

            long originalStateKey = stateKey(task.playerUuid(), task.worldKey());
            PlayerState originalState = statesByPlayer.get(originalStateKey);
            if (originalState == null) {
                continue;
            }

            ServerWorld world = player.getEntityWorld();
            String worldKey = world.getRegistryKey().getValue().toString();
            if (!worldKey.equals(task.worldKey())) {
                originalState.pending.remove(task.pos().toLong());
                originalState.pendingLodByChunk.remove(task.pos().toLong());
                originalState.unloadOutsideSinceTick.remove(task.pos().toLong());
                continue;
            }

            PlayerState state = originalState;

            ChunkPos pos = task.pos();
            long packed = pos.toLong();

            int desiredPendingLod = state.pendingLodByChunk.getOrDefault(packed, Integer.MAX_VALUE);
            if (desiredPendingLod != task.lodLevel()) {
                continue;
            }

            int normalDistance = world.getServer().getPlayerManager().getViewDistance();
            int totalDistance = getEffectiveTotalDistance(player, normalDistance);
            int centerX = player.getChunkPos().x;
            int centerZ = player.getChunkPos().z;

            if (!withinDistance(pos.x, pos.z, centerX, centerZ, totalDistance)
                    || withinDistance(pos.x, pos.z, centerX, centerZ, normalDistance)) {
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.unloadOutsideSinceTick.remove(packed);
                continue;
            }

            if (task.missing() || task.failed()) {
                totalDiskNbtReadMisses++;
                lifetimeDiskNbtReadMisses++;
                if (task.failed()) {
                    totalPreparedTasksDropped++;
                }
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.unloadOutsideSinceTick.remove(packed);
                
                int cooldownTicks = task.missing() ? 100 : 200; // 5 seconds for missing, 10 for failed
                state.missingUntilTick.put(packed, this.ticks + cooldownTicks);
                
                continue;
            }

            if (task.remapCount() > 0) {
                totalLegacyBlockIdRemaps += task.remapCount();
                lifetimeLegacyBlockIdRemaps += task.remapCount();
            }

            if (!task.parsedReady()) {
                try {
                    ServerChunkLoadingManager loadingManager = world.getChunkManager().chunkLoadingManager;
                    NbtCompound chunkNbt = ((ServerChunkLoadingManagerAccessor) loadingManager).viewextend$invokeUpdateChunkNbt(
                            world.getRegistryKey(),
                            () -> world.getChunkManager().getPersistentStateManager(),
                            task.chunkNbt(),
                            world.getChunkManager().getChunkGenerator().getCodecKey());

                    int bottomSectionY = world.getChunkManager().getLightingProvider().getBottomY();
                    int topSectionY = world.getChunkManager().getLightingProvider().getTopY();
                    boolean hasSkyLight = world.getDimension().hasSkyLight();

                    preprocessExecutor.execute(() -> {
                        try {
                            PalettesFactory palettesFactory = PalettesFactory.fromRegistryManager(world.getRegistryManager());
                            SerializedChunk serialized = SerializedChunk.fromNbt(world, palettesFactory, chunkNbt);
                            boolean isOceanBiome = isChunkInOceanBiome(chunkNbt);
                            LightData deterministicLightData = createDeterministicLightData(
                                    pos,
                                    bottomSectionY,
                                    topSectionY,
                                    serialized,
                                    hasSkyLight,
                                    false,
                                    isOceanBiome);

                            preparedChunkQueue.add(PreparedChunkTask.parsed(
                                    task.playerUuid(),
                                    task.worldKey(),
                                    pos,
                                    task.lodLevel(),
                                    serialized,
                                    deterministicLightData,
                                    task.remapCount()));
                            totalPreparedTasksQueued++;
                        } catch (Exception exception) {
                            preparedChunkQueue.add(PreparedChunkTask.failed(task.playerUuid(), task.worldKey(), pos, task.lodLevel()));
                            totalPreparedTasksQueued++;
                        }
                    });
                } catch (Exception exception) {
                    totalPreparedTasksDropped++;
                    state.pending.remove(packed);
                    state.pendingLodByChunk.remove(packed);
                    state.unloadOutsideSinceTick.remove(packed);
                }
                continue;
            }

            try {
                ServerChunkLoadingManager loadingManager = world.getChunkManager().chunkLoadingManager;
                SerializedChunk serialized = task.serializedChunk();
                ProtoChunk proto = serialized.convert(
                    world,
                    world.getPointOfInterestStorage(),
                    ((ServerChunkLoadingManagerAccessor) loadingManager).viewextend$invokeGetStorageKey(),
                    pos);
                WorldChunk transientChunk = new WorldChunk(world, proto, chunk -> {
                });
                transientChunk.setLightOn(true);
                transientChunk.getBlockEntities().clear();

                LightingProvider lightingProvider = world.getChunkManager().getLightingProvider();
                PacketTemplate packetTemplate = createSerializedPacketTemplate(
                        transientChunk,
                        lightingProvider,
                        serialized,
                    task.deterministicLightData(),
                    task.lodLevel());
                putGlobalPacketTemplate(new GlobalChunkKey(task.worldKey(), packed, task.lodLevel()), packetTemplate);
                sendPacketTemplate(player, packetTemplate, task.lodLevel());
                state.sent.add(packed);
                state.sentLodByChunk.put(packed, task.lodLevel());
                state.unloadOutsideSinceTick.remove(packed);
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                putCachedPayload(state, packed, packetTemplate, task.lodLevel());
            } catch (Exception exception) {
                totalPreparedTasksDropped++;
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.unloadOutsideSinceTick.remove(packed);
                ViewExtendMod.LOGGER.debug("Failed to process prepared unsimulated chunk {} for {}", pos, player.getName().getString(), exception);
            }
        }
    }

    private void sendChunkPacket(
            ServerPlayerEntity player,
            WorldChunk chunk,
            LightingProvider lightingProvider,
            SerializedChunk serializedChunk,
            LightData prebuiltLightData) {
        LightingProvider resolvedLightingProvider = lightingProvider != null
                ? lightingProvider
                : chunk.getWorld().getChunkManager().getLightingProvider();
        ChunkPos pos = chunk.getPos();
        ChunkDataS2CPacket packet;
        BitSet includeNone = new BitSet();
        if (serializedChunk != null) {
            packet = new ChunkDataS2CPacket(chunk, resolvedLightingProvider, includeNone, includeNone);
        } else {
            packet = new ChunkDataS2CPacket(chunk, resolvedLightingProvider, null, null);
        }
        LightUpdateS2CPacket lightPacket = serializedChunk != null
                ? new LightUpdateS2CPacket(pos, resolvedLightingProvider, includeNone, includeNone)
                : new LightUpdateS2CPacket(pos, resolvedLightingProvider, null, null);

        if (serializedChunk != null) {
            LightData deterministicLightData = prebuiltLightData != null
                    ? prebuiltLightData
                    : createDeterministicLightData(
                            pos,
                            resolvedLightingProvider.getBottomY(),
                            resolvedLightingProvider.getTopY(),
                            serializedChunk,
                            chunk.getWorld().getDimension().hasSkyLight(),
                        false,
                        isChunkInOcean(chunk));
            totalDeterministicBlockLightSections += deterministicLightData.getInitedBlock().cardinality();
            totalDeterministicSkyLightSections += deterministicLightData.getInitedSky().cardinality();
            lifetimeDeterministicBlockLightSections += deterministicLightData.getInitedBlock().cardinality();
            lifetimeDeterministicSkyLightSections += deterministicLightData.getInitedSky().cardinality();
            ((LightUpdateS2CPacketAccessor) (Object) lightPacket).viewextend$setData(deterministicLightData);
            ((ChunkDataS2CPacketAccessor) (Object) packet).viewextend$setLightData(deterministicLightData);
        }

        totalChunkPacketsSent++;
        lifetimeChunkPacketsSent++;
        int packetBytes = packet.getChunkData().getSectionsDataBuf().readableBytes();
        totalNetworkBytesEstimate += packetBytes;
        lifetimeNetworkBytesEstimate += packetBytes;
        recordLodSend(0);
        player.networkHandler.sendPacket(packet);
        player.networkHandler.sendPacket(lightPacket);
    }

    private PacketTemplate createSerializedPacketTemplate(
            WorldChunk chunk,
            LightingProvider lightingProvider,
            SerializedChunk serializedChunk,
            LightData prebuiltLightData,
            int lodLevel) {
        LightingProvider resolvedLightingProvider = lightingProvider != null
                ? lightingProvider
                : chunk.getWorld().getChunkManager().getLightingProvider();
        ChunkPos pos = chunk.getPos();
        BitSet includeNone = new BitSet();
        ChunkDataS2CPacket chunkPacket = new ChunkDataS2CPacket(chunk, resolvedLightingProvider, includeNone, includeNone);
        LightUpdateS2CPacket lightPacket = new LightUpdateS2CPacket(pos, resolvedLightingProvider, includeNone, includeNone);

        LightData deterministicLightData = prebuiltLightData != null
                ? prebuiltLightData
                : createDeterministicLightData(
                        pos,
                        resolvedLightingProvider.getBottomY(),
                        resolvedLightingProvider.getTopY(),
                        serializedChunk,
                    chunk.getWorld().getDimension().hasSkyLight(),
            false,
            isChunkInOcean(chunk));
        ((LightUpdateS2CPacketAccessor) (Object) lightPacket).viewextend$setData(deterministicLightData);
        ((ChunkDataS2CPacketAccessor) (Object) chunkPacket).viewextend$setLightData(deterministicLightData);

        int chunkPacketBytes = chunkPacket.getChunkData().getSectionsDataBuf().readableBytes();
        int blockSections = deterministicLightData.getInitedBlock().cardinality();
        int skySections = deterministicLightData.getInitedSky().cardinality();
        return new PacketTemplate(chunkPacket, lightPacket, chunkPacketBytes, blockSections, skySections);
    }

    private void sendPacketTemplate(ServerPlayerEntity player, PacketTemplate packetTemplate, int lodLevel) {
        totalChunkPacketsSent++;
        lifetimeChunkPacketsSent++;
        totalNetworkBytesEstimate += packetTemplate.chunkPacketBytes();
        lifetimeNetworkBytesEstimate += packetTemplate.chunkPacketBytes();
        totalDeterministicBlockLightSections += packetTemplate.blockLightSections();
        totalDeterministicSkyLightSections += packetTemplate.skyLightSections();
        lifetimeDeterministicBlockLightSections += packetTemplate.blockLightSections();
        lifetimeDeterministicSkyLightSections += packetTemplate.skyLightSections();
        recordLodSend(lodLevel);
        player.networkHandler.sendPacket(packetTemplate.chunkPacket());
        player.networkHandler.sendPacket(packetTemplate.lightPacket());
    }

    private PacketTemplate getGlobalPacketTemplate(GlobalChunkKey key) {
        SoftReference<PacketTemplate> ref = globalPacketTemplateCache.get(key);
        if (ref == null) {
            return null;
        }
        PacketTemplate template = ref.get();
        if (template == null) {
            removeGlobalPacketTemplateEntry(key);
            return null;
        }
        return template;
    }

    private void invalidateGlobalPacketTemplatesForChunk(String worldKey, long chunkLong, int keepLodLevel) {
        for (int lod = 0; lod <= 3; lod++) {
            if (lod == keepLodLevel) {
                continue;
            }
            removeGlobalPacketTemplateEntry(new GlobalChunkKey(worldKey, chunkLong, lod));
        }
        globalPacketTemplateOrder.removeIf(key -> key.worldKey().equals(worldKey)
                && key.chunkLong() == chunkLong
                && key.lodLevel() != keepLodLevel);
    }

    private void putGlobalPacketTemplate(GlobalChunkKey key, PacketTemplate template) {
        if (config.globalPacketTemplateCacheMaxEntries() <= 0) {
            return;
        }
        SoftReference<PacketTemplate> previous = globalPacketTemplateCache.put(key, new SoftReference<>(template));
        int estimatedBytes = estimatePacketTemplateBytes(template);
        Integer previousBytes = globalPacketTemplateEstimatedBytes.put(key, estimatedBytes);
        if (previousBytes != null) {
            globalPacketTemplateTotalEstimatedBytes.addAndGet(-previousBytes.longValue());
        }
        globalPacketTemplateTotalEstimatedBytes.addAndGet(estimatedBytes);
        if (previous == null) {
            globalPacketTemplateOrder.add(key);
        }
        pruneGlobalPacketTemplateCache();
    }

    private void pruneGlobalPacketTemplateCache() {
        int maxEntries = config.globalPacketTemplateCacheMaxEntries();
        if (maxEntries <= 0) {
            globalPacketTemplateCache.clear();
            globalPacketTemplateOrder.clear();
            globalPacketTemplateEstimatedBytes.clear();
            globalPacketTemplateTotalEstimatedBytes.set(0L);
            return;
        }

        globalPacketTemplateOrder.removeIf(key -> {
            SoftReference<PacketTemplate> ref = globalPacketTemplateCache.get(key);
            if (ref == null || ref.get() == null) {
                removeGlobalPacketTemplateEntry(key);
                return true;
            }
            return false;
        });

        while (globalPacketTemplateCache.size() > maxEntries
                || globalPacketTemplateTotalEstimatedBytes.get() > GLOBAL_TEMPLATE_CACHE_MAX_BYTES) {
            GlobalChunkKey oldest = globalPacketTemplateOrder.poll();
            if (oldest == null) {
                break;
            }
            removeGlobalPacketTemplateEntry(oldest);
        }
    }

    private void removeGlobalPacketTemplateEntry(GlobalChunkKey key) {
        globalPacketTemplateCache.remove(key);
        Integer removedBytes = globalPacketTemplateEstimatedBytes.remove(key);
        if (removedBytes != null) {
            globalPacketTemplateTotalEstimatedBytes.addAndGet(-removedBytes.longValue());
        }
    }

    private static int estimatePacketTemplateBytes(PacketTemplate template) {
        int chunkBytes = Math.max(0, template.chunkPacketBytes());
        int lightBytes = Math.max(0, template.blockLightSections() + template.skyLightSections()) * 2048;
        return chunkBytes + lightBytes + 512;
    }

    private CachedPayload getCachedPayload(PlayerState state, long chunkLong) {
        CachedPayload cached = state.payloadCache.get(chunkLong);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAtTick() < ticks) {
            state.payloadCache.remove(chunkLong);
            return null;
        }
        state.payloadCache.putAndMoveToLast(chunkLong, cached);
        return cached;
    }

    private void putCachedPayload(
            PlayerState state,
            long chunkLong,
            PacketTemplate packetTemplate,
            int lodLevel) {
        if (config.payloadCacheTtlTicks() <= 0 || config.payloadCacheMaxEntriesPerPlayer() <= 0) {
            return;
        }

        state.payloadCache.putAndMoveToLast(
                chunkLong,
            new CachedPayload(packetTemplate, lodLevel, ticks + config.payloadCacheTtlTicks()));
        while (state.payloadCache.size() > config.payloadCacheMaxEntriesPerPlayer()) {
            state.payloadCache.remove(state.payloadCache.firstLongKey());
        }
    }

    private void prunePayloadCache(PlayerState state) {
        if (state.payloadCache.isEmpty()) {
            return;
        }
        if (config.payloadCacheTtlTicks() <= 0 || config.payloadCacheMaxEntriesPerPlayer() <= 0) {
            state.payloadCache.clear();
            return;
        }

        var iterator = state.payloadCache.long2ObjectEntrySet().fastIterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().expiresAtTick() < ticks) {
                iterator.remove();
            }
        }
        while (state.payloadCache.size() > config.payloadCacheMaxEntriesPerPlayer()) {
            state.payloadCache.remove(state.payloadCache.firstLongKey());
        }
    }

    private void unloadOutOfRange(
            ServerPlayerEntity player,
            PlayerState state,
            int centerX,
            int centerZ,
            int totalDistance,
            int normalDistance) {
        int unloadDistance = totalDistance + config.unloadBufferChunks();
        int unloadedThisTick = 0;
        long[] sentSnapshot = state.sent.toLongArray();
        for (long packed : sentSnapshot) {
            int chunkX = ChunkPos.getPackedX(packed);
            int chunkZ = ChunkPos.getPackedZ(packed);
            boolean insideNormal = withinDistance(chunkX, chunkZ, centerX, centerZ, normalDistance);
            boolean insideUnloadDistance = withinDistance(chunkX, chunkZ, centerX, centerZ, unloadDistance);

            if (insideNormal) {
                state.payloadCache.remove(packed);
                invalidateGlobalPacketTemplatesForChunk(state.worldKey, packed, -1);
                state.sent.remove(packed);
                state.sentLodByChunk.remove(packed);
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.unloadOutsideSinceTick.remove(packed);
                continue;
            }

            if (insideUnloadDistance) {
                state.unloadOutsideSinceTick.remove(packed);
                continue;
            }

            int firstOutsideTick = state.unloadOutsideSinceTick.getOrDefault(packed, Integer.MIN_VALUE);
            if (firstOutsideTick == Integer.MIN_VALUE) {
                state.unloadOutsideSinceTick.put(packed, ticks);
                continue;
            }

            if (ticks - firstOutsideTick < config.unloadGraceTicks()) {
                continue;
            }

            if (unloadedThisTick >= config.maxUnloadsPerPlayerPerTick()) {
                continue;
            }

            player.networkHandler.sendPacket(new UnloadChunkS2CPacket(new ChunkPos(chunkX, chunkZ)));
            totalUnloadPacketsSent++;
            lifetimeUnloadPacketsSent++;
            totalNetworkBytesEstimate += 9;
            lifetimeNetworkBytesEstimate += 9;
            state.sent.remove(packed);
            state.sentLodByChunk.remove(packed);
            state.pending.remove(packed);
            state.pendingLodByChunk.remove(packed);
            state.unloadOutsideSinceTick.remove(packed);
            unloadedThisTick++;
        }
    }

    private void prunePendingInsideNormal(PlayerState state, int centerX, int centerZ, int normalDistance) {
        long[] currentPending = state.pending.toLongArray();
        for (long packed : currentPending) {
            int chunkX = ChunkPos.getPackedX(packed);
            int chunkZ = ChunkPos.getPackedZ(packed);
            if (withinDistance(chunkX, chunkZ, centerX, centerZ, normalDistance)) {
                state.pending.remove(packed);
                state.pendingLodByChunk.remove(packed);
                state.unloadOutsideSinceTick.remove(packed);
            }
        }
    }

    public void onLikelyClientUnload(ServerPlayerEntity player, ChunkPos pos) {
        long stateKey = stateKey(player.getUuid(), player.getEntityWorld().getRegistryKey().getValue().toString());
        PlayerState state = statesByPlayer.get(stateKey);
        if (state == null) {
            return;
        }

        long packed = pos.toLong();
        state.sent.remove(packed);
        state.sentLodByChunk.remove(packed);
        state.pending.remove(packed);
        state.pendingLodByChunk.remove(packed);
        state.unloadOutsideSinceTick.remove(packed);
    }

    private void updateClientLoadDistance(ServerPlayerEntity player, PlayerState state, int totalDistance) {
        if (state.clientChunkLoadDistance == totalDistance) {
            return;
        }
        player.networkHandler.sendPacket(new ChunkLoadDistanceS2CPacket(totalDistance));
        state.clientChunkLoadDistance = totalDistance;
        totalChunkLoadDistancePackets++;
        lifetimeChunkLoadDistancePackets++;
        totalNetworkBytesEstimate += 5;
        lifetimeNetworkBytesEstimate += 5;
    }

    private void maybeLogMetrics(MinecraftServer server) {
        if (!config.metricsInfoLogsEnabled()) {
            return;
        }
        if (ticks % config.metricsLogIntervalTicks() != 0) {
            return;
        }

        long now = System.nanoTime();
        long windowNanos = Math.max(1L, now - metricsWindowStartNanos);
        double windowSeconds = windowNanos / 1_000_000_000.0;

        long pendingChunks = 0;
        long sentChunks = 0;
        long payloadCacheEntries = 0;
        for (PlayerState state : statesByPlayer.values()) {
            pendingChunks += state.pending.size();
            sentChunks += state.sent.size();
            payloadCacheEntries += state.payloadCache.size();
        }

        int onlinePlayers = server.getPlayerManager().getPlayerList().size();
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryBytes = runtime.totalMemory() - runtime.freeMemory();
        long committedMemoryBytes = runtime.totalMemory();
        long maxMemoryBytes = runtime.maxMemory();

        double netKiBPerSecond = (totalNetworkBytesEstimate / 1024.0) / windowSeconds;
        double cpuMillisPerSecond = (totalCpuNanos / 1_000_000.0) / windowSeconds;
        double cpuPercentSingleCore = (totalCpuNanos / (double) windowNanos) * 100.0;
        String windowState = (totalChunkPacketsSent == 0
            && totalUnloadPacketsSent == 0
            && totalChunkLoadDistancePackets == 0
            && totalDiskNbtReads == 0
            && totalNetworkBytesEstimate == 0)
            ? "idle"
            : "active";

        ViewExtendMod.LOGGER.debug(
            "[ViewExtend Metrics] windowState={} players={} trackedStates={} sentVisualChunks={} pendingVisualChunks={} preparedQueue={} nbtReadQueue={} globalTemplateCache={} globalTemplateBytes~={} payloadCacheEntries={} waterSkylightFallbackCache={} preparedQueued={} preparedProcessed={} preparedDropped={} nbtQueued={}(total={}) nbtProcessed={}(total={}) queueDeferrals={}(total={}) lodSends(0/1)={}/{}(total={}/{}) globalTemplateHits={}(total={}) globalTemplateMisses={}(total={}) cacheHits={}(total={}) cacheMisses={}(total={}) chunkPackets={}(total={}) unloadPackets={}(total={}) suppressedVanillaUnloads={}(total={}) forcedUpgradeUnloads={}(total={}) unsortedSectionInputs={}(total={}) loadDistancePackets={}(total={}) diskNbtReads={}(total={}) diskNbtMisses={}(total={}) deterministicLightSections(block/sky)={}/{}(total={}/{}) lightSourceSections sky(nbt/fallback/waterFallback)={}/{}/{}(total={}/{}/{}) block(nbt/fallback)={}/{}(total={}/{}) netBytes~={}(total={}) netKiBps~={}/s cpuMsps={} cpuSingleCore~{}% memUsedMiB={}/{}/{}",
            windowState,
                onlinePlayers,
                statesByPlayer.size(),
                sentChunks,
                pendingChunks,
                preparedChunkQueue.size(),
                nbtReadQueue.size(),
                globalPacketTemplateCache.size(),
                globalPacketTemplateTotalEstimatedBytes.get(),
                payloadCacheEntries,
                WATER_SKYLIGHT_FALLBACK_CACHE.size(),
                totalPreparedTasksQueued,
                totalPreparedTasksProcessed,
                totalPreparedTasksDropped,
                totalNbtRequestsQueued,
                lifetimeNbtRequestsQueued,
                totalNbtRequestsProcessed,
                lifetimeNbtRequestsProcessed,
                totalQueueBackpressureDeferrals,
                lifetimeQueueBackpressureDeferrals,
                totalLod0Sends,
                totalLod1Sends,
                lifetimeLod0Sends,
                lifetimeLod1Sends,
                totalGlobalTemplateHits,
                lifetimeGlobalTemplateHits,
                totalGlobalTemplateMisses,
                lifetimeGlobalTemplateMisses,
                totalPayloadCacheHits,
                lifetimePayloadCacheHits,
                totalPayloadCacheMisses,
                lifetimePayloadCacheMisses,
                totalChunkPacketsSent,
            lifetimeChunkPacketsSent,
                totalUnloadPacketsSent,
            lifetimeUnloadPacketsSent,
                totalSuppressedVanillaUnloads,
            lifetimeSuppressedVanillaUnloads,
                totalForcedUpgradeUnloads,
            lifetimeForcedUpgradeUnloads,
                totalUnsortedSectionInputs,
            lifetimeUnsortedSectionInputs,
                totalChunkLoadDistancePackets,
            lifetimeChunkLoadDistancePackets,
                totalDiskNbtReads,
            lifetimeDiskNbtReads,
                totalDiskNbtReadMisses,
            lifetimeDiskNbtReadMisses,
                totalDeterministicBlockLightSections,
                totalDeterministicSkyLightSections,
                lifetimeDeterministicBlockLightSections,
                lifetimeDeterministicSkyLightSections,
                totalSkyLightNbtSections,
                totalSkyLightFallbackSections,
                totalSkyLightWaterFallbackSections,
                lifetimeSkyLightNbtSections,
                lifetimeSkyLightFallbackSections,
                lifetimeSkyLightWaterFallbackSections,
                totalBlockLightNbtSections,
                totalBlockLightFallbackSections,
                lifetimeBlockLightNbtSections,
                lifetimeBlockLightFallbackSections,
                totalNetworkBytesEstimate,
            lifetimeNetworkBytesEstimate,
                String.format("%.2f", netKiBPerSecond),
                String.format("%.2f", cpuMillisPerSecond),
                String.format("%.2f", cpuPercentSingleCore),
                bytesToMiB(usedMemoryBytes),
            bytesToMiB(committedMemoryBytes),
                bytesToMiB(maxMemoryBytes));

        if (totalLegacyBlockIdRemaps > 0) {
            ViewExtendMod.LOGGER.debug(
                    "[ViewExtend Metrics] legacyBlockIdRemaps={} (total={})",
                    totalLegacyBlockIdRemaps,
                    lifetimeLegacyBlockIdRemaps);
        }

        metricsWindowStartNanos = now;
        totalCpuNanos = 0;
        totalChunkPacketsSent = 0;
        totalUnloadPacketsSent = 0;
        totalChunkLoadDistancePackets = 0;
        totalDiskNbtReads = 0;
        totalDiskNbtReadMisses = 0;
        totalNetworkBytesEstimate = 0;
        totalLegacyBlockIdRemaps = 0;
        totalSuppressedVanillaUnloads = 0;
        totalForcedUpgradeUnloads = 0;
        totalUnsortedSectionInputs = 0;
        totalPreparedTasksQueued = 0;
        totalPreparedTasksProcessed = 0;
        totalPreparedTasksDropped = 0;
        totalNbtRequestsQueued = 0;
        totalNbtRequestsProcessed = 0;
        totalQueueBackpressureDeferrals = 0;
        totalLod0Sends = 0;
        totalLod1Sends = 0;
        totalGlobalTemplateHits = 0;
        totalGlobalTemplateMisses = 0;
        totalPayloadCacheHits = 0;
        totalPayloadCacheMisses = 0;
        totalDeterministicBlockLightSections = 0;
        totalDeterministicSkyLightSections = 0;
        totalSkyLightNbtSections = 0;
        totalSkyLightFallbackSections = 0;
        totalSkyLightWaterFallbackSections = 0;
        totalBlockLightNbtSections = 0;
        totalBlockLightFallbackSections = 0;
    }

    private LightData createDeterministicLightData(
            ChunkPos pos,
            int bottomSectionY,
            int topSectionY,
            SerializedChunk serializedChunk,
            boolean hasSkyLight,
            boolean forceFullBright,
            boolean isOceanBiome) {
        int sectionHeight = Math.max(0, topSectionY - bottomSectionY);
        if (sectionHeight == 0) {
            PacketByteBuf emptyBuffer = new PacketByteBuf(Unpooled.buffer(32));
            emptyBuffer.writeBitSet(EMPTY_BIT_SET);
            emptyBuffer.writeBitSet(EMPTY_BIT_SET);
            emptyBuffer.writeBitSet(EMPTY_BIT_SET);
            emptyBuffer.writeBitSet(EMPTY_BIT_SET);
            emptyBuffer.writeCollection(List.<byte[]>of(), (buf, array) -> buf.writeByteArray(array));
            emptyBuffer.writeCollection(List.<byte[]>of(), (buf, array) -> buf.writeByteArray(array));
            LightData lightData = new LightData(emptyBuffer, pos.x, pos.z);
            emptyBuffer.release();
            return lightData;
        }

        BitSet initedSky = new BitSet();
        BitSet initedBlock = new BitSet();
        List<byte[]> skyNibbles = new ArrayList<>(sectionHeight);
        List<byte[]> blockNibbles = new ArrayList<>(sectionHeight);

        SerializedChunk.SectionData[] sectionDataByIndex = new SerializedChunk.SectionData[sectionHeight];
        for (SerializedChunk.SectionData sectionData : serializedChunk.sectionData()) {
            int index = sectionData.y() - bottomSectionY;
            if (index >= 0 && index < sectionHeight) {
                sectionDataByIndex[index] = sectionData;
            }
        }

        for (int section = 0; section < sectionHeight; section++) {
            SerializedChunk.SectionData sectionData = sectionDataByIndex[section];
            int sectionY = bottomSectionY + section;
            // Consider section underwater if it's in an ocean biome and at or below sea level (Y=64)
            boolean isUnderwaterSection = isOceanBiome && (sectionY * 16) < 64;

            if (hasSkyLight) {
                initedSky.set(section);
                if (!forceFullBright && sectionData != null && sectionData.skyLight() != null && !sectionData.skyLight().isUninitialized()) {
                    totalSkyLightNbtSections++;
                    lifetimeSkyLightNbtSections++;
                    skyNibbles.add(sectionData.skyLight().asByteArray());
                } else {
                    totalSkyLightFallbackSections++;
                    lifetimeSkyLightFallbackSections++;
                    if (forceFullBright) {
                        skyNibbles.add(fallbackSkyLightNibble);
                    } else {
                        if (isUnderwaterSection) {
                            if (config.oceanFallbackEnabled()) {
                                // Use configured ocean fallback light level
                                skyNibbles.add(oceanFallbackSkyLightNibble);
                            } else {
                                // Use gradient-based water-aware fallback
                                byte[] fallbackSky = getWaterAwareSkyLightFallback(sectionY);
                                totalSkyLightWaterFallbackSections++;
                                lifetimeSkyLightWaterFallbackSections++;
                                skyNibbles.add(fallbackSky);
                            }
                        } else {
                            skyNibbles.add(fallbackSkyLightNibble);
                        }
                    }
                }
            }

            initedBlock.set(section);
            if (!forceFullBright && sectionData != null && sectionData.blockLight() != null && !sectionData.blockLight().isUninitialized()) {
                totalBlockLightNbtSections++;
                lifetimeBlockLightNbtSections++;
                blockNibbles.add(sectionData.blockLight().asByteArray());
            } else {
                totalBlockLightFallbackSections++;
                lifetimeBlockLightFallbackSections++;
                blockNibbles.add(forceFullBright ? fallbackSkyLightNibble : (hasSkyLight ? fallbackBlockLightNibble : fallbackSkyLightNibble));
            }
        }

        int perSectionNibbleBytes = hasSkyLight ? 4096 : 2048;
        int estimatedSize = 128 + (sectionHeight * perSectionNibbleBytes);
        PacketByteBuf buffer = new PacketByteBuf(Unpooled.buffer(Math.max(256, estimatedSize)));
        buffer.writeBitSet(initedSky);
        buffer.writeBitSet(initedBlock);
        buffer.writeBitSet(EMPTY_BIT_SET);
        buffer.writeBitSet(EMPTY_BIT_SET);
        buffer.writeCollection(skyNibbles, (buf, array) -> buf.writeByteArray(array));
        buffer.writeCollection(blockNibbles, (buf, array) -> buf.writeByteArray(array));
        LightData lightData = new LightData(buffer, pos.x, pos.z);
        buffer.release();
        return lightData;
    }

    public void shutdown() {
        preprocessExecutor.shutdownNow();
        preparedChunkQueue.clear();
        nbtReadQueue.clear();
        globalPacketTemplateCache.clear();
        globalPacketTemplateOrder.clear();
        globalPacketTemplateEstimatedBytes.clear();
        globalPacketTemplateTotalEstimatedBytes.set(0L);
    }

    public boolean shouldSuppressVanillaUnload(ServerPlayerEntity player, ChunkPos pos) {
        int normalDistance = player.getEntityWorld().getServer().getPlayerManager().getViewDistance();
        int totalDistance = getEffectiveTotalDistance(player, normalDistance);
        int centerX = player.getChunkPos().x;
        int centerZ = player.getChunkPos().z;

        if (!withinDistance(pos.x, pos.z, centerX, centerZ, totalDistance)) {
            return false;
        }
        if (withinDistance(pos.x, pos.z, centerX, centerZ, normalDistance)) {
            return false;
        }

        String worldKey = player.getEntityWorld().getRegistryKey().getValue().toString();
        long stateKey = stateKey(player.getUuid(), worldKey);
        PlayerState state = statesByPlayer.computeIfAbsent(stateKey, ignored -> new PlayerState(player.getUuid(), worldKey));

        long packed = pos.toLong();
        if (!state.sent.contains(packed)) {
            int retainedLodLevel = resolveLodLevel(centerX, centerZ, pos.x, pos.z);
            state.sent.add(packed);
            state.sentLodByChunk.put(packed, retainedLodLevel);
        }

        state.pending.remove(packed);
        state.pendingLodByChunk.remove(packed);
        state.unloadOutsideSinceTick.remove(packed);

        totalSuppressedVanillaUnloads++;
        lifetimeSuppressedVanillaUnloads++;
        return true;
    }

    public int getEffectiveTotalDistance(ServerPlayerEntity player, int normalDistance) {
        int configuredTotal = normalDistance + config.unsimulatedViewDistance();
        int hardCap = config.clientReportedViewDistanceHardCap();
        int rawClientDistance = player.getViewDistance();
        int normalizedClientDistance;
        if (rawClientDistance > 0) {
            normalizedClientDistance = rawClientDistance;
        } else if (rawClientDistance < 0) {
            normalizedClientDistance = rawClientDistance & 0xFF;
        } else {
            normalizedClientDistance = configuredTotal;
        }

        int effectiveClientDistance = normalizedClientDistance > 0 ? normalizedClientDistance : configuredTotal;
        if (hardCap > 0) {
            effectiveClientDistance = Math.min(effectiveClientDistance, hardCap);
        }
        int cappedByClient = Math.min(configuredTotal, effectiveClientDistance);
        return Math.max(normalDistance, cappedByClient);
    }

    private int remapLegacyBlockIds(NbtCompound chunkNbt) {
        int dataVersion = chunkNbt.getInt("DataVersion", Integer.MAX_VALUE);
        if (dataVersion > LEGACY_BLOCK_REMAP_MAX_DATA_VERSION) {
            return 0;
        }

        int remapCount = 0;
        NbtList sections = chunkNbt.getListOrEmpty("sections");
        for (int i = 0; i < sections.size(); i++) {
            NbtCompound section = sections.getCompoundOrEmpty(i);
            NbtCompound blockStates = section.getCompoundOrEmpty("block_states");
            NbtList palette = blockStates.getListOrEmpty("palette");

            for (int paletteIndex = 0; paletteIndex < palette.size(); paletteIndex++) {
                NbtCompound state = palette.getCompoundOrEmpty(paletteIndex);
                String name = state.getString("Name", "");
                String remapped = LEGACY_BLOCK_ID_REMAP.get(name);
                if (remapped != null) {
                    state.putString("Name", remapped);
                    remapCount++;
                }
            }
        }
        return remapCount;
    }

    private static long bytesToMiB(long bytes) {
        return bytes / (1024L * 1024L);
    }

    private int resolveLodLevel(int centerX, int centerZ, int chunkX, int chunkZ) {
        int distance = Math.max(Math.abs(chunkX - centerX), Math.abs(chunkZ - centerZ));
        if (distance >= config.lod1StartDistance()) {
            return 1;
        }
        return 0;
    }

    private void applyLodToChunkNbt(NbtCompound chunkNbt, int lodLevel) {
        if (lodLevel <= 0) {
            return;
        }

        NbtList sections = chunkNbt.getListOrEmpty("sections");
        if (sections.isEmpty()) {
            return;
        }

        int previousSectionY = Integer.MIN_VALUE;
        boolean unsortedSectionOrder = false;
        for (int index = 0; index < sections.size(); index++) {
            int sectionY = sections.getCompoundOrEmpty(index).getByte("Y", (byte) 0);
            if (sectionY < previousSectionY) {
                unsortedSectionOrder = true;
                break;
            }
            previousSectionY = sectionY;
        }
        if (unsortedSectionOrder) {
            totalUnsortedSectionInputs++;
            lifetimeUnsortedSectionInputs++;
        }

        List<NbtCompound> sortedSections = new ArrayList<>(sections.size());
        for (int index = 0; index < sections.size(); index++) {
            sortedSections.add(sections.getCompoundOrEmpty(index));
        }
        sortedSections.sort(Comparator.comparingInt(section -> section.getByte("Y", (byte) 0)));

        int keepNonAirSections = config.lod1TopNonAirSections();

        int highestNonAirSectionY = Integer.MIN_VALUE;
        for (NbtCompound section : sortedSections) {
            if (!sectionHasNonAir(section)) {
                continue;
            }
            int y = section.getByte("Y", (byte) 0);
            if (y > highestNonAirSectionY) {
                highestNonAirSectionY = y;
            }
        }

        if (highestNonAirSectionY == Integer.MIN_VALUE) {
            return;
        }

        int minKeepY = highestNonAirSectionY - (keepNonAirSections - 1);
        NbtList filteredSections = new NbtList();
        for (NbtCompound section : sortedSections) {
            int y = section.getByte("Y", (byte) 0);
            if (y >= minKeepY && sectionHasNonAir(section)) {
                filteredSections.add(section.copy());
            }
        }
        chunkNbt.put("sections", filteredSections);

        chunkNbt.put("block_entities", new NbtList());
    }

    private boolean sectionHasNonAir(NbtCompound section) {
        NbtCompound blockStates = section.getCompoundOrEmpty("block_states");
        NbtList palette = blockStates.getListOrEmpty("palette");
        for (int index = 0; index < palette.size(); index++) {
            NbtCompound state = palette.getCompoundOrEmpty(index);
            String name = state.getString("Name", "");
            if (!"minecraft:air".equals(name) && !"minecraft:cave_air".equals(name) && !"minecraft:void_air".equals(name)) {
                return true;
            }
        }
        return false;
    }

    private void recordLodSend(int lodLevel) {
        switch (normalizeLodLevel(lodLevel)) {
            case 0 -> {
                totalLod0Sends++;
                lifetimeLod0Sends++;
            }
            default -> {
                totalLod1Sends++;
                lifetimeLod1Sends++;
            }
        }
    }

    private static int normalizeLodLevel(int lodLevel) {
        return lodLevel <= 0 ? 0 : 1;
    }

    private static byte[] createFilledNibble(byte value) {
        byte[] data = new byte[2048];
        if (value != 0) {
            java.util.Arrays.fill(data, value);
        }
        return data;
    }

    private static byte[] createLightLevelNibble(int lightLevel) {
        if (lightLevel < 0 || lightLevel > 15) {
            throw new IllegalArgumentException("Light level must be 0-15, got: " + lightLevel);
        }
        // Each byte contains two 4-bit nibbles (high and low)
        byte nibbleValue = (byte) ((lightLevel << 4) | lightLevel);
        return createFilledNibble(nibbleValue);
    }

    /**
     * Checks if a chunk is in an ocean biome by examining the biome list in the NBT.
     * Biomes are stored as a palette of biome strings.
     * 
     * @param chunkNbt The chunk NBT data
     * @return true if the chunk's primary biome is an ocean variant
     */
    private static boolean isChunkInOceanBiome(NbtCompound chunkNbt) {
        try {
            NbtList sections = chunkNbt.getListOrEmpty("sections");
            for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
                NbtCompound section = sections.getCompoundOrEmpty(sectionIndex);
                NbtCompound biomes = section.getCompoundOrEmpty("biomes");
                NbtList biomePalette = biomes.getListOrEmpty("palette");

                for (int paletteIndex = 0; paletteIndex < biomePalette.size(); paletteIndex++) {
                    String biomeId = biomePalette.getString(paletteIndex).orElse("");
                    if (biomeId.isEmpty()) {
                        NbtCompound biomeEntry = biomePalette.getCompoundOrEmpty(paletteIndex);
                        biomeId = biomeEntry.getString("Name", "");
                    }

                    if (!biomeId.isEmpty() && isOceanBiomeId(biomeId)) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            ViewExtendMod.LOGGER.debug("Error detecting ocean biome from section palettes", e);
        }
        return false;
    }

    /**
     * Checks if a chunk is in an ocean biome from a WorldChunk.
     * Simplified version - returns false as NBT-based detection is more reliable.
     * 
     * @param chunk The world chunk
     * @return false (NBT-based detection is used in preprocessing where it matters)
     */
    private static boolean isChunkInOcean(WorldChunk chunk) {
        // The NBT-based detection is used during preprocessing
        // Here we return false as a safe default for runtime chunk checks
        return false;
    }

    /**
     * Checks if a biome ID represents a water-dominant biome.
     * 
     * @param biomeId The biome ID string
     * @return true if the biome is water-dominant (ocean, river, etc.)
     */
    private static boolean isOceanBiomeId(String biomeId) {
        String lower = biomeId.toLowerCase();
        return lower.contains("ocean")
                || lower.contains("sea")
                || lower.contains("river")
                || lower.contains("beach")
                || lower.contains("shore")
                || lower.contains("swamp")
                || lower.contains("mangrove");
    }

    /**
     * Determines the appropriate sky light fallback based on water depth estimation.
     * Uses section Y coordinate to estimate depth and apply realistic water lighting.
     * Only called for sections that are in ocean biomes and below sea level (Y=64).
     * 
     * @param sectionY The Y coordinate of the section
     * @return The appropriate fallback nibble array for the estimated water depth
     */
    private static byte[] getWaterAwareSkyLightFallback(int sectionY) {
        return WATER_SKYLIGHT_FALLBACK_CACHE.computeIfAbsent(sectionY, ViewExtendService::createWaterAwareSkyLightFallback);
    }

    private static byte[] createWaterAwareSkyLightFallback(int sectionY) {
        int seaSurfaceY = 63;
        int sectionBaseY = sectionY * 16;
        ChunkNibbleArray nibbleArray = new ChunkNibbleArray(0);

        for (int localY = 0; localY < 16; localY++) {
            int blockY = sectionBaseY + localY;
            int depth = Math.max(0, seaSurfaceY - blockY);
            int brightness = Math.max(0, 15 - depth);

            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    nibbleArray.set(x, localY, z, brightness);
                }
            }
        }

        return nibbleArray.asByteArray();
    }

    private void recomputeEffectiveBudgets() {
        int configuredChunkBudget = config.maxChunksPerPlayerPerTick();
        int configuredPreparedBudget = config.maxMainThreadPreparedChunksPerTick();

        if (cpuEmaNanos <= 0) {
            effectiveChunkQueueBudget = configuredChunkBudget;
            effectivePreparedProcessBudget = configuredPreparedBudget;
            return;
        }

        long cpuMs = cpuEmaNanos / 1_000_000L;
        int divisor;
        if (cpuMs >= 30) {
            divisor = 8;
        } else if (cpuMs >= 18) {
            divisor = 4;
        } else if (cpuMs >= 10) {
            divisor = 2;
        } else {
            divisor = 1;
        }

        effectiveChunkQueueBudget = Math.max(1, configuredChunkBudget / divisor);
        effectivePreparedProcessBudget = Math.max(8, configuredPreparedBudget / divisor);
    }

    private void updateCpuEma(long currentTickCpuNanos) {
        if (cpuEmaNanos == 0) {
            cpuEmaNanos = currentTickCpuNanos;
            return;
        }
        cpuEmaNanos = ((cpuEmaNanos * 7) + currentTickCpuNanos) / 8;
    }

    private void cleanupDisconnected(MinecraftServer server) {
        List<Long> toRemove = new ArrayList<>();
        statesByPlayer.forEach((key, state) -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(state.playerUuid);
            if (player == null) {
                toRemove.add(key);
                return;
            }

            String currentWorldKey = player.getEntityWorld().getRegistryKey().getValue().toString();
            if (!currentWorldKey.equals(state.worldKey)) {
                toRemove.add(key);
            }
        });

        for (Long key : toRemove) {
            statesByPlayer.remove(key);
        }
    }

    private List<PlayerTickTarget> collectTickTargets(MinecraftServer server) {
        List<PlayerTickTarget> targets = new ArrayList<>();
        for (ServerWorld world : server.getWorlds()) {
            List<ServerPlayerEntity> players = world.getPlayers();
            if (players.isEmpty()) {
                continue;
            }
            for (ServerPlayerEntity player : players) {
                targets.add(new PlayerTickTarget(world, player));
            }
        }
        return targets;
    }

    private int[] allocatePerPlayerBudgets(List<PlayerTickTarget> targets, int basePerPlayerBudget) {
        int count = targets.size();
        int[] budgets = new int[count];
        if (count == 0 || basePerPlayerBudget <= 0) {
            return budgets;
        }

        int globalBudget = basePerPlayerBudget * count;
        int baseFloor = Math.min(basePerPlayerBudget, 2);

        int weightSum = 0;
        int spent = 0;
        int[] weights = new int[count];
        int[] caps = new int[count];

        for (int index = 0; index < count; index++) {
            ServerPlayerEntity player = targets.get(index).player();
            boolean priority = isPriorityStreamer(player);
            int weight = priority ? PRIORITY_WEIGHT : NORMAL_WEIGHT;
            int cap = priority ? Math.max(basePerPlayerBudget, basePerPlayerBudget * 6) : basePerPlayerBudget;

            budgets[index] = Math.min(baseFloor, cap);
            weights[index] = weight;
            caps[index] = cap;
            spent += budgets[index];
            weightSum += weight;
        }

        int remaining = Math.max(0, globalBudget - spent);
        if (remaining <= 0 || weightSum <= 0) {
            return budgets;
        }

        for (int index = 0; index < count; index++) {
            if (remaining <= 0) {
                break;
            }
            int share = (remaining * weights[index]) / weightSum;
            if (share <= 0) {
                continue;
            }
            int allocatable = Math.min(share, caps[index] - budgets[index]);
            if (allocatable <= 0) {
                continue;
            }
            budgets[index] += allocatable;
            remaining -= allocatable;
        }

        if (remaining > 0) {
            List<Integer> order = new ArrayList<>(count);
            for (int index = 0; index < count; index++) {
                order.add(index);
            }
            order.sort(Comparator.comparingInt((Integer index) -> weights[index]).reversed());

            int cursor = 0;
            while (remaining > 0 && !order.isEmpty()) {
                int index = order.get(cursor % order.size());
                if (budgets[index] < caps[index]) {
                    budgets[index]++;
                    remaining--;
                }
                cursor++;
                if (cursor > order.size() * 8) {
                    break;
                }
            }
        }

        return budgets;
    }

    private static boolean isPriorityStreamer(ServerPlayerEntity player) {
        return player.getAbilities().flying;
    }

    private static int[] ringOffsets(int radius) {
        if (radius <= 0) {
            return new int[0];
        }
        return RING_OFFSET_CACHE.computeIfAbsent(radius, ViewExtendService::computeRingOffsets);
    }

    private static int[] computeRingOffsets(int radius) {
        int sideLength = radius * 2 + 1;
        int perimeter = Math.max(1, sideLength * 4 - 4);
        int[] offsets = new int[perimeter * 2];
        int index = 0;

        int min = -radius;
        int max = radius;

        for (int x = min; x <= max; x++) {
            offsets[index++] = x;
            offsets[index++] = min;
            if (min != max) {
                offsets[index++] = x;
                offsets[index++] = max;
            }
        }

        for (int z = min + 1; z < max; z++) {
            offsets[index++] = min;
            offsets[index++] = z;
            if (min != max) {
                offsets[index++] = max;
                offsets[index++] = z;
            }
        }

        if (index == offsets.length) {
            return offsets;
        }

        int[] trimmed = new int[index];
        System.arraycopy(offsets, 0, trimmed, 0, index);
        return trimmed;
    }

    private boolean shouldDeferChunkSelection(PlayerState state, boolean movedChunk) {
        if (movedChunk) {
            return false;
        }
        if (cpuEmaNanos <= 0L) {
            return false;
        }

        long cpuMs = cpuEmaNanos / 1_000_000L;
        int phase = state.selectionPhase;
        if (cpuMs >= 30) {
            return (ticks + phase) % 4 != 0;
        }
        if (cpuMs >= 18) {
            return (ticks + phase) % 2 != 0;
        }
        return false;
    }

    private boolean isPreparedQueueAtHardLimit() {
        int hardLimit = config.preparedQueueHardLimit();
        return hardLimit > 0 && preparedChunkQueue.size() >= hardLimit;
    }

    private static boolean withinDistance(int chunkX, int chunkZ, int centerX, int centerZ, int radius) {
        return Math.abs(chunkX - centerX) <= radius && Math.abs(chunkZ - centerZ) <= radius;
    }

    private static long stateKey(UUID uuid, String worldKey) {
        return ((long) uuid.hashCode() << 32) ^ worldKey.hashCode();
    }

    private record PlayerTickTarget(ServerWorld world, ServerPlayerEntity player) {
    }

    private record NbtReadRequest(UUID playerUuid, String worldKey, ChunkPos pos, int lodLevel, boolean upgradePriority) {
    }

    private record GlobalChunkKey(String worldKey, long chunkLong, int lodLevel) {
    }

    private record PreparedChunkTask(
            UUID playerUuid,
            String worldKey,
            ChunkPos pos,
            int lodLevel,
            NbtCompound chunkNbt,
            SerializedChunk serializedChunk,
            LightData deterministicLightData,
            int remapCount,
            boolean missing,
            boolean failed) {
        private static PreparedChunkTask withNbt(UUID playerUuid, String worldKey, ChunkPos pos, NbtCompound chunkNbt, int remapCount, int lodLevel) {
            return new PreparedChunkTask(playerUuid, worldKey, pos, lodLevel, chunkNbt, null, null, remapCount, false, false);
        }

        private static PreparedChunkTask parsed(
                UUID playerUuid,
                String worldKey,
                ChunkPos pos,
                int lodLevel,
                SerializedChunk serializedChunk,
                LightData deterministicLightData,
                int remapCount) {
            return new PreparedChunkTask(playerUuid, worldKey, pos, lodLevel, null, serializedChunk, deterministicLightData, remapCount, false, false);
        }

        private static PreparedChunkTask missing(UUID playerUuid, String worldKey, ChunkPos pos, int lodLevel) {
            return new PreparedChunkTask(playerUuid, worldKey, pos, lodLevel, null, null, null, 0, true, false);
        }

        private static PreparedChunkTask failed(UUID playerUuid, String worldKey, ChunkPos pos, int lodLevel) {
            return new PreparedChunkTask(playerUuid, worldKey, pos, lodLevel, null, null, null, 0, false, true);
        }

        private boolean parsedReady() {
            return serializedChunk != null;
        }
    }

    private record PacketTemplate(
            ChunkDataS2CPacket chunkPacket,
            LightUpdateS2CPacket lightPacket,
            int chunkPacketBytes,
            int blockLightSections,
            int skyLightSections) {
    }

    private record CachedPayload(
            PacketTemplate packetTemplate,
            int lodLevel,
            int expiresAtTick) {
    }

    private static final class PlayerState {
        private final UUID playerUuid;
        private final String worldKey;
        private final LongOpenHashSet sent = new LongOpenHashSet();
        private final LongOpenHashSet pending = new LongOpenHashSet();
        private final Long2IntOpenHashMap sentLodByChunk = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap pendingLodByChunk = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap missingUntilTick = new Long2IntOpenHashMap();
        private final Long2IntOpenHashMap unloadOutsideSinceTick = new Long2IntOpenHashMap();
        private final Long2ObjectLinkedOpenHashMap<CachedPayload> payloadCache = new Long2ObjectLinkedOpenHashMap<>();
        private final int selectionPhase;
        private int clientChunkLoadDistance = Integer.MIN_VALUE;
        private int centerX = Integer.MIN_VALUE;
        private int centerZ = Integer.MIN_VALUE;

        private PlayerState(UUID playerUuid, String worldKey) {
            this.playerUuid = playerUuid;
            this.worldKey = worldKey;
            this.selectionPhase = Math.floorMod(playerUuid.hashCode(), 4);
        }
    }
}