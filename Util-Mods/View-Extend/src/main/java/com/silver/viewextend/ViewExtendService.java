package com.silver.viewextend;

import com.silver.viewextend.VisualChunkPreparer.*;
import com.silver.viewextend.PlayerViewState.ScheduledChunk;
import static com.silver.viewextend.ViewExtendGeometry.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.LongAdder;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.fabricmc.fabric.api.networking.v1.context.PacketContextProvider;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

/** Streams read-only, unsimulated chunk snapshots outside the vanilla view distance. */
public final class ViewExtendService {
    private final ViewExtendConfig config;
    private final VisualChunkLoader loader;
    private final PlayerViewPlanner planner;

    // Server-thread owned, except completedLoadQueue and the LongAdders.
    private final Map<PlayerWorldKey, PlayerViewState> statesByPlayer = new HashMap<>();
    private final ArrayDeque<GlobalChunkKey> nbtReadQueue = new ArrayDeque<>();
    private final Map<GlobalChunkKey, SharedLoad> loadsByKey = new HashMap<>();
    private final ConcurrentLinkedQueue<PreparedLoadResult> completedLoadQueue = new ConcurrentLinkedQueue<>();
    private final ArrayDeque<ReadyDelivery> readyDeliveries = new ArrayDeque<>();
    private final ReuseAwareChunkCache<GlobalChunkKey, PreparedVisualChunk> packetCache;
    private final ChunkRetryCache<ChunkSourceKey> retryCache = new ChunkRetryCache<>(65536);
    private final java.util.EnumMap<VisualChunkFailure, Long> failureCounts = new java.util.EnumMap<>(VisualChunkFailure.class);
    private final java.util.EnumMap<VisualChunkFailure, Integer> nextFailureLog = new java.util.EnumMap<>(VisualChunkFailure.class);
    private long totalCooldownHits;
    private final LongAdder completedTasksQueuedWindow = new LongAdder();

    private boolean sendingVisualPacket;
    private volatile boolean shuttingDown;
    private int ticks;
    private long metricsWindowStartNanos = System.nanoTime();

    // Server-thread metrics. Worker results carry their own stats back to this thread.
    private long totalMainCpuNanos;
    private long totalWorkerCpuNanos;
    private long totalChunkPacketsSent;
    private long totalUnloadPacketsSent;
    private long totalDiskNbtReads;
    private long totalDiskNbtReadMisses;
    private long totalNetworkBytesEstimate;
    private long totalChunkLoadDistancePackets;
    private long totalLegacyBlockIdRemaps;
    private long totalUnsortedSectionInputs;
    private long totalNbtRequestsQueued;
    private long totalNbtRequestsStarted;
    private long totalCoalescedRequests;
    private long totalPreparedResultsProcessed;
    private long totalPreparedResultsDropped;
    private long totalPipelineDeferrals;
    private long totalCacheHits;
    private long totalCacheMisses;
    private long totalCandidateInspections;
    private long totalSuppressedVanillaUnloads;
    private long totalSkyLightNbtSections;
    private long totalSkyLightFallbackSections;
    private long totalSkyLightWaterFallbackSections;
    private long totalBlockLightNbtSections;
    private long totalBlockLightFallbackSections;

    public ViewExtendService(ViewExtendConfig config) {
        this.config = config;
        this.adaptive = new AdaptiveWorkBudget(config.maxNbtReadsPerTick());
        this.loader = new VisualChunkLoader(config);
        this.planner = new PlayerViewPlanner(config);
        this.packetCache = new ReuseAwareChunkCache<>(config.globalPacketTemplateCacheMaxEntries(),
                config.globalPacketTemplateCacheMaxBytes(), config.globalPacketTemplateCacheTtlTicks(),
                PreparedVisualChunk::estimatedBytes);
    }

    private final java.util.Map<String, it.unimi.dsi.fastutil.ints.IntOpenHashSet> loadedDragons = new java.util.HashMap<>();

    public void onEntityLoaded(net.minecraft.world.entity.Entity entity, ServerLevel world) {
        if (entity instanceof net.minecraft.world.entity.boss.enderdragon.EnderDragon)
            loadedDragons.computeIfAbsent(world.dimension().identifier().toString(), ignored -> new it.unimi.dsi.fastutil.ints.IntOpenHashSet()).add(entity.getId());
    }

    public void onEntityUnloaded(net.minecraft.world.entity.Entity entity, ServerLevel world) {
        var ids = loadedDragons.get(world.dimension().identifier().toString());
        if (ids != null) ids.remove(entity.getId());
    }

    private int sendsThisTick;
    private final AdaptiveWorkBudget adaptive;
    private long vanillaTickStarted;
    private boolean borrowing;
    private double typicalPacketBytes = 48 * 1024;
    public void beginTick() { vanillaTickStarted = System.nanoTime(); }
    private boolean hasWorkTime() { return adaptive.hasTime(System.nanoTime()); }

