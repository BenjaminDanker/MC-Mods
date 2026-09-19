package com.silver.viewextend;

import com.silver.viewextend.VisualChunkPreparer.*;
import com.silver.viewextend.PlayerViewState.ScheduledChunk;
import static com.silver.viewextend.ViewExtendGeometry.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
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
    private final ViewExtendNetworkMetrics networkMetrics = new ViewExtendNetworkMetrics();

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
    private long totalPreparationFailures;
    private long totalPreparedOrphaned;
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
    private final DistanceRange admittedDistances = new DistanceRange();
    private final DistanceRange sentDistances = new DistanceRange();
    private long totalPriorityInversions;
    private long totalPriorityInversionsAvoided;
    private long totalStaleCandidatesSkipped;
    private long totalSupersededSubscribers;
    private long totalSkippedUnstartedLoads;
    private long totalPreparationsSkippedNoSubscribers;
    private long totalOrphanRawNbtCount;
    private long totalOrphanRawNbtBytes;
    private long totalPreparedDiscardedBytes;
    private long totalPreparedDiscardedNoCache;
    private long currentReadyBytes;
    private long peakReadyBytes;
    private long totalReadyWaitTicks;
    private long totalReadyWaitSamples;
    private long peakReadyWaitTicks;
    private long readyFairnessSequence;
    private final long[] demandYielded = new long[PlayerViewPlanner.DemandSource.values().length];
    private final long[] demandAccepted = new long[PlayerViewPlanner.DemandSource.values().length];
    private final long[] demandSkipped = new long[PlayerViewPlanner.DemandSource.values().length];
    private final long[] candidateSkipReasons = new long[CandidateSkipReason.values().length];

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
                    .filter(state -> state.bootstrapActive || state.nearActive
                            || !state.pending.isEmpty() || state.demandDescriptorCount() > 0).count());
            for (PlayerViewState state : statesByPlayer.values()) {
                state.share.accrue((long) (typicalPacketBytes * config.maxMainThreadPreparedChunksPerTick() / active),
                        adaptive.allowance() / active);
            }
            int preparedBudget = config.maxMainThreadPreparedChunksPerTick();
            transferCompletedLoads(server, Math.max(256, preparedBudget));
            processReadyDeliveries(server, preparedBudget);
            processNbtReadRequests(server, adaptive.reads());
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
                        || state.demandDescriptorCount() == 0
                        || !canAdmit(player, state)) continue;
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
            PlayerViewPlanner.DemandCandidate candidate = planner.peekBestCandidate(state);
            if (candidate == null) break;
            demandYielded[candidate.source().ordinal()]++;
            long packed = candidate.packed();

            inspected++;
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);
            if (!withinDistance(chunkX, chunkZ, centerX, centerZ, totalDistance)) {
                planner.consumeCandidate(state, candidate);
                recordCandidateSkip(candidate.source(), CandidateSkipReason.OUTSIDE_RADIUS);
                continue;
            }
            if (player.getChunkTrackingView().contains(chunkX, chunkZ)) {
                planner.consumeCandidate(state, candidate);
                recordCandidateSkip(candidate.source(), CandidateSkipReason.VANILLA_OWNED);
                continue;
            }

            CandidateResult result = tryQueueCandidate(world, player, state, candidate, chunkX, chunkZ);
            if (result == CandidateResult.BLOCKED) {
                break;
            }
            planner.consumeCandidate(state, candidate);
            if (result == CandidateResult.ACCEPTED) {
                accepted++;
                demandAccepted[candidate.source().ordinal()]++;
            } else {
                recordCandidateSkip(candidate.source(), result.skipReason);
            }
        }

        totalCandidateInspections += inspected;
    }

    private CandidateResult tryQueueCandidate(
            ServerLevel world,
            ServerPlayer player,
            PlayerViewState state,
            PlayerViewPlanner.DemandCandidate candidate,
            int chunkX,
            int chunkZ) {
        long packed = ChunkPos.pack(chunkX, chunkZ);
        if (!canAdmit(player, state)) return CandidateResult.BLOCKED;
        if (state.retryDueByChunk.getOrDefault(packed, Integer.MIN_VALUE) > ticks) {
            return CandidateResult.SKIPPED_RETRY_DEADLINE;
        }
        int desiredLod = resolveLodLevel(state.centerX, state.centerZ, chunkX, chunkZ);

        int sentLod = state.sentLodByChunk.getOrDefault(packed, Integer.MAX_VALUE);
        if (state.sent.contains(packed) && desiredLod >= sentLod) {
            return CandidateResult.SKIPPED_ALREADY_SENT;
        }
        int pendingLod = state.pendingLodByChunk.getOrDefault(packed, Integer.MAX_VALUE);
        if (state.pending.contains(packed) && desiredLod >= pendingLod) {
            return CandidateResult.SKIPPED_ALREADY_PENDING;
        }
        if (state.pending.contains(packed)) {
            state.clearPending(packed);
        }

        // A currently loaded chunk is authoritative and bypasses disk cache data.
        LevelChunk loaded = world.getChunkSource().getChunkNow(chunkX, chunkZ);
        if (loaded != null) {
            if (!canSend(player, state)) return CandidateResult.BLOCKED;
            try {
                sendLoadedChunk(player, loaded);
                state.markSent(packed, 0);
                invalidatePreparedCacheForChunk(state.worldKey, packed);
                return CandidateResult.ACCEPTED;
            } catch (RuntimeException exception) {
                ViewExtendMod.LOGGER.debug("Failed to send loaded visual chunk {}", loaded.getPos(), exception);
                planner.scheduleRetry(state, packed, 200);
                return CandidateResult.SKIPPED_DELIVERY_FAILURE;
            }
        }

        GlobalChunkKey key = new GlobalChunkKey(state.worldKey, packed, desiredLod);
        PreparedVisualChunk cached = packetCache.get(key, ticks);
        if (cached != null) {
            if (!canSend(player, state)) return CandidateResult.BLOCKED;
            totalCacheHits++;

            try {
                sendPreparedChunk(player, cached);
                state.markSent(packed, desiredLod);
                return CandidateResult.ACCEPTED;
            } catch (RuntimeException exception) {
                packetCache.invalidate(key);
                ViewExtendMod.LOGGER.debug("Failed to send cached visual chunk {}", new ChunkPos(chunkX, chunkZ), exception);
                planner.scheduleRetry(state, packed, 200);
                return CandidateResult.SKIPPED_DELIVERY_FAILURE;
            }
        }
        totalCacheMisses++;

        int cooldown = retryCache.remaining(new ChunkSourceKey(state.worldKey, packed), ticks);
        if (cooldown > 0) {
            totalCooldownHits++;
            planner.scheduleRetry(state, packed, cooldown);
            return CandidateResult.SKIPPED_SHARED_COOLDOWN;
        }

        if (state.pending.size() >= state.lookahead
                && !supersedeWorseSubscription(state, candidate)) return CandidateResult.BLOCKED;
        SharedLoad existingLoad = loadsByKey.get(key);
        if (existingLoad != null) {
            long requestId = state.markPending(packed, desiredLod);
            existingLoad.subscribers.add(new LoadSubscriber(player.getUUID(), state, requestId));
            totalCoalescedRequests++;
            admittedDistances.record(candidate.distance());
            return CandidateResult.ACCEPTED;
        }

        if (isPipelineAtHardLimit()) {
            totalPipelineDeferrals++;
            if (!supersedeWorseSubscription(state, candidate) || isPipelineAtHardLimit()) {
                return CandidateResult.BLOCKED;
            }
        }

        long requestId = state.markPending(packed, desiredLod);
        SharedLoad load = new SharedLoad(key, ticks);
        load.subscribers.add(new LoadSubscriber(player.getUUID(), state, requestId));
        loadsByKey.put(key, load);
        nbtReadQueue.addLast(key);
        totalNbtRequestsQueued++;
        admittedDistances.record(candidate.distance());
        return CandidateResult.ACCEPTED;
    }

    private boolean supersedeWorseSubscription(
            PlayerViewState state,
            PlayerViewPlanner.DemandCandidate incoming) {
        long worstPacked = Long.MIN_VALUE;
        int worstTier = -1;
        int worstDistance = -1;
        long worstRequestId = 0;
        var iterator = state.pending.iterator();
        while (iterator.hasNext()) {
            long packed = iterator.nextLong();
            int distance = Math.max(Math.abs(ChunkPos.getX(packed) - state.centerX),
                    Math.abs(ChunkPos.getZ(packed) - state.centerZ));
            int tier = PlayerViewPlanner.priorityTier(state.normalDistance, distance);
            boolean substantiallyWorse = shouldSupersede(
                    incoming.tier(), incoming.distance(), tier, distance);
            if (!substantiallyWorse) continue;
            if (worstPacked == Long.MIN_VALUE
                    || PlayerViewPlanner.comparePriority(tier, distance, 0,
                            worstTier, worstDistance, 0) > 0) {
                worstPacked = packed;
                worstTier = tier;
                worstDistance = distance;
                worstRequestId = state.pendingRequestIds.get(packed);
            }
        }
        if (worstPacked == Long.MIN_VALUE) return false;

        int lod = state.pendingLodByChunk.getOrDefault(worstPacked, 0);
        GlobalChunkKey key = new GlobalChunkKey(state.worldKey, worstPacked, lod);
        SharedLoad load = loadsByKey.get(key);
        if (load != null) {
            long requestId = worstRequestId;
            load.subscribers.removeIf(subscriber -> subscriber.state() == state
                    && subscriber.requestId() == requestId);
            if (load.subscribers.isEmpty() && !load.started) {
                loadsByKey.remove(key);
                totalSkippedUnstartedLoads++;
                totalPreparationsSkippedNoSubscribers++;
            }
        }
        long requestId = worstRequestId;
        readyDeliveries.removeIf(delivery -> {
            delivery.removeSubscriber(state, requestId);
            return !delivery.hasSubscribers();
        });
        reconcileReadyBytes();
        state.clearPending(worstPacked);
        totalSupersededSubscribers++;
        return true;
    }

    static boolean shouldSupersede(int incomingTier, int incomingDistance,
            int existingTier, int existingDistance) {
        return existingTier > incomingTier
                || (existingTier == incomingTier && existingDistance >= incomingDistance + 8);
    }

    private void processNbtReadRequests(MinecraftServer server, int budget) {
        List<QueuedLoadRank> ranked = new ArrayList<>(nbtReadQueue.size());
        while (!nbtReadQueue.isEmpty()) {
            GlobalChunkKey key = nbtReadQueue.pollFirst();
            SharedLoad load = loadsByKey.get(key);
            if (load == null || load.started) continue;
            QueuedLoadRank rank = rankQueuedLoad(server, load);
            if (rank == null) {
                loadsByKey.remove(key);
                totalSkippedUnstartedLoads++;
                totalPreparationsSkippedNoSubscribers++;
            } else {
                ranked.add(rank);
            }
        }
        ranked.sort((left, right) -> PlayerViewPlanner.comparePriority(
                left.tier(), left.distance(), left.load().createdTick,
                right.tier(), right.distance(), right.load().createdTick));

        int started = 0;
        int index = 0;
        while (index < ranked.size() && started < Math.max(1, budget) && hasWorkTime()) {
            QueuedLoadRank rank = ranked.get(index++);
            SharedLoad load = rank.load();
            GlobalChunkKey key = load.key;
            ValidSubscriber firstValid = rank.firstValid();

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
                                key, result.prepared(), result.missing(), result.error(),
                                result.workerNanos(), result.rawNbtBytes())));
            } catch (RuntimeException exception) {
                publishCompletion(PreparedLoadResult.failed(key, exception));
            }
        }
        while (index < ranked.size()) nbtReadQueue.addLast(ranked.get(index++).load().key);
    }

    private QueuedLoadRank rankQueuedLoad(MinecraftServer server, SharedLoad load) {
        ValidSubscriber first = null;
        int bestTier = Integer.MAX_VALUE;
        int bestDistance = Integer.MAX_VALUE;
        Iterator<LoadSubscriber> iterator = load.subscribers.iterator();
        while (iterator.hasNext()) {
            LoadSubscriber subscriber = iterator.next();
            ValidSubscriber valid = resolveValidSubscriber(server, subscriber, load.key);
            if (valid == null) {
                iterator.remove();
                continue;
            }
            ChunkPos pos = ChunkPos.unpack(load.key.chunkLong());
            int distance = Math.max(Math.abs(pos.x() - valid.state().centerX),
                    Math.abs(pos.z() - valid.state().centerZ));
            int tier = PlayerViewPlanner.priorityTier(valid.state().normalDistance, distance);
            if (first == null || PlayerViewPlanner.comparePriority(tier, distance, load.createdTick,
                    bestTier, bestDistance, load.createdTick) < 0) {
                first = valid;
                bestTier = tier;
                bestDistance = distance;
            }
        }
        return first == null ? null : new QueuedLoadRank(load, first, bestTier, bestDistance);
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
                recordOrphanResult(result);
                continue;
            }

            adaptive.completed(ticks - load.createdTick);
            totalPreparedResultsProcessed++;

            totalWorkerCpuNanos += result.workerNanos();

            // Validation is server-thread-only. The numeric NBT size travelled with the result;
            // no NBT object survives the worker task.
            pruneAndFindFirstValidSubscriber(server, load);
            if (load.subscribers.isEmpty() && result.rawNbtBytes() > 0) {
                totalOrphanRawNbtCount++;
                totalOrphanRawNbtBytes += result.rawNbtBytes();
            }

            if (result.missing() || result.error() != null || result.prepared() == null) {
                VisualChunkFailure failure = VisualChunkFailure.classify(result.missing(), result.error());
                if (result.missing()) totalDiskNbtReadMisses++;
                else totalPreparationFailures++;
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

            if (load.subscribers.isEmpty()) {
                totalPreparedOrphaned++;
                totalPreparedDiscardedNoCache++;
                totalPreparedDiscardedBytes += result.prepared().estimatedBytes();
                continue;
            }

            retryCache.invalidate(new ChunkSourceKey(result.key().worldKey(), result.key().chunkLong()));
            PreparedVisualChunk prepared = result.prepared();
            recordPreparationStats(prepared);
            packetCache.put(result.key(), prepared, ticks, load.subscribers.size() > 1);
            readyDeliveries.addLast(new ReadyDelivery(result.key(), prepared, load.subscribers, ticks));
            currentReadyBytes += prepared.estimatedBytes();
            peakReadyBytes = Math.max(peakReadyBytes, currentReadyBytes);
        }
    }

    private void recordOrphanResult(PreparedLoadResult result) {
        if (result.rawNbtBytes() > 0) {
            totalOrphanRawNbtCount++;
            totalOrphanRawNbtBytes += result.rawNbtBytes();
        }
        if (result.prepared() != null) {
            totalPreparedOrphaned++;
            totalPreparedDiscardedNoCache++;
            totalPreparedDiscardedBytes += result.prepared().estimatedBytes();
        }
    }

    private void processReadyDeliveries(MinecraftServer server, int budget) {
        ReadyPass pass = buildReadyPass(server);
        int processed = 0;
        while (processed < Math.max(1, budget) && hasWorkTime()) {
            ReadyChoice choice = chooseBestReady(pass);
            if (choice == null) break;
            PriorityQueue<ReadyChoice> playerQueue = pass.byPlayer().get(choice.valid().state());
            ReadyChoice rankedFirst = playerQueue.poll();
            if (rankedFirst != choice) totalPriorityInversions++;

            ReadyChoice fifoFirst = firstUnconsumed(pass.fifoByPlayer().get(choice.valid().state()),
                    pass.consumed());
            if (fifoFirst != null && fifoFirst != choice && compareReady(choice, fifoFirst) < 0) {
                totalPriorityInversionsAvoided++;
            }
            pass.consumed().add(choice);
            choice.delivery().removeSubscriber(choice.subscriber());
            if (!choice.delivery().hasSubscribers()) readyDeliveries.remove(choice.delivery());

            choice.valid().state().readyFairnessSequence = ++readyFairnessSequence;
            long readyWait = Math.max(0, ticks - choice.delivery().createdTick());
            totalReadyWaitTicks += readyWait;
            totalReadyWaitSamples++;
            peakReadyWaitTicks = Math.max(peakReadyWaitTicks, readyWait);
            long deliveryStarted = System.nanoTime();
            try {
                ChunkPos pos = ChunkPos.unpack(choice.delivery().key().chunkLong());
                LevelChunk live = choice.valid().world().getChunkSource().getChunkNow(pos.x(), pos.z());
                if (live == null) {
                    sendPreparedChunk(choice.valid().player(), choice.delivery().prepared());
                    choice.valid().state().markSent(choice.delivery().key().chunkLong(),
                            choice.delivery().key().lodLevel());
                } else {
                    sendLoadedChunk(choice.valid().player(), live);
                    choice.valid().state().markSent(choice.delivery().key().chunkLong(), 0);
                    invalidatePreparedCacheForChunk(choice.delivery().key().worldKey(),
                            choice.delivery().key().chunkLong());
                }
            } catch (RuntimeException exception) {
                choice.valid().state().clearPending(choice.delivery().key().chunkLong());
                planner.scheduleRetry(choice.valid().state(), choice.delivery().key().chunkLong(), 200);
                ViewExtendMod.LOGGER.debug("Failed to deliver prepared visual chunk {}",
                        ChunkPos.unpack(choice.delivery().key().chunkLong()), exception);
            } finally {
                choice.valid().state().share.charge(0, System.nanoTime() - deliveryStarted);
            }
            processed++;
        }
        readyDeliveries.removeIf(delivery -> !delivery.hasSubscribers());
        reconcileReadyBytes();
    }

    /** Builds one current-position priority snapshot for this bounded delivery pass. */
    private ReadyPass buildReadyPass(MinecraftServer server) {
        Map<PlayerViewState, PriorityQueue<ReadyChoice>> byPlayer = new HashMap<>();
        Map<PlayerViewState, ArrayDeque<ReadyChoice>> fifoByPlayer = new HashMap<>();
        for (ReadyDelivery delivery : readyDeliveries) {
            List<LoadSubscriber> stale = null;
            for (LoadSubscriber subscriber : delivery.subscribers()) {
                ValidSubscriber valid = resolveValidSubscriber(server, subscriber, delivery.key());
                if (valid == null) {
                    if (stale == null) stale = new ArrayList<>();
                    stale.add(subscriber);
                    continue;
                }
                ChunkPos pos = ChunkPos.unpack(delivery.key().chunkLong());
                int distance = Math.max(Math.abs(pos.x() - valid.state().centerX),
                        Math.abs(pos.z() - valid.state().centerZ));
                int tier = PlayerViewPlanner.priorityTier(valid.state().normalDistance, distance);
                ReadyChoice candidate = new ReadyChoice(delivery, subscriber, valid, tier, distance,
                        delivery.createdTick());
                byPlayer.computeIfAbsent(valid.state(), ignored ->
                        new PriorityQueue<>(ViewExtendService::compareReady)).add(candidate);
                fifoByPlayer.computeIfAbsent(valid.state(), ignored -> new ArrayDeque<>())
                        .addLast(candidate);
            }
            if (stale != null) for (LoadSubscriber subscriber : stale) delivery.removeSubscriber(subscriber);
        }
        readyDeliveries.removeIf(delivery -> !delivery.hasSubscribers());
        reconcileReadyBytes();
        return new ReadyPass(byPlayer, fifoByPlayer, new HashSet<>());
    }

    private void reconcileReadyBytes() {
        long bytes = 0;
        for (ReadyDelivery delivery : readyDeliveries) bytes += delivery.prepared().estimatedBytes();
        currentReadyBytes = bytes;
        peakReadyBytes = Math.max(peakReadyBytes, bytes);
    }

    private ReadyChoice chooseBestReady(ReadyPass pass) {
        ReadyChoice selected = null;
        for (PriorityQueue<ReadyChoice> queue : pass.byPlayer().values()) {
            ReadyChoice candidate = queue.peek();
            if (candidate == null || !canSend(candidate.valid().player(), candidate.valid().state())) continue;
            if (selected == null
                    || candidate.valid().state().readyFairnessSequence
                            < selected.valid().state().readyFairnessSequence
                    || (candidate.valid().state().readyFairnessSequence
                            == selected.valid().state().readyFairnessSequence
                        && compareReady(candidate, selected) < 0)) {
                selected = candidate;
            }
        }
        return selected;
    }

    private static ReadyChoice firstUnconsumed(ArrayDeque<ReadyChoice> fifo, Set<ReadyChoice> consumed) {
        if (fifo == null) return null;
        while (!fifo.isEmpty() && consumed.contains(fifo.peekFirst())) fifo.removeFirst();
        return fifo.peekFirst();
    }

    private static int compareReady(ReadyChoice left, ReadyChoice right) {
        return compareReadyPriority(left.tier(), left.distance(), left.age(),
                right.tier(), right.distance(), right.age());
    }

    static int compareReadyPriority(int leftTier, int leftDistance, long leftAge,
            int rightTier, int rightDistance, long rightAge) {
        return PlayerViewPlanner.comparePriority(leftTier, leftDistance, leftAge,
                rightTier, rightDistance, rightAge);
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
        sendChunkPacket(player, packet, null);
    }

    private void sendPreparedChunk(ServerPlayer player, PreparedVisualChunk prepared) {
        sendChunkPacket(player, prepared.packet(), prepared.composition());
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
        return hasWorkTime() && (borrowing || state.share.available())
                && sendsThisTick < config.maxMainThreadPreparedChunksPerTick()
                && hasNetworkQuota(player, state)
                && state.sendsThisTick < config.maxChunksPerPlayerPerTick() && connection.isConnected()
                && channel != null && channel.isWritable();
    }

    private static boolean hasNetworkQuota(ServerPlayer player, PlayerViewState state) {
        var flow = (com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender;
        return VisualChunkFlow.canSend(flow.viewextend$getQuota(), flow.viewextend$getOutstanding(),
                flow.viewextend$getMaxOutstanding(), state.visualBatchOpen);
    }

    private boolean canAdmit(ServerPlayer player, PlayerViewState state) {
        var connection = ((com.silver.viewextend.mixin.ServerConnectionAccessor)
                player.connection).viewextend$getConnection();
        return hasWorkTime() && (borrowing || state.share.available()) && connection.isConnected();
    }

    private void sendChunkPacket(ServerPlayer player, ClientboundLevelChunkWithLightPacket packet,
            VisualChunkPackets.PacketComposition composition) {
        String worldKey = player.level().dimension().identifier().toString();
        PlayerViewState state = statesByPlayer.get(new PlayerWorldKey(player.getUUID(), worldKey));
        if (state == null) throw new IllegalStateException("Missing visual batch state");
        int sentDistance = Math.max(Math.abs(packet.getX() - state.centerX),
                Math.abs(packet.getZ() - state.centerZ));
        sentDistances.record(sentDistance);
        var flow = (com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender;
        if (!state.visualBatchOpen) {
            flow.viewextend$setOutstanding(flow.viewextend$getOutstanding() + 1);
            state.visualBatchOpen = true;
            player.connection.send(net.minecraft.network.protocol.game.ClientboundChunkBatchStartPacket.INSTANCE);
        }
        flow.viewextend$setQuota(flow.viewextend$getQuota() - 1);
        int packetBytes = VisualChunkPackets.estimate(packet);
        var connection = ((com.silver.viewextend.mixin.ServerConnectionAccessor) player.connection)
                .viewextend$getConnection();
        var channel = ((com.silver.viewextend.mixin.ConnectionAccessor) connection).viewextend$getChannel();
        networkMetrics.observe(channel, packet, composition);
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

    private void recordCandidateSkip(PlayerViewPlanner.DemandSource source, CandidateSkipReason reason) {
        demandSkipped[source.ordinal()]++;
        candidateSkipReasons[reason.ordinal()]++;
        if (reason == CandidateSkipReason.ALREADY_SENT
                || reason == CandidateSkipReason.ALREADY_PENDING) {
            candidateSkipReasons[CandidateSkipReason.DUPLICATE_OVERLAP.ordinal()]++;
        }
        totalStaleCandidatesSkipped++;
    }

    private enum CandidateSkipReason {
        ALREADY_SENT,
        ALREADY_PENDING,
        RETRY_DEADLINE,
        SHARED_COOLDOWN,
        VANILLA_OWNED,
        OUTSIDE_RADIUS,
        DUPLICATE_OVERLAP,
        DELIVERY_FAILURE
    }

    private enum CandidateResult {
        ACCEPTED(null),
        BLOCKED(null),
        SKIPPED_ALREADY_SENT(CandidateSkipReason.ALREADY_SENT),
        SKIPPED_ALREADY_PENDING(CandidateSkipReason.ALREADY_PENDING),
        SKIPPED_RETRY_DEADLINE(CandidateSkipReason.RETRY_DEADLINE),
        SKIPPED_SHARED_COOLDOWN(CandidateSkipReason.SHARED_COOLDOWN),
        SKIPPED_DELIVERY_FAILURE(CandidateSkipReason.DELIVERY_FAILURE);

        private final CandidateSkipReason skipReason;
        CandidateResult(CandidateSkipReason skipReason) { this.skipReason = skipReason; }
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
        long demandDescriptors = 0;
        long activeDirectDemand = 0;
        long activeMovementDescriptors = 0;
        long activeGapDescriptors = 0;
        long activeNearCursors = 0;
        long activeBootstrapCursors = 0;
        long activeGapCursors = 0;
        for (PlayerViewState state : statesByPlayer.values()) {
            pending += state.pending.size();
            sent += state.sent.size();
            demandDescriptors += state.demandDescriptorCount();
            activeDirectDemand += state.directDemand.size();
            activeMovementDescriptors += state.movementDemand.size();
            activeGapDescriptors += state.vanillaGapDemand.size();
            if (state.nearActive) activeNearCursors++;
            if (state.bootstrapActive) activeBootstrapCursors++;
            if (state.gapActive) activeGapCursors++;
        }
        long completedQueued = completedTasksQueuedWindow.sumThenReset();
        PipelineSnapshot pipeline = schedulerSnapshot();
        PipelineMemoryMetrics.Snapshot nbtMemory = loader.memorySnapshot();
        PreparationMetrics.Snapshot preparationMetrics = loader.drainPreparationMetrics();
        ViewExtendNetworkMetrics.Snapshot network = networkMetrics.drainSnapshot();
        ReadyMemorySnapshot readyMemory = readyMemorySnapshot(server);
        ReuseAwareChunkCache.CacheStats cacheStats = packetCache.drainStats();
        PlayerViewPlanner.SelectionMetrics selectionMetrics = planner.drainSelectionMetrics();
        StateMemoryEstimate globalStateMemory = new StateMemoryEstimate(0, 0, 0, 0, 0, 0, 0);
        for (PlayerViewState state : statesByPlayer.values()) {
            globalStateMemory = globalStateMemory.plus(estimateStateMemory(state));
        }

        ViewExtendMod.LOGGER.info(
                "[ViewExtend Metrics] players={} states={} sent={} pending={} descriptors={} loads={} stages(queuedRead/read/complete/readyPackets/readySubscribers)={}/{}/{}/{}/{} distances(admitted/active/read/ready/sent)={}/{}/{}/{}/{} inversions(actual/avoided)={}/{} staleSkipped={} superseded={} skippedUnstarted={} cache={}/{}MiB packets={} unloads={} diskReads={} diskMisses={} nbtQueued={} nbtStarted={} coalesced={} cacheHits={} cacheMisses={} pipelineDeferrals={} candidateChecks={} preparedQueued={} preparedProcessed={} prepFailures={} preparedOrphaned={} remaps={} unsorted={} suppressedUnloads={} lightSky(nbt/fallback/water)={}/{}/{} lightBlock(nbt/fallback)={}/{} preCompressionEstimateMiB={} preCompressionEstimateMiBps={} mainElapsedMsps={} workerElapsedMsps={}",
                server.getPlayerList().getPlayers().size(), statesByPlayer.size(), sent, pending,
                demandDescriptors, loadsByKey.size(), pipeline.queuedReads(), pipeline.startedReads(),
                completedLoadQueue.size(), readyDeliveries.size(), pipeline.readySubscribers(),
                admittedDistances.display(), pipeline.activeDistances().display(),
                pipeline.readDistances().display(), pipeline.readyDistances().display(), sentDistances.display(),
                totalPriorityInversions, totalPriorityInversionsAvoided, totalStaleCandidatesSkipped,
                totalSupersededSubscribers, totalSkippedUnstartedLoads, packetCache.size(),
                packetCache.bytes() / (1024L * 1024L), totalChunkPacketsSent,
                totalUnloadPacketsSent, totalDiskNbtReads, totalDiskNbtReadMisses,
                totalNbtRequestsQueued, totalNbtRequestsStarted, totalCoalescedRequests,
                totalCacheHits, totalCacheMisses, totalPipelineDeferrals, totalCandidateInspections,
                completedQueued, totalPreparedResultsProcessed, totalPreparationFailures,
                totalPreparedOrphaned,
                totalLegacyBlockIdRemaps, totalUnsortedSectionInputs, totalSuppressedVanillaUnloads,
                totalSkyLightNbtSections, totalSkyLightFallbackSections,
                totalSkyLightWaterFallbackSections, totalBlockLightNbtSections,
                totalBlockLightFallbackSections,
                String.format(Locale.ROOT, "%.2f", totalNetworkBytesEstimate / 1048576.0),
                String.format(Locale.ROOT, "%.2f", totalNetworkBytesEstimate / 1048576.0 / seconds),
                String.format(Locale.ROOT, "%.2f", totalMainCpuNanos / 1_000_000.0 / seconds),
                String.format(Locale.ROOT, "%.2f", totalWorkerCpuNanos / 1_000_000.0 / seconds));

        ViewExtendMod.LOGGER.info(
                "[ViewExtend Demand] source(yielded/accepted/skipped) direct={}/{}/{} near={}/{}/{} bootstrap={}/{}/{} movement={}/{}/{} gap={}/{}/{} skip(sent/pending/retryDeadline/sharedCooldown/vanilla/outside/overlap/delivery)={}/{}/{}/{}/{}/{}/{}/{} active(direct/movement/gapLines/near/bootstrap/gapCursor)={}/{}/{}/{}/{}/{} selection(comparisons/directEntries/directPruned/movementHeap/nearCursor/bootstrapCursor/gap)={}/{}/{}/{}/{}/{}/{} heads(rebuilds/rekeys/merges/dedup)={}/{}/{}/{}",
                demandYielded[PlayerViewPlanner.DemandSource.DIRECT_RETRY.ordinal()],
                demandAccepted[PlayerViewPlanner.DemandSource.DIRECT_RETRY.ordinal()],
                demandSkipped[PlayerViewPlanner.DemandSource.DIRECT_RETRY.ordinal()],
                demandYielded[PlayerViewPlanner.DemandSource.NEAR.ordinal()],
                demandAccepted[PlayerViewPlanner.DemandSource.NEAR.ordinal()],
                demandSkipped[PlayerViewPlanner.DemandSource.NEAR.ordinal()],
                demandYielded[PlayerViewPlanner.DemandSource.BOOTSTRAP.ordinal()],
                demandAccepted[PlayerViewPlanner.DemandSource.BOOTSTRAP.ordinal()],
                demandSkipped[PlayerViewPlanner.DemandSource.BOOTSTRAP.ordinal()],
                demandYielded[PlayerViewPlanner.DemandSource.MOVEMENT.ordinal()],
                demandAccepted[PlayerViewPlanner.DemandSource.MOVEMENT.ordinal()],
                demandSkipped[PlayerViewPlanner.DemandSource.MOVEMENT.ordinal()],
                demandYielded[PlayerViewPlanner.DemandSource.VANILLA_GAP.ordinal()],
                demandAccepted[PlayerViewPlanner.DemandSource.VANILLA_GAP.ordinal()],
                demandSkipped[PlayerViewPlanner.DemandSource.VANILLA_GAP.ordinal()],
                candidateSkipReasons[CandidateSkipReason.ALREADY_SENT.ordinal()],
                candidateSkipReasons[CandidateSkipReason.ALREADY_PENDING.ordinal()],
                candidateSkipReasons[CandidateSkipReason.RETRY_DEADLINE.ordinal()],
                candidateSkipReasons[CandidateSkipReason.SHARED_COOLDOWN.ordinal()],
                candidateSkipReasons[CandidateSkipReason.VANILLA_OWNED.ordinal()],
                candidateSkipReasons[CandidateSkipReason.OUTSIDE_RADIUS.ordinal()],
                candidateSkipReasons[CandidateSkipReason.DUPLICATE_OVERLAP.ordinal()],
                candidateSkipReasons[CandidateSkipReason.DELIVERY_FAILURE.ordinal()],
                activeDirectDemand, activeMovementDescriptors, activeGapDescriptors,
                activeNearCursors, activeBootstrapCursors, activeGapCursors,
                selectionMetrics.comparisons(), selectionMetrics.directRetryComparisons(),
                selectionMetrics.directRetryPruned(), selectionMetrics.movementDescriptorComparisons(),
                selectionMetrics.nearCursorChecks(),
                selectionMetrics.bootstrapCursorChecks(), selectionMetrics.gapCursorChecks(),
                selectionMetrics.descriptorHeadRebuilds(), selectionMetrics.descriptorHeadRekeys(),
                selectionMetrics.descriptorMerges(), selectionMetrics.descriptorDeduplications());

        ViewExtendMod.LOGGER.info("[ViewExtend Preparation] outcomes={} sharedCooldownHits={}", failureCounts, totalCooldownHits);
        ViewExtendMod.LOGGER.info(
                "[ViewExtend Worker] prep(attempts/success/failure)={}/{}/{} totalMs={} avgMs={} p50Ms~={} p95Ms~={} maxMs={} stagesMs(validate/copy/transform/parse/light/packet)={}/{}/{}/{}/{}/{} packetMs(section/encode/decode)={}/{}/{} sectionCopyMiBAvoided={} bufferGrowths={} sections={} lightFastPath(layers/scanKiBAvoided)={}/{}",
                preparationMetrics.attempts(), preparationMetrics.successes(), preparationMetrics.failures(),
                formatWorkerMillis(preparationMetrics.totalNanos()),
                formatWorkerMillis(preparationMetrics.averageNanos()),
                formatWorkerMillis(preparationMetrics.percentileNanos(0.50)),
                formatWorkerMillis(preparationMetrics.percentileNanos(0.95)),
                formatWorkerMillis(preparationMetrics.maxNanos()),
                formatWorkerMillis(preparationMetrics.validationNanos()),
                formatWorkerMillis(preparationMetrics.copyNanos()),
                formatWorkerMillis(preparationMetrics.transformNanos()),
                formatWorkerMillis(preparationMetrics.parseNanos()),
                formatWorkerMillis(preparationMetrics.lightNanos()),
                formatWorkerMillis(preparationMetrics.packetNanos()),
                formatWorkerMillis(preparationMetrics.packetSectionNanos()),
                formatWorkerMillis(preparationMetrics.packetEncodingNanos()),
                formatWorkerMillis(preparationMetrics.packetDecodeNanos()),
                String.format(Locale.ROOT, "%.2f", preparationMetrics.sectionCopyBytesEliminated() / 1048576.0),
                preparationMetrics.packetBufferGrowths(),
                preparationMetrics.sectionsProcessed(), preparationMetrics.lightLayersFastPathed(),
                preparationMetrics.lightBytesScanAvoided() / 1024);
        long categorizedBytes = network.categorizedBytes();
        ViewExtendMod.LOGGER.info(
                "[ViewExtend Network] chunks={} compressionThreshold={} logicalMiB/ps={}/{} compressedMiB/ps={}/{} wireMiB/ps={}/{} ratio(compressed/logical)={} chunkBytes(avg/p50~/p95~)={}/{}/{} composition(bytes; pctOfCategorized) sections={};{} biomes={};{} heightmaps={};{} sky={};{} block={};{} masks={};{} protocol={};{} categorizedMiB={} uncategorized={} dropped={}",
                network.packets(), network.compressionThreshold(),
                formatMiB(network.logicalBytes()), formatMiBPerSecond(network.logicalBytes(), seconds),
                formatMiB(network.compressedBytes()), formatMiBPerSecond(network.compressedBytes(), seconds),
                formatMiB(network.wireBytes()), formatMiBPerSecond(network.wireBytes(), seconds),
                network.logicalBytes() == 0 ? "-" : String.format(Locale.ROOT, "%.3f",
                        (double) network.compressedBytes() / network.logicalBytes()),
                network.averageBytes(), network.percentileBytes(0.50), network.percentileBytes(0.95),
                network.blockStateBytes(), formatPercent(network.blockStateBytes(), categorizedBytes),
                network.biomeBytes(), formatPercent(network.biomeBytes(), categorizedBytes),
                network.heightmapBytes(), formatPercent(network.heightmapBytes(), categorizedBytes),
                network.skyLightBytes(), formatPercent(network.skyLightBytes(), categorizedBytes),
                network.blockLightBytes(), formatPercent(network.blockLightBytes(), categorizedBytes),
                network.lightMaskBytes(), formatPercent(network.lightMaskBytes(), categorizedBytes),
                network.protocolBytes(), formatPercent(network.protocolBytes(), categorizedBytes),
                formatMiB(categorizedBytes), network.uncategorizedPackets(), network.droppedObservations());
        ViewExtendMod.LOGGER.info(
                "[ViewExtend Memory] nbt(retained/bytes/avg/max/peakCount/peakBytes)={}/{}/{}/{}/{}/{} prep(queued/active/peakQueued/peakActive/queuedNbtBytes/activeNbtBytes/copyPayloads/peakCopyPayloads)={}/{}/{}/{}/{}/{}/{}/{} prepared(readyPackets/readyBytes/cacheBytes/uniqueBytes/peakReadyBytes/quotaBlockedBytes)={}/{}/{}/{}/{}/{} readyWaitTicks(avg/peak/currentOldest)={}/{}/{} orphan(rawProcessedCount/rawProcessedBytes/prepSkipped/preparedDiscarded/preparedBytes)={}/{}/{}/{}/{} state(sent/sentLod/pending/activeMetadata/descriptors/sentBytes/schedulerBytes)={}/{}/{}/{}/{}/{}/{}",
                nbtMemory.rawCount(), nbtMemory.rawBytes(), nbtMemory.averageRawBytes(),
                nbtMemory.maxRawBytes(), nbtMemory.peakRawCount(), nbtMemory.peakRawBytes(),
                nbtMemory.queuedTasks(), nbtMemory.activeTasks(), nbtMemory.peakQueuedTasks(),
                nbtMemory.peakActiveTasks(), nbtMemory.queuedRawBytes(), nbtMemory.activeRawBytes(),
                nbtMemory.copyingNbt() * 2, nbtMemory.peakCopyingNbt() * 2, readyMemory.packetCount(),
                readyMemory.readyBytes(), packetCache.bytes(), readyMemory.uniquePreparedBytes(),
                peakReadyBytes, readyMemory.quotaBlockedBytes(),
                totalReadyWaitSamples == 0 ? 0 : totalReadyWaitTicks / totalReadyWaitSamples,
                peakReadyWaitTicks, readyMemory.oldestWaitTicks(), totalOrphanRawNbtCount,
                totalOrphanRawNbtBytes, totalPreparationsSkippedNoSubscribers,
                totalPreparedDiscardedNoCache, totalPreparedDiscardedBytes,
                globalStateMemory.sentEntries(), globalStateMemory.sentLodEntries(),
                globalStateMemory.pendingEntries(), globalStateMemory.activeMetadataEntries(),
                globalStateMemory.descriptors(),
                globalStateMemory.sentBytes(), globalStateMemory.schedulerBytes());
        ViewExtendMod.LOGGER.info(
                "[ViewExtend Cache] admissions={} probation={} protectedShared={} hits={} promotions={} expirations={} evictedEntries={} evictedBytes={} expiredWithoutReuse={}",
                cacheStats.admissions(), cacheStats.probationAdmissions(),
                cacheStats.protectedAdmissions(), cacheStats.hits(), cacheStats.promotions(),
                cacheStats.expirations(), cacheStats.entryLimitEvictions(),
                cacheStats.byteLimitEvictions(), cacheStats.expiredWithoutReuse());
        for (PlayerViewState state : statesByPlayer.values()) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.playerUuid);
            if (player == null) continue;
            ViewExtendMod.LOGGER.info("[ViewExtend View] player={} world={} requested={} effective={} server={} sent={} pending={} retries={} bootstrapActive={} bootstrapRadius={} batchQuota={} unackedBatches={} lookahead={}",
                    player.getGameProfile().name(), state.worldKey, player.requestedViewDistance(), state.totalDistance,
                    state.normalDistance, state.sent.size(), state.pending.size(), state.retryDueByChunk.size(),
                    state.bootstrapActive, state.bootstrapRadius,
                    ((com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender).viewextend$getQuota(),
                    ((com.silver.viewextend.mixin.PlayerChunkSenderAccessor) player.connection.chunkSender).viewextend$getOutstanding(), state.lookahead);
            StateMemoryEstimate memory = estimateStateMemory(state);
            ViewExtendMod.LOGGER.info(
                    "[ViewExtend State] player={} world={} sent={} sentLod={} pending={} activeMetadata={} descriptors={} sentBytes~={} schedulerBytes~={}",
                    player.getGameProfile().name(), state.worldKey, memory.sentEntries(),
                    memory.sentLodEntries(), memory.pendingEntries(), memory.activeMetadataEntries(),
                    memory.descriptors(), memory.sentBytes(), memory.schedulerBytes());
        }
        ViewExtendMod.LOGGER.info("[ViewExtend Scheduler] readsPerTick={} budgetMs={} preparationLatencyTicks={} cachePromotions={}",
                adaptive.reads(), adaptive.allowance() / 1_000_000.0, adaptive.latencyTicks(), cacheStats.promotions());
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
        totalPreparationFailures = 0;
        totalPreparedOrphaned = 0;
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
        admittedDistances.clear();
        sentDistances.clear();
        totalPriorityInversions = 0;
        totalPriorityInversionsAvoided = 0;
        totalStaleCandidatesSkipped = 0;
        Arrays.fill(demandYielded, 0);
        Arrays.fill(demandAccepted, 0);
        Arrays.fill(demandSkipped, 0);
        Arrays.fill(candidateSkipReasons, 0);
        totalSupersededSubscribers = 0;
        totalSkippedUnstartedLoads = 0;
        totalPreparationsSkippedNoSubscribers = 0;
        totalOrphanRawNbtCount = 0;
        totalOrphanRawNbtBytes = 0;
        totalPreparedDiscardedBytes = 0;
        totalPreparedDiscardedNoCache = 0;
    }

    private PipelineSnapshot schedulerSnapshot() {
        DistanceRange active = new DistanceRange();
        DistanceRange reads = new DistanceRange();
        DistanceRange ready = new DistanceRange();
        int queuedReads = 0;
        int startedReads = 0;
        int readySubscribers = 0;
        for (PlayerViewState state : statesByPlayer.values()) {
            var pending = state.pending.iterator();
            while (pending.hasNext()) active.record(distanceFromState(state, pending.nextLong()));
        }
        for (SharedLoad load : loadsByKey.values()) {
            if (load.started) startedReads++;
            else queuedReads++;
            if (!load.started) continue;
            for (LoadSubscriber subscriber : load.subscribers) {
                if (subscriber.state().isCurrentRequest(subscriber.state(), load.key.chunkLong(),
                        subscriber.requestId())) {
                    reads.record(distanceFromState(subscriber.state(), load.key.chunkLong()));
                }
            }
        }
        for (ReadyDelivery delivery : readyDeliveries) {
            for (LoadSubscriber subscriber : delivery.subscribers()) {
                if (subscriber.state().isCurrentRequest(subscriber.state(), delivery.key().chunkLong(),
                        subscriber.requestId())) {
                    ready.record(distanceFromState(subscriber.state(), delivery.key().chunkLong()));
                    readySubscribers++;
                }
            }
        }
        return new PipelineSnapshot(queuedReads, startedReads, readySubscribers, active, reads, ready);
    }

    private ReadyMemorySnapshot readyMemorySnapshot(MinecraftServer server) {
        Set<PreparedVisualChunk> readySeen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        Set<PreparedVisualChunk> blockedSeen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        long readyBytes = 0;
        long readyOnlyBytes = 0;
        long quotaBlockedBytes = 0;
        long oldestWaitTicks = 0;
        for (ReadyDelivery delivery : readyDeliveries) {
            PreparedVisualChunk prepared = delivery.prepared();
            if (readySeen.add(prepared)) {
                readyBytes += prepared.estimatedBytes();
                if (!packetCache.containsIdentity(delivery.key(), prepared)) {
                    readyOnlyBytes += prepared.estimatedBytes();
                }
            }
            oldestWaitTicks = Math.max(oldestWaitTicks, Math.max(0, ticks - delivery.createdTick()));
            boolean quotaBlocked = false;
            for (LoadSubscriber subscriber : delivery.subscribers()) {
                PlayerViewState state = subscriber.state();
                if (!state.isCurrentRequest(state, delivery.key().chunkLong(), subscriber.requestId())) continue;
                ServerPlayer player = server.getPlayerList().getPlayer(subscriber.playerUuid());
                if (player != null && state.worldKey.equals(
                        player.level().dimension().identifier().toString())
                        && !hasNetworkQuota(player, state)) {
                    quotaBlocked = true;
                    break;
                }
            }
            if (quotaBlocked && blockedSeen.add(prepared)) {
                quotaBlockedBytes += prepared.estimatedBytes();
            }
        }
        return new ReadyMemorySnapshot(readyDeliveries.size(), readyBytes,
                packetCache.bytes() + readyOnlyBytes, quotaBlockedBytes, oldestWaitTicks);
    }

    static StateMemoryEstimate estimateStateMemory(PlayerViewState state) {
        long sentBytes = primitiveTableBytes(state.sent.size(), 8, 48)
                + primitiveTableBytes(state.sentLodByChunk.size(), 12, 56);
        long schedulerBytes = primitiveTableBytes(state.pending.size(), 8, 48)
                + primitiveTableBytes(state.pendingLodByChunk.size(), 12, 56)
                + primitiveTableBytes(state.pendingRequestIds.size(), 16, 56)
                + primitiveTableBytes(state.directDemandSet.size(), 8, 48)
                + primitiveTableBytes(state.directDemandAge.size(), 16, 56)
                + 24L + 8L * state.directDemand.elements().length
                + primitiveTableBytes(state.retryDueByChunk.size(), 12, 56)
                + primitiveTableBytes(state.unloadDueByChunk.size(), 12, 56)
                + 40L * (state.retryQueue.size() + state.unloadQueue.size())
                + 72L * (state.movementDemand.size() + state.vanillaGapDemand.size())
                + 8L * ((state.movementHeads == null ? 0 : state.movementHeads.size())
                        + (state.vanillaGapHeads == null ? 0 : state.vanillaGapHeads.size()))
                + 280L;
        long activeMetadata = (long) state.pending.size() + state.pendingLodByChunk.size()
                + state.pendingRequestIds.size() + state.directDemand.size()
                + state.directDemandSet.size() + state.directDemandAge.size()
                + state.retryDueByChunk.size() + state.retryQueue.size()
                + state.unloadDueByChunk.size() + state.unloadQueue.size()
                + (state.movementHeads == null ? 0 : state.movementHeads.size())
                + (state.vanillaGapHeads == null ? 0 : state.vanillaGapHeads.size())
                + state.demandDescriptorCount();
        return new StateMemoryEstimate(state.sent.size(), state.sentLodByChunk.size(),
                state.pending.size(), activeMetadata, state.demandDescriptorCount(), sentBytes,
                schedulerBytes);
    }

    private static String formatWorkerMillis(long nanos) {
        return String.format(Locale.ROOT, "%.3f", nanos / 1_000_000.0);
    }

    private static String formatMiB(long bytes) {
        return String.format(Locale.ROOT, "%.2f", bytes / 1048576.0);
    }

    private static String formatMiBPerSecond(long bytes, double seconds) {
        return String.format(Locale.ROOT, "%.2f", bytes / 1048576.0 / seconds);
    }

    private static String formatPercent(long bytes, long total) {
        return total == 0 ? "-" : String.format(Locale.ROOT, "%.1f%%", 100.0 * bytes / total);
    }

    private static long primitiveTableBytes(int size, int bytesPerSlot, int objectBytes) {
        if (size == 0) return objectBytes + 16L;
        long needed = (size * 4L + 2L) / 3L;
        long capacity = 2;
        while (capacity < needed && capacity < (1L << 30)) capacity <<= 1;
        return objectBytes + 16L + bytesPerSlot * (capacity + 1);
    }

    private static int distanceFromState(PlayerViewState state, long packed) {
        return Math.max(Math.abs(ChunkPos.getX(packed) - state.centerX),
                Math.abs(ChunkPos.getZ(packed) - state.centerZ));
    }

    public void shutdown() {
        shuttingDown = true;
        loader.close();
        nbtReadQueue.clear();
        loadsByKey.clear();
        completedLoadQueue.clear();
        readyDeliveries.clear();
        currentReadyBytes = 0;
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

    private record ReadyChoice(
            ReadyDelivery delivery,
            LoadSubscriber subscriber,
            ValidSubscriber valid,
            int tier,
            int distance,
            long age) {
    }

    private record ReadyPass(
            Map<PlayerViewState, PriorityQueue<ReadyChoice>> byPlayer,
            Map<PlayerViewState, ArrayDeque<ReadyChoice>> fifoByPlayer,
            Set<ReadyChoice> consumed) {
    }

    private record QueuedLoadRank(
            SharedLoad load,
            ValidSubscriber firstValid,
            int tier,
            int distance) {
    }

    private record PipelineSnapshot(
            int queuedReads,
            int startedReads,
            int readySubscribers,
            DistanceRange activeDistances,
            DistanceRange readDistances,
            DistanceRange readyDistances) {
    }

    private record ReadyMemorySnapshot(
            int packetCount,
            long readyBytes,
            long uniquePreparedBytes,
            long quotaBlockedBytes,
            long oldestWaitTicks) {
    }

    record StateMemoryEstimate(
            long sentEntries,
            long sentLodEntries,
            long pendingEntries,
            long activeMetadataEntries,
            long descriptors,
            long sentBytes,
            long schedulerBytes) {
        StateMemoryEstimate plus(StateMemoryEstimate other) {
            return new StateMemoryEstimate(sentEntries + other.sentEntries,
                    sentLodEntries + other.sentLodEntries,
                    pendingEntries + other.pendingEntries,
                    activeMetadataEntries + other.activeMetadataEntries,
                    descriptors + other.descriptors,
                    sentBytes + other.sentBytes,
                    schedulerBytes + other.schedulerBytes);
        }
    }

    private record PreparedLoadResult(
            GlobalChunkKey key,
            PreparedVisualChunk prepared,
            boolean missing,
            Throwable error,
            long workerNanos,
            int rawNbtBytes) {
        private static PreparedLoadResult failed(GlobalChunkKey key, Throwable error) {
            return new PreparedLoadResult(key, null, false, error, 0L, 0);
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
        private final int createdTick;

        ReadyDelivery(GlobalChunkKey key, PreparedVisualChunk prepared, List<LoadSubscriber> subscribers) {
            this(key, prepared, subscribers, 0);
        }

        ReadyDelivery(GlobalChunkKey key, PreparedVisualChunk prepared, List<LoadSubscriber> subscribers,
                int createdTick) {
            this.key = key;
            this.prepared = prepared;
            this.subscribers = new ArrayDeque<>(subscribers);
            this.createdTick = createdTick;
        }

        GlobalChunkKey key() { return key; }
        PreparedVisualChunk prepared() { return prepared; }
        int createdTick() { return createdTick; }
        LoadSubscriber nextSubscriber() { return subscribers.pollFirst(); }
        void defer(LoadSubscriber subscriber) { subscribers.addLast(subscriber); }
        boolean hasSubscribers() { return !subscribers.isEmpty(); }
        Iterable<LoadSubscriber> subscribers() { return subscribers; }
        boolean removeSubscriber(LoadSubscriber subscriber) { return subscribers.removeFirstOccurrence(subscriber); }
        void removeSubscriber(PlayerViewState state, long requestId) {
            subscribers.removeIf(subscriber -> subscriber.state() == state
                    && subscriber.requestId() == requestId);
        }
    }

    private static final class DistanceRange {
        private int nearest = Integer.MAX_VALUE;
        private int farthest = Integer.MIN_VALUE;
        void record(int distance) {
            nearest = Math.min(nearest, distance);
            farthest = Math.max(farthest, distance);
        }
        String display() { return nearest == Integer.MAX_VALUE ? "-/-" : nearest + "/" + farthest; }
        void clear() { nearest = Integer.MAX_VALUE; farthest = Integer.MIN_VALUE; }
    }
}