    public void tick(MinecraftServer server) {
        long tickStartNanos = System.nanoTime();
        ticks++;
        planner.ticks = ticks;

        if (shuttingDown) return;
        sendsThisTick = 0;
        borrowing = false;
        adaptive.begin(tickStartNanos, vanillaTickStarted == 0 ? 0 : tickStartNanos - vanillaTickStarted,
                loader.pressure(), !loadsByKey.isEmpty() || !readyDeliveries.isEmpty());
        cleanupDisconnected(server);
        List<PlayerTickTarget> targets = collectTickTargets(server);
        // Rotate the first player so a saturated shared pipeline cannot favor join order.
        if (!targets.isEmpty()) java.util.Collections.rotate(targets, ticks % targets.size());
        try {
            for (PlayerTickTarget target : targets) {
                tickPlayer(target.world(), target.player());
            }
            int active = Math.max(1, (int) statesByPlayer.values().stream()
                    .filter(state -> state.bootstrapActive || !state.pending.isEmpty() || !state.candidateQueue.isEmpty()).count());
            for (PlayerViewState state : statesByPlayer.values()) {
                state.share.accrue((long) (typicalPacketBytes * config.maxMainThreadPreparedChunksPerTick() / active),
                        adaptive.allowance() / active);
            }
            int preparedBudget = config.maxMainThreadPreparedChunksPerTick();
            transferCompletedLoads(server, Math.max(256, preparedBudget));
            processNbtReadRequests(server, adaptive.reads());
            processReadyDeliveries(server, preparedBudget);
            scheduleCandidates(targets);
            // Unused shares are borrowable only after every active observer had its fair pass.
            borrowing = true;
            processReadyDeliveries(server, preparedBudget);
            scheduleCandidates(targets);
            packetCache.expire(ticks);
            if (targets.isEmpty()) { packetCache.clear(); retryCache.clear(); }
            refreshPlayerEntities(server);

        } finally {
            for (PlayerTickTarget target : targets) finishVisualBatch(target.player());
        }
        totalMainCpuNanos += System.nanoTime() - tickStartNanos;
        maybeLogMetrics(server);
    }

    private void tickPlayer(ServerLevel world, ServerPlayer player) {
        String worldKey = world.dimension().identifier().toString();
        PlayerWorldKey playerWorldKey = new PlayerWorldKey(player.getUUID(), worldKey);
        PlayerViewState state = statesByPlayer.computeIfAbsent(
                playerWorldKey,
                ignored -> new PlayerViewState(player.getUUID(), worldKey));

        int centerX = player.chunkPosition().x();
        int centerZ = player.chunkPosition().z();
        int normalDistance = world.getServer().getPlayerList().getViewDistance();
        int totalDistance = getEffectiveTotalDistance(player, normalDistance);

        state.sendsThisTick = 0;
        updateClientLoadDistance(player, state, totalDistance);
        planner.updatePlayerView(state, centerX, centerZ, normalDistance, totalDistance);
        planner.updateVanillaView(state, player.getChunkTrackingView());
        planner.drainDueRetries(state);
        processDueUnloads(player, state, centerX, centerZ, totalDistance);
        var flow = (com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender;
        state.lookahead = adaptive.lookahead(flow.viewextend$getDesiredRate(), config.pendingChunksHardLimit());
    }

    private void scheduleCandidates(List<PlayerTickTarget> targets) {
        for (int round = 0; round < config.maxChunksPerPlayerPerTick() && hasWorkTime(); round++) {
            boolean attempted = false;
            for (PlayerTickTarget target : targets) {
                if (!hasWorkTime()) return;
                var player = target.player();
                var state = statesByPlayer.get(new PlayerWorldKey(player.getUUID(), target.world().dimension().identifier().toString()));
                if (state == null || !isEnabled() || !player.isAlive()
                        || (!state.bootstrapActive && state.candidateQueue.isEmpty() && !state.candidateOverflowed)
                        || !canSend(player, state)) continue;
                attempted = true;
                long started = System.nanoTime();
                processCandidates(target.world(), player, state, state.centerX, state.centerZ, state.normalDistance, state.totalDistance, 1);
                state.share.charge(0, System.nanoTime() - started);
            }
            if (!attempted) break;
        }
    }

    private void processCandidates(
            ServerLevel world,
            ServerPlayer player,
            PlayerViewState state,
            int centerX,
            int centerZ,
            int normalDistance,
            int totalDistance,
            int maxPerTick) {
        int accepted = 0;
        int inspected = 0;
        int inspectionBudget = Math.max(16, maxPerTick * 16);

        while (accepted < maxPerTick && inspected < inspectionBudget && hasWorkTime()) {
            long packed;
            if (!state.candidateQueue.isEmpty() && (!state.bootstrapActive || (state.candidateSelectionCounter++ & 1) == 0)) {
                packed = state.candidateQueue.dequeueLong();
                state.candidateQueued.remove(packed);
            } else {
                packed = planner.nextBootstrapCandidate(state);
                if (packed == Long.MIN_VALUE) {
                    if (state.candidateOverflowed) {
                        state.candidateOverflowed = false;
                        planner.resetBootstrap(state, centerX, centerZ, normalDistance, totalDistance);
                        continue;
                    }
                    break;
                }
            }

            inspected++;
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);
            if (!withinDistance(chunkX, chunkZ, centerX, centerZ, totalDistance)
                    || player.getChunkTrackingView().contains(chunkX, chunkZ)) {
                continue;
            }

            int result = tryQueueCandidate(world, player, state, centerX, centerZ, chunkX, chunkZ);
            if (result < 0) {
                planner.enqueueCandidate(state, packed);
                break;
            }
            accepted += result;
        }

        totalCandidateInspections += inspected;
    }

    private int tryQueueCandidate(
            ServerLevel world,
            ServerPlayer player,
            PlayerViewState state,
            int centerX,
            int centerZ,
            int chunkX,
            int chunkZ) {
        long packed = ChunkPos.pack(chunkX, chunkZ);
        if (!canSend(player, state)) return -1;
        if (state.retryDueByChunk.getOrDefault(packed, Integer.MIN_VALUE) > ticks) return 0;
        int desiredLod = resolveLodLevel(centerX, centerZ, chunkX, chunkZ);

        int sentLod = state.sentLodByChunk.getOrDefault(packed, Integer.MAX_VALUE);
        if (state.sent.contains(packed) && desiredLod >= sentLod) {
            return 0;
        }
        int pendingLod = state.pendingLodByChunk.getOrDefault(packed, Integer.MAX_VALUE);
        if (state.pending.contains(packed) && desiredLod >= pendingLod) {
            return 0;
        }
        if (state.pending.contains(packed)) {
            state.clearPending(packed);
        }

        // A currently loaded chunk is authoritative and bypasses disk cache data.
        LevelChunk loaded = world.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded != null) {
            try {
                sendLoadedChunk(player, loaded);
                state.markSent(packed, 0);
                invalidatePreparedCacheForChunk(state.worldKey, packed);
                return 1;
            } catch (RuntimeException exception) {
                ViewExtendMod.LOGGER.debug("Failed to send loaded visual chunk {}", loaded.getPos(), exception);
                planner.scheduleRetry(state, packed, 200);
                return 0;
            }
        }

        GlobalChunkKey key = new GlobalChunkKey(state.worldKey, packed, desiredLod);
        PreparedVisualChunk cached = packetCache.get(key, ticks);
        if (cached != null) {
            totalCacheHits++;

            try {
                sendPreparedChunk(player, cached);
                state.markSent(packed, desiredLod);
                return 1;
            } catch (RuntimeException exception) {
                packetCache.invalidate(key);
                ViewExtendMod.LOGGER.debug("Failed to send cached visual chunk {}", new ChunkPos(chunkX, chunkZ), exception);
                planner.scheduleRetry(state, packed, 200);
                return 0;
            }
        }
        totalCacheMisses++;

        int cooldown = retryCache.remaining(new ChunkSourceKey(state.worldKey, packed), ticks);
        if (cooldown > 0) {
            totalCooldownHits++;
            planner.scheduleRetry(state, packed, cooldown);
            return 0;
        }


        if (state.pending.size() >= state.lookahead) return -1;
        SharedLoad existingLoad = loadsByKey.get(key);
        if (existingLoad != null) {
            long requestId = state.markPending(packed, desiredLod);
            existingLoad.subscribers.add(new LoadSubscriber(player.getUUID(), state, requestId));
            totalCoalescedRequests++;

            return 1;
        }

        if (isPipelineAtHardLimit()) {
            totalPipelineDeferrals++;

            return -1;
        }

        long requestId = state.markPending(packed, desiredLod);
        SharedLoad load = new SharedLoad(key, ticks);
        load.subscribers.add(new LoadSubscriber(player.getUUID(), state, requestId));
        loadsByKey.put(key, load);
        nbtReadQueue.addLast(key);
        totalNbtRequestsQueued++;

        return 1;
    }

    private void processNbtReadRequests(MinecraftServer server, int budget) {
        int started = 0;
        while (started < Math.max(1, budget) && hasWorkTime()) {
            GlobalChunkKey key = nbtReadQueue.pollFirst();
            if (key == null) {
                return;
            }
            SharedLoad load = loadsByKey.get(key);
            if (load == null || load.started) {
                continue;
            }

            ValidSubscriber firstValid = pruneAndFindFirstValidSubscriber(server, load);
            if (firstValid == null) {
                loadsByKey.remove(key);
                continue;
            }

            ServerLevel world = firstValid.world();
            ChunkPos pos = ChunkPos.unpack(key.chunkLong());
            LevelChunk loaded = world.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (loaded != null) {
                loadsByKey.remove(key);
                deliverLoadedChunkToSubscribers(server, load, loaded);
                started++;
                continue;
            }

            load.started = true;
            started++;
            totalNbtRequestsStarted++;

            totalDiskNbtReads++;

            LevelLightEngine lightEngine = world.getChunkSource().getLightEngine();
            LoadContext context = new LoadContext(
                    world.registryAccess(),
                    LevelHeightAccessor.create(world.getMinY(), world.getHeight()),
                    world.palettedContainerFactory(),
                    lightEngine.getMinLightSection(),
                    lightEngine.getMaxLightSection(),
                    world.dimensionType().hasSkyLight());

            try {
                loader.load(world.getChunkSource().chunkMap.read(pos), pos, key.lodLevel(), context)
                        .thenAccept(result -> publishCompletion(new PreparedLoadResult(
                                key, result.prepared(), result.missing(), result.error(), result.workerNanos())));
            } catch (RuntimeException exception) {
                publishCompletion(PreparedLoadResult.failed(key, exception));
            }
        }
    }

    private void publishCompletion(PreparedLoadResult result) {
        if (shuttingDown) {
            return;
        }
        completedLoadQueue.add(result);
        completedTasksQueuedWindow.increment();
    }

    private void transferCompletedLoads(MinecraftServer server, int budget) {
        for (int index = 0; index < budget; index++) {
            PreparedLoadResult result = completedLoadQueue.poll();
            if (result == null) {
                return;
            }
            SharedLoad load = loadsByKey.remove(result.key());
            if (load == null) {
                continue;
            }

            adaptive.completed(ticks - load.createdTick);
            totalPreparedResultsProcessed++;

            totalWorkerCpuNanos += result.workerNanos();

            if (result.missing() || result.error() != null || result.prepared() == null) {
                VisualChunkFailure failure = VisualChunkFailure.classify(result.missing(), result.error());
                if (result.missing()) totalDiskNbtReadMisses++;
                else totalPreparedResultsDropped++;
                failureCounts.merge(failure, 1L, Long::sum);
                Throwable cause = VisualChunkFailure.unwrap(result.error());
                Object failureIdentity = failure == VisualChunkFailure.UNFINISHED && cause != null ? cause.getMessage() : failure;
                int retryTicks = retryCache.fail(new ChunkSourceKey(result.key().worldKey(), result.key().chunkLong()),
                        ticks, failureIdentity, failure.retryTicks);
                if (ticks >= nextFailureLog.getOrDefault(failure, 0)) {
                    nextFailureLog.put(failure, ticks + 1200);
                    if (failure == VisualChunkFailure.MISSING || failure == VisualChunkFailure.UNFINISHED) {
                        ViewExtendMod.LOGGER.info("[ViewExtend Preparation] reason={} world={} chunk={} lod={} retryTicks={} detail={}",
                                failure, result.key().worldKey(), ChunkPos.unpack(result.key().chunkLong()),
                                result.key().lodLevel(), retryTicks, cause == null ? "No saved chunk" : cause.getMessage());
                    } else {
                        ViewExtendMod.LOGGER.warn("[ViewExtend Preparation] reason={} world={} chunk={} lod={} retryTicks={} (one example per reason/minute)",
                                failure, result.key().worldKey(), ChunkPos.unpack(result.key().chunkLong()),
                                result.key().lodLevel(), retryTicks, cause);
                    }
                }
                for (LoadSubscriber subscriber : load.subscribers) {
                    clearPendingAndScheduleRetry(server, subscriber, result.key(), retryTicks);
                }
                continue;
            }

            retryCache.invalidate(new ChunkSourceKey(result.key().worldKey(), result.key().chunkLong()));
            PreparedVisualChunk prepared = result.prepared();
            recordPreparationStats(prepared);
            packetCache.put(result.key(), prepared, ticks, load.subscribers.size() > 1);
            readyDeliveries.addLast(new ReadyDelivery(result.key(), prepared, load.subscribers));
        }
    }

    private void processReadyDeliveries(MinecraftServer server, int budget) {
        int processed = 0;
        int inspected = 0;
        int scanLimit = readyDeliveries.size() + budget;
        while (processed < Math.max(1, budget) && inspected++ < scanLimit && hasWorkTime()) {
            ReadyDelivery delivery = readyDeliveries.pollFirst();
            if (delivery == null) {
                return;
            }
            LoadSubscriber subscriber = delivery.nextSubscriber();
            if (subscriber == null) {
                continue;
            }

            ValidSubscriber valid = resolveValidSubscriber(server, subscriber, delivery.key());
            if (valid != null && !canSend(valid.player(), valid.state())) {
                delivery.defer(subscriber);
                readyDeliveries.addLast(delivery);
                continue;
            }
            if (valid != null) {
                long deliveryStarted = System.nanoTime();
                try {
                    ChunkPos pos = ChunkPos.unpack(delivery.key().chunkLong());
                    LevelChunk live = valid.world().getChunkSource().getChunkNow(pos.x(), pos.z());
                    if (live == null) {
                        sendPreparedChunk(valid.player(), delivery.prepared());
                        valid.state().markSent(delivery.key().chunkLong(), delivery.key().lodLevel());
                    } else {
                        sendLoadedChunk(valid.player(), live);
                        valid.state().markSent(delivery.key().chunkLong(), 0);
                        invalidatePreparedCacheForChunk(delivery.key().worldKey(), delivery.key().chunkLong());
                    }
                } catch (RuntimeException exception) {
                    valid.state().clearPending(delivery.key().chunkLong());
                    planner.scheduleRetry(valid.state(), delivery.key().chunkLong(), 200);
                    ViewExtendMod.LOGGER.debug(
                            "Failed to deliver prepared visual chunk {}",
                            ChunkPos.unpack(delivery.key().chunkLong()),
                            exception);
                } finally {
                    valid.state().share.charge(0, System.nanoTime() - deliveryStarted);
                }
            }
            processed++;

            if (delivery.hasSubscribers()) {
                readyDeliveries.addLast(delivery);
            }
        }
    }

    private void deliverLoadedChunkToSubscribers(MinecraftServer server, SharedLoad load, LevelChunk loaded) {
        for (LoadSubscriber subscriber : load.subscribers) {
            ValidSubscriber valid = resolveValidSubscriber(server, subscriber, load.key);
            if (valid == null) {
                continue;
            }
            if (!canSend(valid.player(), valid.state())) {
                valid.state().clearPending(load.key.chunkLong());
                planner.scheduleRetry(valid.state(), load.key.chunkLong(), 1);
                continue;
            }
            try {
                sendLoadedChunk(valid.player(), loaded);
                valid.state().markSent(load.key.chunkLong(), 0);
            } catch (RuntimeException exception) {
                valid.state().clearPending(load.key.chunkLong());
                planner.scheduleRetry(valid.state(), load.key.chunkLong(), 200);
            }
        }
        invalidatePreparedCacheForChunk(load.key.worldKey(), load.key.chunkLong());
    }

    private ValidSubscriber pruneAndFindFirstValidSubscriber(MinecraftServer server, SharedLoad load) {
        ValidSubscriber first = null;
        Iterator<LoadSubscriber> iterator = load.subscribers.iterator();
        while (iterator.hasNext()) {
            LoadSubscriber subscriber = iterator.next();
            ValidSubscriber valid = resolveValidSubscriber(server, subscriber, load.key);
            if (valid == null) {
                iterator.remove();
            } else if (first == null) {
                first = valid;
            }
        }
        return first;
    }

    private ValidSubscriber resolveValidSubscriber(
            MinecraftServer server,
            LoadSubscriber subscriber,
            GlobalChunkKey key) {
        ServerPlayer player = server.getPlayerList().getPlayer(subscriber.playerUuid());
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(subscriber.playerUuid(), key.worldKey()));
        if (player == null || state == null || !state.isCurrentRequest(subscriber.state(), key.chunkLong(), subscriber.requestId())) {
            return null;
        }

        ServerLevel world = player.level();
        long packed = key.chunkLong();
        if (!world.dimension().identifier().toString().equals(key.worldKey())) {
            state.clearPending(packed);
            return null;
        }
        if (!state.pending.contains(packed)
                || state.pendingLodByChunk.getOrDefault(packed, Integer.MAX_VALUE) != key.lodLevel()) {
            return null;
        }

        int normalDistance = world.getServer().getPlayerList().getViewDistance();
        int totalDistance = getEffectiveTotalDistance(player, normalDistance);
        int centerX = player.chunkPosition().x();
        int centerZ = player.chunkPosition().z();
        int chunkX = ChunkPos.getX(packed);
        int chunkZ = ChunkPos.getZ(packed);
        if (!withinDistance(chunkX, chunkZ, centerX, centerZ, totalDistance)
                || player.getChunkTrackingView().contains(chunkX, chunkZ)) {
            state.clearPending(packed);
            return null;
        }
        // A full-detail result is valid at any distance. A result too coarse for the new
        // view must explicitly schedule its replacement, including when the player stops.
        if (resolveLodLevel(centerX, centerZ, chunkX, chunkZ) < key.lodLevel()) {
            state.clearPending(packed);
            planner.enqueueCandidate(state, packed);
            return null;
        }
        return new ValidSubscriber(player, world, state);
    }

    private void clearPendingAndScheduleRetry(
            MinecraftServer server,
            LoadSubscriber subscriber,
            GlobalChunkKey key,
            int retryTicks) {
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(subscriber.playerUuid(), key.worldKey()));
        if (state == null || !state.isCurrentRequest(subscriber.state(), key.chunkLong(), subscriber.requestId())) {
            return;
        }
        state.clearPending(key.chunkLong());
        ServerPlayer player = server.getPlayerList().getPlayer(subscriber.playerUuid());
        if (player != null && player.isAlive()
                && player.level().dimension().identifier().toString().equals(key.worldKey())) {
            planner.scheduleRetry(state, key.chunkLong(), retryTicks);
        }
    }

    private void sendLoadedChunk(ServerPlayer player, LevelChunk chunk) {
        LevelLightEngine lightEngine = ((ServerLevel) chunk.getLevel()).getChunkSource().getLightEngine();
        ClientboundLevelChunkWithLightPacket packet = createChunkPacket(player, chunk, lightEngine, null, null);
        sendChunkPacket(player, packet);
    }

    private void sendPreparedChunk(ServerPlayer player, PreparedVisualChunk prepared) {
        sendChunkPacket(player, prepared.packet());
    }

    private static ClientboundLevelChunkWithLightPacket createChunkPacket(
            ServerPlayer player,
            LevelChunk chunk,
            LevelLightEngine lightEngine,
            BitSet skyLightMask,
            BitSet blockLightMask) {
        if (!(player.connection instanceof PacketContextProvider contextProvider)) {
            throw new IllegalStateException("Server connection does not provide a Fabric packet context");
        }
        return PacketContext.supplyWithContext(
                contextProvider,
                () -> new ClientboundLevelChunkWithLightPacket(
                        chunk, lightEngine, skyLightMask, blockLightMask));
    }

    public boolean isEnabled() { return config.unsimulatedViewDistance() > 0; }

    private boolean canSend(ServerPlayer player, PlayerViewState state) {
        var connection = ((com.silver.viewextend.mixin.ServerConnectionAccessor) player.connection).viewextend$getConnection();
        var channel = ((com.silver.viewextend.mixin.ConnectionAccessor) connection).viewextend$getChannel();
        var flow = (com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender;
        return hasWorkTime() && (borrowing || state.share.available())
                && sendsThisTick < config.maxMainThreadPreparedChunksPerTick()
                && VisualChunkFlow.canSend(flow.viewextend$getQuota(), flow.viewextend$getOutstanding(),
                        flow.viewextend$getMaxOutstanding(), state.visualBatchOpen)
                && state.sendsThisTick < config.maxChunksPerPlayerPerTick() && connection.isConnected()
                && channel != null && channel.isWritable();
    }

    private void sendChunkPacket(ServerPlayer player, ClientboundLevelChunkWithLightPacket packet) {
        String worldKey = player.level().dimension().identifier().toString();
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(player.getUUID(), worldKey));
        if (state == null) throw new IllegalStateException("Missing visual batch state");
        var flow = (com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender;
        if (!state.visualBatchOpen) {
            flow.viewextend$setOutstanding(flow.viewextend$getOutstanding() + 1);
            state.visualBatchOpen = true;
            player.connection.send(net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket.INSTANCE);
        }
        flow.viewextend$setQuota(flow.viewextend$getQuota() - 1);
        int packetBytes = VisualChunkPackets.estimate(packet);
        sendingVisualPacket = true;
        try {
            player.connection.send(packet);
        } finally {
            sendingVisualPacket = false;
        }
        state.share.charge(packetBytes, 0);
        typicalPacketBytes += .02 * (packetBytes - typicalPacketBytes);
        state.sendsThisTick++;
        state.refreshEntities = true;
        sendsThisTick++;
        totalChunkPacketsSent++;

        totalNetworkBytesEstimate += packetBytes;
    }

    private void finishVisualBatch(ServerPlayer player) {
        var key = new PlayerWorldKey(player.getUUID(), player.level().dimension().identifier().toString());
        PlayerViewState state = statesByPlayer.get(key);
        if (state != null && state.visualBatchOpen) {
            state.visualBatchOpen = false;
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket(state.sendsThisTick));
        }
    }

    public void onChunkAvailable(ServerLevel world, LevelChunk chunk) {
        String worldKey = world.dimension().identifier().toString();
        long packed = chunk.getPos().pack();
        invalidatePreparedCacheForChunk(worldKey, packed);
        for (PlayerViewState state : statesByPlayer.values()) {
            if (state.worldKey.equals(worldKey) && state.retryDueByChunk.remove(packed) != Integer.MIN_VALUE) {
                planner.enqueueCandidate(state, packed);
            }
        }
    }

    private void invalidatePreparedCacheForChunk(String worldKey, long packed) {
        retryCache.invalidate(new ChunkSourceKey(worldKey, packed));
        packetCache.invalidate(new GlobalChunkKey(worldKey, packed, 0));
        packetCache.invalidate(new GlobalChunkKey(worldKey, packed, 1));
    }

    private void recordPreparationStats(PreparedVisualChunk prepared) {
        TransformStats transform = prepared.transformStats();
        totalLegacyBlockIdRemaps += transform.remapCount();

        if (transform.unsortedSections()) {
            totalUnsortedSectionInputs++;
        }

        LightStats light = prepared.lightStats();
        totalSkyLightNbtSections += light.skyNbt();
        totalSkyLightFallbackSections += light.skyFallback();
        totalSkyLightWaterFallbackSections += light.skyWaterFallback();
        totalBlockLightNbtSections += light.blockNbt();
        totalBlockLightFallbackSections += light.blockFallback();
    }

    private void processDueUnloads(
            ServerPlayer player,
            PlayerViewState state,
            int centerX,
            int centerZ,
            int totalDistance) {
        int unloadDistance = totalDistance + config.unloadBufferChunks();
        int sent = 0;
        while (sent < config.maxUnloadsPerPlayerPerTick()
                && !state.unloadQueue.isEmpty()
                && state.unloadQueue.peek().dueTick() <= ticks) {
            ScheduledChunk scheduled = state.unloadQueue.poll();
            long packed = scheduled.chunkLong();
            if (state.unloadDueByChunk.getOrDefault(packed, Integer.MIN_VALUE) != scheduled.dueTick()) {
                continue;
            }
            state.unloadDueByChunk.remove(packed);
            if (!state.sent.contains(packed)) {
                continue;
            }

            int x = ChunkPos.getX(packed);
            int z = ChunkPos.getZ(packed);
            if (withinDistance(x, z, centerX, centerZ, unloadDistance)) {
                continue;
            }
            player.connection.send(new ClientboundForgetLevelChunkPacket(new ChunkPos(x, z)));
            state.sent.remove(packed);
            state.sentLodByChunk.remove(packed);
            state.clearPending(packed);
            totalUnloadPacketsSent++;

            totalNetworkBytesEstimate += 9;

            sent++;
        }
        planner.compactScheduledQueueIfNeeded(state.unloadQueue, state.unloadDueByChunk);
    }

    private boolean isPipelineAtHardLimit() {
        int hardLimit = config.preparedQueueHardLimit();
        return hardLimit > 0 && loadsByKey.size() + readyDeliveries.size() >= hardLimit;
    }

    private int resolveLodLevel(int centerX, int centerZ, int chunkX, int chunkZ) {
        int distance = Math.max(Math.abs(chunkX - centerX), Math.abs(chunkZ - centerZ));
        return distance >= config.lod1StartDistance() ? 1 : 0;
    }

    private void refreshPlayerEntities(MinecraftServer server) {
        // Refresh only supported live entities; never scan every mob for each terrain delivery.
        var byWorld = new java.util.HashMap<ServerLevel, java.util.List<ServerPlayer>>();
        for (PlayerViewState state : statesByPlayer.values()) {
            if (!state.refreshEntities) continue;
            state.refreshEntities = false;
            ServerPlayer observer = server.getPlayerList().getPlayer(state.playerUuid);
            if (observer != null) byWorld.computeIfAbsent(observer.level(), ignored -> new ArrayList<>()).add(observer);
        }
        byWorld.forEach((world, observers) -> {
            var trackers = ((com.silver.viewextend.mixin.ServerChunkLoadingManagerAccessor)
                    world.getChunkSource().chunkMap).viewextend$getEntityMap();
            for (ServerPlayer target : world.players()) refreshTracker(trackers.get(target.getId()), observers);
            var dragons = loadedDragons.get(world.dimension().identifier().toString());
            if (dragons != null) for (int id : dragons) refreshTracker(trackers.get(id), observers);
        });
    }

    private static void refreshTracker(Object tracker, java.util.List<ServerPlayer> observers) {
        if (tracker instanceof ExtendedPlayerTracker extended && extended.viewextend$eligible()) {
            for (ServerPlayer observer : observers) extended.viewextend$refresh(observer);
        }
    }

    public void resetPlayer(ServerPlayer player) {
        statesByPlayer.keySet().removeIf(key -> key.playerUuid().equals(player.getUUID()));
    }

    public void onVanillaChunkSent(ServerPlayer player, int x, int z) {
        if (sendingVisualPacket) return;
        String worldKey = player.level().dimension().identifier().toString();
        PlayerViewState state = statesByPlayer.computeIfAbsent(
                new PlayerWorldKey(player.getUUID(), worldKey),
                ignored -> new PlayerViewState(player.getUUID(), worldKey));
        state.markSent(ChunkPos.pack(x, z), 0);
        state.refreshEntities = true;
        invalidatePreparedCacheForChunk(worldKey, ChunkPos.pack(x, z));
    }

    public boolean shouldSuppressVanillaUnload(ServerPlayer player, ChunkPos pos) {
        if (config.unsimulatedViewDistance() == 0) return false;
        int totalDistance = getRequiredClientLoadDistance(player);
        if (!withinDistance(pos.x(), pos.z(), player.chunkPosition().x(), player.chunkPosition().z(), totalDistance)) {
            return false;
        }
        String worldKey = player.level().dimension().identifier().toString();
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(player.getUUID(), worldKey));
        if (state == null || !state.sent.contains(pos.pack())) return false;
        totalSuppressedVanillaUnloads++;

        return true;
    }

    public void onLikelyClientUnload(ServerPlayer player, ChunkPos pos) {
        String worldKey = player.level().dimension().identifier().toString();
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(player.getUUID(), worldKey));
        if (state == null) {
            return;
        }
        long packed = pos.pack();
        state.sent.remove(packed);
        state.sentLodByChunk.remove(packed);
        state.clearPending(packed);
        state.unloadDueByChunk.remove(packed);
        if (withinDistance(pos.x(), pos.z(), player.chunkPosition().x(), player.chunkPosition().z(), getRequiredClientLoadDistance(player))) {
            planner.enqueueCandidate(state, packed);
        }
    }

    public boolean hasSentVisualChunk(ServerPlayer player, ChunkPos pos) {
        String worldKey = player.level().dimension().identifier().toString();
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(player.getUUID(), worldKey));
        return state != null && state.sent.contains(pos.pack());
    }

    public int getEffectiveTotalDistance(ServerPlayer player, int normalDistance) {
        return ViewDistancePolicy.effective(normalDistance, config.unsimulatedViewDistance(),
                player.requestedViewDistance(), config.clientReportedViewDistanceHardCap());
    }

    public int getRequiredClientLoadDistance(ServerPlayer player) {
        int normalDistance = player.level().getServer().getPlayerList().getViewDistance();
        return getEffectiveTotalDistance(player, normalDistance);
    }

    private void updateClientLoadDistance(ServerPlayer player, PlayerViewState state, int totalDistance) {
        if (state.clientChunkLoadDistance == totalDistance) {
            return;
        }
        player.connection.send(new ClientboundSetChunkCacheRadiusPacket(totalDistance));
        state.clientChunkLoadDistance = totalDistance;
        totalChunkLoadDistancePackets++;

        totalNetworkBytesEstimate += 5;
    }

    private void cleanupDisconnected(MinecraftServer server) {
        Iterator<Map.Entry<PlayerWorldKey, PlayerViewState>> iterator = statesByPlayer.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<PlayerWorldKey, PlayerViewState> entry = iterator.next();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey().playerUuid());
            if (player == null
                    || !entry.getKey().worldKey().equals(
                            player.level().dimension().identifier().toString())) {
                iterator.remove();
            }
        }
    }

    private static List<PlayerTickTarget> collectTickTargets(MinecraftServer server) {
        List<PlayerTickTarget> targets = new ArrayList<>();
        for (ServerLevel world : server.getAllLevels()) {
            for (ServerPlayer player : world.players()) {
                targets.add(new PlayerTickTarget(world, player));
            }
        }
        return targets;
    }

    private void maybeLogMetrics(MinecraftServer server) {
        if (!config.metricsInfoLogsEnabled() || ticks % config.metricsLogIntervalTicks() != 0) {
            return;
        }

        long now = System.nanoTime();
        double seconds = Math.max(1L, now - metricsWindowStartNanos) / 1_000_000_000.0;
        long pending = 0;
        long sent = 0;
        long candidateQueue = 0;
        for (PlayerViewState state : statesByPlayer.values()) {
            pending += state.pending.size();
            sent += state.sent.size();
            candidateQueue += state.candidateQueue.size();
        }
        long completedQueued = completedTasksQueuedWindow.sumThenReset();

        ViewExtendMod.LOGGER.info(
                "[ViewExtend Metrics] players={} states={} sent={} pending={} candidates={} loads={} nbtQueue={} completedQueue={} deliveries={} cache={}/{}MiB packets={} unloads={} diskReads={} diskMisses={} nbtQueued={} nbtStarted={} coalesced={} cacheHits={} cacheMisses={} pipelineDeferrals={} candidateChecks={} preparedQueued={} preparedProcessed={} preparedDropped={} remaps={} unsorted={} suppressedUnloads={} lightSky(nbt/fallback/water)={}/{}/{} lightBlock(nbt/fallback)={}/{} netMiB~={} netMiBps~={} mainElapsedMsps={} workerElapsedMsps={}",
                server.getPlayerList().getPlayers().size(), statesByPlayer.size(), sent, pending,
                candidateQueue, loadsByKey.size(), nbtReadQueue.size(), completedLoadQueue.size(),
                readyDeliveries.size(), packetCache.size(),
                packetCache.bytes() / (1024L * 1024L), totalChunkPacketsSent,
                totalUnloadPacketsSent, totalDiskNbtReads, totalDiskNbtReadMisses,
                totalNbtRequestsQueued, totalNbtRequestsStarted, totalCoalescedRequests,
                totalCacheHits, totalCacheMisses, totalPipelineDeferrals, totalCandidateInspections,
                completedQueued, totalPreparedResultsProcessed, totalPreparedResultsDropped,
                totalLegacyBlockIdRemaps, totalUnsortedSectionInputs, totalSuppressedVanillaUnloads,
                totalSkyLightNbtSections, totalSkyLightFallbackSections,
                totalSkyLightWaterFallbackSections, totalBlockLightNbtSections,
                totalBlockLightFallbackSections,
                String.format(Locale.ROOT, "%.2f", totalNetworkBytesEstimate / 1048576.0),
                String.format(Locale.ROOT, "%.2f", totalNetworkBytesEstimate / 1048576.0 / seconds),
                String.format(Locale.ROOT, "%.2f", totalMainCpuNanos / 1_000_000.0 / seconds),
                String.format(Locale.ROOT, "%.2f", totalWorkerCpuNanos / 1_000_000.0 / seconds));

        ViewExtendMod.LOGGER.info("[ViewExtend Preparation] outcomes={} sharedCooldownHits={}", failureCounts, totalCooldownHits);
        for (PlayerViewState state : statesByPlayer.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerUuid);
            if (player == null) continue;
            ViewExtendMod.LOGGER.info("[ViewExtend View] player={} world={} requested={} effective={} server={} sent={} pending={} retries={} bootstrapActive={} bootstrapRadius={} batchQuota={} unackedBatches={} lookahead={}",
                    player.getGameProfile().name(), state.worldKey, player.requestedViewDistance(), state.totalDistance,
                    state.normalDistance, state.sent.size(), state.pending.size(), state.retryDueByChunk.size(),
                    state.bootstrapActive, state.bootstrapRadius,
                    ((com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender).viewextend$getQuota(),
                    ((com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender).viewextend$getOutstanding(), state.lookahead);
        }
        ViewExtendMod.LOGGER.info("[ViewExtend Scheduler] readsPerTick={} budgetMs={} preparationLatencyTicks={} cachePromotions={}",
                adaptive.reads(), adaptive.allowance() / 1_000_000.0, adaptive.latencyTicks(), packetCache.promotions());
        failureCounts.clear();
        totalCooldownHits = 0;
        metricsWindowStartNanos = now;
        totalMainCpuNanos = 0;
        totalWorkerCpuNanos = 0;
        totalChunkPacketsSent = 0;
        totalUnloadPacketsSent = 0;
        totalDiskNbtReads = 0;
        totalDiskNbtReadMisses = 0;
        totalNetworkBytesEstimate = 0;
        totalChunkLoadDistancePackets = 0;
        totalLegacyBlockIdRemaps = 0;
        totalUnsortedSectionInputs = 0;
        totalNbtRequestsQueued = 0;
        totalNbtRequestsStarted = 0;
        totalCoalescedRequests = 0;
        totalPreparedResultsProcessed = 0;
        totalPreparedResultsDropped = 0;
        totalPipelineDeferrals = 0;
        totalCacheHits = 0;
        totalCacheMisses = 0;
        totalCandidateInspections = 0;
        totalSuppressedVanillaUnloads = 0;
        totalSkyLightNbtSections = 0;
        totalSkyLightFallbackSections = 0;
        totalSkyLightWaterFallbackSections = 0;
        totalBlockLightNbtSections = 0;
        totalBlockLightFallbackSections = 0;
    }

    public void shutdown() {
        shuttingDown = true;
        loader.close();
        nbtReadQueue.clear();
        loadsByKey.clear();
        completedLoadQueue.clear();
        readyDeliveries.clear();
        packetCache.clear();
        retryCache.clear();
        loadedDragons.clear();
        statesByPlayer.clear();
    }

    private record PlayerTickTarget(ServerLevel world, ServerPlayer player) {
    }

    private record PlayerWorldKey(UUID playerUuid, String worldKey) {
    }

    private record ChunkSourceKey(String worldKey, long chunkLong) {}

    record GlobalChunkKey(String worldKey, long chunkLong, int lodLevel) {
    }

    record LoadSubscriber(UUID playerUuid, PlayerViewState state, long requestId) {
    }

    private record ValidSubscriber(ServerPlayer player, ServerLevel world, PlayerViewState state) {
    }

    private record PreparedLoadResult(
            GlobalChunkKey key,
            PreparedVisualChunk prepared,
            boolean missing,
            Throwable error,
            long workerNanos) {
        private static PreparedLoadResult failed(GlobalChunkKey key, Throwable error) {
            return new PreparedLoadResult(key, null, false, error, 0L);
        }
    }

    private static final class SharedLoad {
        private final GlobalChunkKey key;
        private final List<LoadSubscriber> subscribers = new ArrayList<>();
        private boolean started;
        private final int createdTick;

        private SharedLoad(GlobalChunkKey key, int createdTick) {
            this.key = key;
            this.createdTick = createdTick;
        }
    }

    static final class ReadyDelivery {
        private final GlobalChunkKey key;
        private final PreparedVisualChunk prepared;
        private final ArrayDeque<LoadSubscriber> subscribers;

        ReadyDelivery(GlobalChunkKey key, PreparedVisualChunk prepared, List<LoadSubscriber> subscribers) {
            this.key = key;
            this.prepared = prepared;
            this.subscribers = new ArrayDeque<>(subscribers);
        }

        GlobalChunkKey key() { return key; }
        PreparedVisualChunk prepared() { return prepared; }
        LoadSubscriber nextSubscriber() { return subscribers.pollFirst(); }
        void defer(LoadSubscriber subscriber) { subscribers.addLast(subscriber); }
        boolean hasSubscribers() { return !subscribers.isEmpty(); }
    }
}
