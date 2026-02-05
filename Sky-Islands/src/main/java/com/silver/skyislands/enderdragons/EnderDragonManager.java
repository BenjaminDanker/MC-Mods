package com.silver.skyislands.enderdragons;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.boss.dragon.phase.PhaseType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;

import com.silver.skyislands.specialitems.SpecialFeatherItem;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

public final class EnderDragonManager {
    public static final String MANAGED_TAG = "sky_islands_managed_dragon";
    private static final long RECENT_DEATH_GUARD_TICKS = 20L * 30L;
    private static final Map<UUID, Long> recentlyDiedUntilTick = new HashMap<>();

    private static final Logger LOGGER = LoggerFactory.getLogger(EnderDragonManager.class);

    private static EnderDragonsConfig config;
    private static VirtualDragonStore virtualStore;
    private static DragonChunkPreloader chunkPreloader;

    private static DragonHeadTracker headTracker;

    private static MinecraftServer serverRef;

    private static long serverTicks;

    private static int effectiveActivationRadiusBlocks = -1;
    private static int effectiveDespawnRadiusBlocks = -1;
    private static int effectiveMinSpawnDistanceBlocks = -1;
    private static boolean effectiveDistancesInitialized;

    private static final Map<UUID, Long> nextHeadingNudgeTick = new HashMap<>();
    private static final Map<UUID, Vec3d> returnTargetHeading = new HashMap<>();
    private static final Map<UUID, Long> returnTurnUntilTick = new HashMap<>();
    private static final Map<UUID, Long> nextReturnDecisionTick = new HashMap<>();
    private static final Map<UUID, Long> nextSpawnWaitLogTick = new HashMap<>();

    private static final Set<UUID> debugLoggedUnmanagedDragons = new HashSet<>();

    // Prevent spamming chunk release calls/logs when a dragon stays inactive for a long time.
    private static final Set<UUID> inactiveChunkReleaseDone = new HashSet<>();

    private static final Map<UUID, Vec3d> lastLoadedEntityPos = new HashMap<>();
    private static final Map<UUID, Integer> loadedStuckTicks = new HashMap<>();
    private static final Map<UUID, Long> loadedSpawnGraceUntilTick = new HashMap<>();
    private static final Map<UUID, Long> loadedPendingTakeoffKickTick = new HashMap<>();

    private static final Map<UUID, BlockPos> fearedHeadPos = new HashMap<>();
    private static final Map<UUID, Vec3d> fearedHeadAvoidTarget = new HashMap<>();
    private static final Map<UUID, Long> fearedHeadLockUntilTick = new HashMap<>();
    private static final Map<UUID, Long> fearedHeadNextScanTick = new HashMap<>();

    private EnderDragonManager() {
    }

    public static void init() {
        final boolean debug = LOGGER.isDebugEnabled();
        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] init begin");
        }
        config = EnderDragonsConfig.loadOrCreate();
        virtualStore = new VirtualDragonStore();
        chunkPreloader = new DragonChunkPreloader(config.preloadTicketLevel);
        headTracker = new DragonHeadTracker();

        effectiveActivationRadiusBlocks = config.activationRadiusBlocks;
        effectiveDespawnRadiusBlocks = config.despawnRadiusBlocks;
        effectiveMinSpawnDistanceBlocks = config.minSpawnDistanceBlocks;
        effectiveDistancesInitialized = false;

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] init ok virtualTravel={} min={} activation={} despawn={} chunkPreload={} headFear={}",
                    config.virtualTravelEnabled,
                    config.minimumDragons,
                    config.activationRadiusBlocks,
                    config.despawnRadiusBlocks,
                    config.forceChunkLoadingEnabled,
                    config.headFearEnabled);
        }

        ServerTickEvents.END_SERVER_TICK.register(EnderDragonManager::tick);

        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, blockEntity) -> {
            if (world == null || world.isClient()) {
                return;
            }
            if (headTracker != null && world instanceof ServerWorld serverWorld) {
                if (LOGGER.isDebugEnabled() && (state != null) && (state.isOf(Blocks.DRAGON_HEAD) || state.isOf(Blocks.DRAGON_WALL_HEAD))) {
                    LOGGER.debug("[Sky-Islands][dragons][heads] break hook player={} pos={} block={} blockEntity={}",
                            player == null ? "<null>" : player.getName().getString(),
                            pos,
                            state.getBlock(),
                            blockEntity == null ? "<null>" : blockEntity.getType());
                }
                headTracker.onPossibleBroken(serverWorld, pos, state);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (virtualStore != null) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] SERVER_STOPPING flush");
                }
                virtualStore.flush();
            }

            if (headTracker != null) {
                headTracker.flush();
            }
        });
    }

    public static void onPossibleDragonHeadPlaced(net.minecraft.world.World world, BlockPos pos) {
        if (world == null || world.isClient()) {
            return;
        }
        if (headTracker != null && world instanceof ServerWorld serverWorld) {
            if (LOGGER.isDebugEnabled()) {
                BlockState bs = serverWorld.getBlockState(pos);
                if (bs.isOf(Blocks.DRAGON_HEAD) || bs.isOf(Blocks.DRAGON_WALL_HEAD)) {
                    LOGGER.debug("[Sky-Islands][dragons][heads] place hook dim={} pos={} block={}",
                            serverWorld.getRegistryKey().getValue(),
                            pos,
                            bs.getBlock());
                }
            }
            headTracker.onPossiblePlaced(serverWorld, pos);
        }
    }

    public static boolean isManaged(EnderDragonEntity dragon) {
        return dragon.getCommandTags().contains(MANAGED_TAG);
    }

    public static EnderDragonsConfig getConfig() {
        return config;
    }

    public static VirtualDragonStore.VirtualDragonState getVirtualState(UUID id) {
        VirtualDragonStore store = virtualStore;
        if (store == null) {
            return null;
        }
        return store.get(id);
    }

    public static boolean isHeadAvoidActive(UUID id) {
        return fearedHeadAvoidTarget.containsKey(id);
    }

    public static void onManagedDragonDeath(EnderDragonEntity dragon) {
        final boolean debug = LOGGER.isDebugEnabled();
        if (virtualStore == null) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] onDeath ignore (no store) uuid={} managed={}",
                        dragon.getUuidAsString(),
                        isManaged(dragon));
            }
            return;
        }
        if (!isManaged(dragon)) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] onDeath ignore (not managed) uuid={}", dragon.getUuidAsString());
            }
            return;
        }

        // Guaranteed special drop for managed dragons.
        if (dragon.getEntityWorld() instanceof ServerWorld serverWorld) {
            ItemStack feather = SpecialFeatherItem.createOne();
            ItemEntity entity = new ItemEntity(serverWorld, dragon.getX(), dragon.getY(), dragon.getZ(), feather);
            entity.setToDefaultPickupDelay();
            serverWorld.spawnEntity(entity);
        }

        DragonIdTags.getId(dragon).ifPresent(id -> {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] onDeath cleanup id={} uuid={}", shortId(id), dragon.getUuidAsString());
            }
            virtualStore.remove(id);
            nextHeadingNudgeTick.remove(id);
            returnTargetHeading.remove(id);
            returnTurnUntilTick.remove(id);
            nextReturnDecisionTick.remove(id);
            lastLoadedEntityPos.remove(id);
            loadedStuckTicks.remove(id);
            loadedSpawnGraceUntilTick.remove(id);
            fearedHeadPos.remove(id);
            fearedHeadAvoidTarget.remove(id);
            fearedHeadLockUntilTick.remove(id);
            fearedHeadNextScanTick.remove(id);

            inactiveChunkReleaseDone.remove(id);
            // Chunk tickets are also released on despawn/inactive; keep death cleanup minimal.
        });
    }

    public static int dumpDragons(ServerCommandSource source, boolean includeVirtual, boolean includeLoaded) {
        if (virtualStore == null || config == null) {
            source.sendFeedback(() -> Text.literal("Sky-Islands dragons system not initialised yet."), false);
            return 0;
        }

        ServerWorld overworld = source.getServer().getOverworld();
        if (overworld == null) {
            source.sendFeedback(() -> Text.literal("Sky-Islands: no overworld available."), false);
            return 0;
        }

        List<VirtualDragonStore.VirtualDragonState> snapshot = virtualStore.snapshot();

        Map<UUID, EnderDragonEntity> loaded = new HashMap<>();
        if (includeLoaded) {
            for (Entity entity : overworld.iterateEntities()) {
                if (!(entity instanceof EnderDragonEntity dragon)) {
                    continue;
                }
                if (!isManaged(dragon)) {
                    continue;
                }
                getOrAssignId(dragon).ifPresent(id -> loaded.put(id, dragon));
            }
        }

        int virtualCount = includeVirtual ? snapshot.size() : 0;
        int loadedCount = loaded.size();
        source.sendFeedback(() -> Text.literal("Sky-Islands dragons: virtual=" + virtualCount + " loaded=" + loadedCount +
            " (activationRadius=" + getActivationRadiusBlocks() + " despawnRadius=" + getDespawnRadiusBlocks() + ")"), false);

        int shown = 0;
        int maxShow = 25;

        if (includeVirtual) {
            for (VirtualDragonStore.VirtualDragonState s : snapshot) {
                if (shown >= maxShow) {
                    break;
                }
                String line = " - id=" + shortId(s.id()) +
                        " virtualPos=(" + round1(s.pos().x) + ", " + round1(s.pos().y) + ", " + round1(s.pos().z) + ")" +
                        " heading=(" + round2(s.headingX()) + ", " + round2(s.headingZ()) + ")";
                source.sendFeedback(() -> Text.literal(line), false);

                if (includeLoaded) {
                    EnderDragonEntity dragon = loaded.get(s.id());
                    if (dragon != null) {
                        boolean provoked = dragon instanceof DragonProvokedAccess access && access.skyIslands$isProvoked();
                        String extra = "   loaded entityPos=(" + round1(dragon.getX()) + ", " + round1(dragon.getY()) + ", " + round1(dragon.getZ()) + ")" +
                                " phase=" + dragon.getPhaseManager().getCurrent().getType() +
                                " provoked=" + provoked;
                        source.sendFeedback(() -> Text.literal(extra), false);
                    }
                }
                shown++;
            }
        } else if (includeLoaded) {
            for (Map.Entry<UUID, EnderDragonEntity> e : loaded.entrySet()) {
                if (shown >= maxShow) {
                    break;
                }
                EnderDragonEntity dragon = e.getValue();
                boolean provoked = dragon instanceof DragonProvokedAccess access && access.skyIslands$isProvoked();
                String line = " - id=" + shortId(e.getKey()) +
                        " entityPos=(" + round1(dragon.getX()) + ", " + round1(dragon.getY()) + ", " + round1(dragon.getZ()) + ")" +
                        " phase=" + dragon.getPhaseManager().getCurrent().getType() +
                        " provoked=" + provoked;
                source.sendFeedback(() -> Text.literal(line), false);
                shown++;
            }
        }

        if ((includeVirtual && snapshot.size() > maxShow) || (includeLoaded && loaded.size() > maxShow)) {
            source.sendFeedback(() -> Text.literal("(output truncated; showing first " + maxShow + ")"), false);
        }

        return 1;
    }

    public record DragonLocatorResult(Vec3d pos, double headingX, double headingZ, float headingYawDegrees, boolean isLoadedEntity) {
    }

    public static DragonLocatorResult findNearestDragonFor(ServerPlayerEntity player) {
        if (player == null) {
            return null;
        }
        if (virtualStore == null) {
            return null;
        }

        MinecraftServer server = serverRef;
        if (server == null) {
            return null;
        }

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return null;
        }

        Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
        List<VirtualDragonStore.VirtualDragonState> snapshot = virtualStore.snapshot();
        if (snapshot.isEmpty()) {
            return null;
        }

        VirtualDragonStore.VirtualDragonState best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (VirtualDragonStore.VirtualDragonState s : snapshot) {
            double d2 = playerPos.squaredDistanceTo(s.pos());
            if (d2 < bestSq) {
                bestSq = d2;
                best = s;
            }
        }

        if (best == null) {
            return null;
        }

        EnderDragonEntity loaded = findLoadedById(overworld, best.id()).orElse(null);
        Vec3d pos = loaded != null ? new Vec3d(loaded.getX(), loaded.getY(), loaded.getZ()) : best.pos();
        double hx = best.headingX();
        double hz = best.headingZ();

        float yaw;
        if (loaded != null) {
            yaw = loaded.getYaw();
        } else {
            // Simple yaw derived from heading vector.
            yaw = (float) Math.toDegrees(Math.atan2(hz, hx));
        }

        return new DragonLocatorResult(pos, hx, hz, yaw, loaded != null);
    }

    private static Optional<EnderDragonEntity> findLoadedById(ServerWorld overworld, UUID id) {
        if (overworld == null || id == null) {
            return Optional.empty();
        }
        for (Entity entity : overworld.iterateEntities()) {
            if (!(entity instanceof EnderDragonEntity dragon)) {
                continue;
            }
            if (!isManaged(dragon)) {
                continue;
            }
            UUID internalId = getOrAssignId(dragon).orElse(null);
            if (id.equals(internalId)) {
                return Optional.of(dragon);
            }
        }
        return Optional.empty();
    }

    private static void tick(MinecraftServer server) {
        serverRef = server;
        serverTicks++;
        if ((serverTicks % 200L) == 0L && !recentlyDiedUntilTick.isEmpty()) {
            recentlyDiedUntilTick.entrySet().removeIf(e -> e.getValue() < serverTicks);

        }

        updateEffectiveDistancesFromServerSettings(server);

        if (headTracker != null) {
            headTracker.tick(server, serverTicks);
        }

        final boolean debug = LOGGER.isDebugEnabled();
        if (debug && (serverTicks % 200L) == 0L) {
            LOGGER.debug("[Sky-Islands][dragons][manager] tick serverTicks={} players={}", serverTicks, server.getPlayerManager().getPlayerList().size());
        }

        if (virtualStore != null && config != null && config.virtualStateFlushIntervalMinutes > 0) {
            long intervalTicks = (long) config.virtualStateFlushIntervalMinutes * 60L * 20L;
            if (intervalTicks > 0 && (serverTicks % intervalTicks) == 0) {
                if (debug) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] periodic virtual flush intervalTicks={}", intervalTicks);
                }
                virtualStore.flush();
            }
        }

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] tick skip (no overworld)");
            }
            return;
        }

        if (debug && (serverTicks % 40L) == 0L) {
            debugLogUnmanagedDragons(overworld);
        }

        // Reconcile any managed dragons that exist as loaded entities but are missing from the
        // virtual store (e.g., crash/unflushed shutdown). This must run before we decide whether to
        // create new virtual dragons, otherwise we can overspawn beyond the configured minimum.
        if (serverTicks % 40 == 0) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] recoverMissingLoadedDragons");
            }
            recoverMissingLoadedDragons(overworld);
        }

        if (serverTicks % 200 == 0) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] ensureMinimumVirtualDragons");
            }
            ensureMinimumVirtualDragons(overworld);
        }

        tickVirtualTravel(overworld);
    }

    private static void debugLogUnmanagedDragons(ServerWorld world) {
        int found = 0;
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof EnderDragonEntity dragon)) {
                continue;
            }
            if (isManaged(dragon)) {
                continue;
            }

            found++;
            UUID id = dragon.getUuid();
            if (!debugLoggedUnmanagedDragons.add(id)) {
                continue;
            }

            ChunkPos cp = new ChunkPos(dragon.getBlockPos());
            LOGGER.warn("[Sky-Islands][debug] unmanaged EnderDragonEntity present uuid={} pos=({}, {}, {}) chunk=({}, {}) tags={} phase={} fightOrigin={}",
                    dragon.getUuidAsString(),
                    round1(dragon.getX()), round1(dragon.getY()), round1(dragon.getZ()),
                    cp.x, cp.z,
                    dragon.getCommandTags().size(),
                    dragon.getPhaseManager().getCurrent().getType(),
                    dragon.getFightOrigin());
        }

        if (found == 0 && !debugLoggedUnmanagedDragons.isEmpty()) {
            // If none are present now, allow future instances to be logged again.
            debugLoggedUnmanagedDragons.clear();
        }
    }

    private static void recoverMissingLoadedDragons(ServerWorld world) {
        if (virtualStore == null) {
            return;
        }

        final boolean debug = LOGGER.isDebugEnabled();
        int scanned = 0;

        Set<UUID> known = new HashSet<>();
        for (VirtualDragonStore.VirtualDragonState s : virtualStore.snapshot()) {
            known.add(s.id());
        }

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] recover scan begin knownVirtual={}", known.size());
        }

        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof EnderDragonEntity dragon)) {
                continue;
            }
            if (!isManaged(dragon)) {
                continue;
            }

            if (dragon.isRemoved() || !dragon.isAlive()) {
                continue;
            }

            // Don't recover dragons that are in the middle of their death animation.
            if (dragon.getPhaseManager().getCurrent().getType() == PhaseType.DYING) {
                continue;
            }

            scanned++;

            getOrAssignId(dragon).ifPresent(id -> {
                if (known.contains(id)) {
                    return;
                }

                Long recentlyDiedUntil = recentlyDiedUntilTick.get(id);
                if (recentlyDiedUntil != null && recentlyDiedUntil >= serverTicks) {
                    return;
                }

                Vec3d pos = new Vec3d(dragon.getX(), dragon.getY(), dragon.getZ());

                double hx;
                double hz;
                Vec3d v = dragon.getVelocity();
                double hlen = Math.sqrt(v.x * v.x + v.z * v.z);
                if (hlen > 1.0e-3) {
                    hx = v.x / hlen;
                    hz = v.z / hlen;
                } else {
                    double yawRad = Math.toRadians(dragon.getYaw());
                    hx = -Math.sin(yawRad);
                    hz = Math.cos(yawRad);
                }

                virtualStore.upsert(new VirtualDragonStore.VirtualDragonState(id, pos, hx, hz, serverTicks));
                known.add(id);
                nextHeadingNudgeTick.put(id, serverTicks + 1);
                lastLoadedEntityPos.remove(id);
                loadedStuckTicks.put(id, 0);
                loadedSpawnGraceUntilTick.put(id, serverTicks + 100);

                if (debug) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] recovered id={} uuid={} pos=({}, {}, {}) heading=({}, {})",
                            shortId(id),
                            dragon.getUuidAsString(),
                            round1(pos.x), round1(pos.y), round1(pos.z),
                            round2(hx), round2(hz));
                }

                LOGGER.warn("[Sky-Islands] Recovered managed dragon missing from virtual store. id={} uuid={} entityPos=({}, {}, {})",
                        shortId(id),
                        dragon.getUuidAsString(),
                        round1(pos.x), round1(pos.y), round1(pos.z));
            });
        }

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] recover scan end scannedManagedLoaded={}", scanned);
        }
    }

    private static void ensureMinimumVirtualDragons(ServerWorld world) {
        if (virtualStore == null) {
            return;
        }

        final boolean debug = LOGGER.isDebugEnabled();
        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] ensureMinimum begin current={} minimum={}", virtualStore.size(), config.minimumDragons);
        }

        while (virtualStore.size() < config.minimumDragons) {
            UUID id = UUID.randomUUID();
            BlockPos spawnPos = pickSpawnPos(world);

            double angle = world.getRandom().nextDouble() * (Math.PI * 2.0);
            double hx = Math.cos(angle);
            double hz = Math.sin(angle);

            virtualStore.upsert(new VirtualDragonStore.VirtualDragonState(
                    id,
                    new Vec3d(spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5),
                    hx,
                    hz,
                    serverTicks
            ));

            LOGGER.info("[Sky-Islands] Created virtual dragon id={} pos=({}, {}, {}) heading=({}, {})",
                    shortId(id),
                    spawnPos.getX() + 0.5, spawnPos.getY() + 0.5, spawnPos.getZ() + 0.5,
                    round2(hx), round2(hz));
        }

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] ensureMinimum end current={}", virtualStore.size());
        }
    }

    private static void tickVirtualTravel(ServerWorld world) {
        if (virtualStore == null) {
            return;
        }

        final boolean debug = LOGGER.isDebugEnabled();

        List<VirtualDragonStore.VirtualDragonState> snapshot = virtualStore.snapshot();

        if (debug && (serverTicks % 200L) == 0L) {
            LOGGER.debug("[Sky-Islands][dragons][manager] tickVirtualTravel snapshot={} virtualTravelEnabled={} forceChunkLoadEnabled={}",
                    snapshot.size(),
                    config.virtualTravelEnabled,
                    config.forceChunkLoadingEnabled);
        }

        // Build a quick index of currently-loaded managed dragons by our internal dragon id.
        Map<UUID, EnderDragonEntity> loaded = new HashMap<>();
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof EnderDragonEntity dragon)) {
                continue;
            }
            if (!isManaged(dragon)) {
                continue;
            }
            getOrAssignId(dragon).ifPresent(id -> loaded.put(id, dragon));
        }

        if (debug && (serverTicks % 200L) == 0L) {
            LOGGER.debug("[Sky-Islands][dragons][manager] loadedIndex size={}", loaded.size());
        }

        int chunkBudget = config.forceChunkLoadingEnabled ? config.maxChunkLoadsPerTick : 0;

        if (debug && config.forceChunkLoadingEnabled && (serverTicks % 200L) == 0L) {
            LOGGER.debug("[Sky-Islands][dragons][manager] chunkBudget start={} preloadRadius={} aheadChunks={} ticketLevel={}",
                chunkBudget,
                config.preloadRadiusChunks,
                config.preloadAheadChunks,
                config.preloadTicketLevel);
        }

        for (VirtualDragonStore.VirtualDragonState state : snapshot) {
            VirtualDragonStore.VirtualDragonState updated = advanceVirtualState(world, state);

            boolean playerNearVirtual = isAnyPlayerNear(world, updated.pos(), getActivationRadiusBlocks());
            EnderDragonEntity dragon = loaded.get(updated.id());

            if (config.virtualTravelEnabled) {
                if (dragon == null) {
                    if (playerNearVirtual) {
                        inactiveChunkReleaseDone.remove(updated.id());
                        if (debug) {
                            LOGGER.debug("[Sky-Islands][dragons][manager] id={} activate virtualPos=({}, {}, {})",
                                    shortId(updated.id()),
                                    round1(updated.pos().x), round1(updated.pos().y), round1(updated.pos().z));
                        }
                        updated = rewindIfTooCloseToPlayers(world, updated);

                        if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                            Set<ChunkPos> desired = computeDesiredChunks(updated);
                            if (debug) {
                                LOGGER.debug("[Sky-Islands][dragons][manager] id={} preload desiredChunks={} budgetBefore={}",
                                        shortId(updated.id()),
                                        desired.size(),
                                        chunkBudget);
                            }
                            chunkBudget -= chunkPreloader.request(world, updated.id(), desired, serverTicks, chunkBudget);
                        }

                        if (isSpawnReady(world, updated)) {
                            dragon = spawnDragonFromVirtual(world, updated);
                            if (dragon != null) {
                                loaded.put(updated.id(), dragon);
                            } else if (debug) {
                                LOGGER.debug("[Sky-Islands][dragons][manager] id={} spawn attempted but returned null", shortId(updated.id()));
                            }
                        } else {
                            long nextLog = nextSpawnWaitLogTick.getOrDefault(updated.id(), 0L);
                            if (serverTicks >= nextLog) {
                                nextSpawnWaitLogTick.put(updated.id(), serverTicks + 200);
                                LOGGER.info("[Sky-Islands] Dragon id={} near player(s) but spawn not ready (chunks). virtualPos=({}, {}, {})",
                                        shortId(updated.id()),
                                        round1(updated.pos().x), round1(updated.pos().y), round1(updated.pos().z));
                            }
                        }
                    } else {
                        // Not active; ensure we don't keep chunks loaded.
                        if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                            if (inactiveChunkReleaseDone.add(updated.id())) {
                                if (debug) {
                                    LOGGER.debug("[Sky-Islands][dragons][manager] id={} inactive (no nearby players) release chunks", shortId(updated.id()));
                                }
                                chunkPreloader.release(world, updated.id());
                            }
                        }
                    }
                } else {
                    inactiveChunkReleaseDone.remove(updated.id());
                    // When loaded, use the real entity position as the authoritative position.
                    // Keep the stored Y as the "roaming altitude" so despawn/respawn doesn't drift vertically.
                    Vec3d entityPosForChecks = new Vec3d(dragon.getX(), updated.pos().y, dragon.getZ());
                    boolean playerNearLoaded = isAnyPlayerNear(world, entityPosForChecks, getActivationRadiusBlocks());
                    updated = updated.withPos(entityPosForChecks, serverTicks);

                    // If a passive dragon is loaded but hasn't moved for ~5 seconds, force a phase
                    // transition to kick it out of a stalled HOLDING_PATTERN state.
                    boolean provoked = dragon instanceof DragonProvokedAccess access && access.skyIslands$isProvoked();

                    if (debug && (serverTicks % 100L) == 0L) {
                        LOGGER.debug("[Sky-Islands][dragons][manager] id={} loaded uuid={} pos=({}, {}, {}) provoked={} phase={}",
                                shortId(updated.id()),
                                dragon.getUuidAsString(),
                                round1(dragon.getX()), round1(dragon.getY()), round1(dragon.getZ()),
                                provoked,
                                dragon.getPhaseManager().getCurrent().getType());
                    }

                    // Sync heading from actual entity travel direction only while provoked.
                    // For passive roaming we keep the virtual heading stable so it doesn't slowly drift/curve.
                    double hxFromEntity = updated.headingX();
                    double hzFromEntity = updated.headingZ();
                    if (provoked) {
                        Vec3d v = dragon.getVelocity();
                        double hlen = Math.sqrt(v.x * v.x + v.z * v.z);
                        if (hlen > 1.0e-3) {
                            hxFromEntity = v.x / hlen;
                            hzFromEntity = v.z / hlen;
                            if (debug && (serverTicks % 100L) == 0L) {
                                LOGGER.debug("[Sky-Islands][dragons][manager] id={} syncHeadingFromVelocity heading=({}, {}) vel=({}, {}, {})",
                                        shortId(updated.id()),
                                        round2(hxFromEntity), round2(hzFromEntity),
                                        round2(v.x), round2(v.y), round2(v.z));
                            }
                        }
                    }
                    if (!provoked) {
                        long pendingKick = loadedPendingTakeoffKickTick.getOrDefault(updated.id(), 0L);
                        if (pendingKick > 0L && serverTicks >= pendingKick) {
                            dragon.getPhaseManager().setPhase(PhaseType.TAKEOFF);
                            loadedPendingTakeoffKickTick.remove(updated.id());
                        }

                        long graceUntil = loadedSpawnGraceUntilTick.getOrDefault(updated.id(), 0L);
                        if (serverTicks < graceUntil) {
                            loadedStuckTicks.put(updated.id(), 0);
                            lastLoadedEntityPos.put(updated.id(), new Vec3d(dragon.getX(), 0.0, dragon.getZ()));
                        } else {
                            Vec3d last = lastLoadedEntityPos.get(updated.id());
                            if (last != null) {
                                double dx = dragon.getX() - last.x;
                                double dz = dragon.getZ() - last.z;
                                double movedSq = dx * dx + dz * dz;
                                if (movedSq < 0.04) {
                                    int stuck = loadedStuckTicks.getOrDefault(updated.id(), 0) + 1;
                                    loadedStuckTicks.put(updated.id(), stuck);
                                    if (debug && stuck % 40 == 0) {
                                        LOGGER.debug("[Sky-Islands][dragons][manager] id={} stuckTicks={} movedSq={}",
                                                shortId(updated.id()), stuck, round2(movedSq));
                                    }
                                    if (stuck == 100) {
                                        PhaseType<?> current = dragon.getPhaseManager().getCurrent().getType();
                                        // Some overworld TAKEOFF states can stall; briefly toggle phases to
                                        // force vanilla to recompute movement, then return to TAKEOFF.
                                        dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);
                                        loadedPendingTakeoffKickTick.put(updated.id(), serverTicks + 1);

                                        // Also apply a small initial push so we don't depend on the phase
                                        // immediately producing motion.
                                        Vec3d pushDir = new Vec3d(updated.headingX(), 0.0, updated.headingZ());
                                        double plen = pushDir.length();
                                        if (plen > 1.0e-6) {
                                            pushDir = pushDir.multiply(1.0 / plen);
                                        } else {
                                            pushDir = new Vec3d(1, 0, 0);
                                        }
                                        dragon.setVelocity(pushDir.x * 0.35, 0.05, pushDir.z * 0.35);

                                        nextHeadingNudgeTick.put(updated.id(), serverTicks);

                                        LOGGER.info("[Sky-Islands] Dragon id={} stuck while loaded; forcing phase {} -> {} at entityPos=({}, {}, {})",
                                                shortId(updated.id()),
                                                current, PhaseType.TAKEOFF,
                                                round1(dragon.getX()), round1(updated.pos().y), round1(dragon.getZ()));
                                    }
                                } else {
                                    if (debug && loadedStuckTicks.getOrDefault(updated.id(), 0) > 0) {
                                        LOGGER.debug("[Sky-Islands][dragons][manager] id={} unstuck movedSq={} (reset)",
                                                shortId(updated.id()), round2(movedSq));
                                    }
                                    loadedStuckTicks.put(updated.id(), 0);
                                }
                            }
                            lastLoadedEntityPos.put(updated.id(), new Vec3d(dragon.getX(), 0.0, dragon.getZ()));
                        }
                    } else {
                        lastLoadedEntityPos.remove(updated.id());
                        loadedStuckTicks.remove(updated.id());
                        loadedPendingTakeoffKickTick.remove(updated.id());
                    }

                    // Apply the same steer-to/away-from-spawn logic while the entity is loaded.
                    Vec3d steered = steerHeading(world, updated.id(), updated.pos(), hxFromEntity, hzFromEntity, Math.min(20, serverTicks - updated.lastTick()));
                    updated = updated.withHeading(steered.x, steered.z, serverTicks);

                    if (debug && (serverTicks % 100L) == 0L) {
                        LOGGER.debug("[Sky-Islands][dragons][manager] id={} headingAfterSteer=({}, {})",
                                shortId(updated.id()),
                                round2(updated.headingX()), round2(updated.headingZ()));
                    }

                    if (config.forceChunkLoadingEnabled && chunkPreloader != null && playerNearLoaded) {
                        Set<ChunkPos> desired = computeDesiredChunks(updated);
                        if (debug && (serverTicks % 100L) == 0L) {
                            LOGGER.debug("[Sky-Islands][dragons][manager] id={} preloadWhileLoaded desiredChunks={} budgetBefore={}",
                                    shortId(updated.id()),
                                    desired.size(),
                                    chunkBudget);
                        }
                        chunkBudget -= chunkPreloader.request(world, updated.id(), desired, serverTicks, chunkBudget);
                    }

                    // If no players are nearby, despawn and continue virtual travel.
                    if (!isAnyPlayerNear(world, entityPosForChecks, getDespawnRadiusBlocks())) {
                        if (debug) {
                            LOGGER.debug("[Sky-Islands][dragons][manager] id={} despawn (no nearby players) entityPos=({}, {}, {}) virtualY={}",
                                    shortId(updated.id()),
                                    round1(dragon.getX()), round1(dragon.getY()), round1(dragon.getZ()),
                                    round1(updated.pos().y));
                        }
                        dragon.discard();
                        dragon = null;
                        loaded.remove(updated.id());

                        lastLoadedEntityPos.remove(updated.id());
                        loadedStuckTicks.remove(updated.id());
                        loadedSpawnGraceUntilTick.remove(updated.id());

                        fearedHeadPos.remove(updated.id());
                        fearedHeadAvoidTarget.remove(updated.id());
                        fearedHeadLockUntilTick.remove(updated.id());
                        fearedHeadNextScanTick.remove(updated.id());

                        LOGGER.info("[Sky-Islands] Despawned managed dragon id={} (no nearby players). virtualPos=({}, {}, {})",
                                shortId(updated.id()),
                                round1(updated.pos().x), round1(updated.pos().y), round1(updated.pos().z));

                        if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                            chunkPreloader.release(world, updated.id());
                            inactiveChunkReleaseDone.add(updated.id());
                        }
                    }
                }
            }

            if (dragon != null) {
                // Keep passive dragons moving roughly in the stored direction by keeping the fight origin ahead.
                if (dragon instanceof DragonProvokedAccess access && !access.skyIslands$isProvoked()) {
                    boolean didHeadFear = false;
                    if (config.headFearEnabled) {
                        didHeadFear = applyHeadFearOrbit(world, updated.id(), dragon, updated);
                    }

                    if (didHeadFear) {
                        if (debug && (serverTicks % 40L) == 0L) {
                            LOGGER.debug("[Sky-Islands][dragons][manager] id={} headFear active", shortId(updated.id()));
                        }
                        // Head fear overrides the generic roaming-origin nudge.
                        virtualStore.upsert(updated);
                        continue;
                    }

                    long nextTick = nextHeadingNudgeTick.getOrDefault(updated.id(), 0L);
                    if (serverTicks >= nextTick) {
                        int flightY = (int) Math.floor(updated.pos().y);
                        BlockPos ahead = new BlockPos(
                                (int) Math.floor(updated.pos().x + updated.headingX() * 512.0),
                                flightY,
                                (int) Math.floor(updated.pos().z + updated.headingZ() * 512.0)
                        );
                        dragon.setFightOrigin(ahead);
                        // Avoid HOLDING_PATTERN's inherent orbit; keep passive roaming in TAKEOFF.
                        if (dragon.getPhaseManager().getCurrent().getType() == PhaseType.HOLDING_PATTERN) {
                            dragon.getPhaseManager().setPhase(PhaseType.TAKEOFF);
                        }
                        nextHeadingNudgeTick.put(updated.id(), serverTicks + 20);

                        if (debug && (serverTicks % 200L) == 0L) {
                            LOGGER.debug("[Sky-Islands][dragons][manager] id={} nudge fightOrigin=({}, {}, {}) heading=({}, {})",
                                    shortId(updated.id()),
                                    ahead.getX(), ahead.getY(), ahead.getZ(),
                                    round2(updated.headingX()), round2(updated.headingZ()));
                        }
                    }
                }
            }

            virtualStore.upsert(updated);
        }

        if (serverTicks % 600 == 0) {
            int loadedCount = loaded.size();
            int virtualCount = virtualStore.size();
            LOGGER.info("[Sky-Islands] Dragons summary: virtual={} loaded={} (activationRadius={} despawnRadius={})",
                virtualCount, loadedCount, getActivationRadiusBlocks(), getDespawnRadiusBlocks());

            int shown = 0;
            for (VirtualDragonStore.VirtualDragonState s : snapshot) {
                if (shown >= 5) {
                    break;
                }

                EnderDragonEntity loadedDragon = loaded.get(s.id());
                String phase = loadedDragon != null ? String.valueOf(loadedDragon.getPhaseManager().getCurrent().getType()) : "<virtual>";
                boolean provoked = loadedDragon instanceof DragonProvokedAccess access && access.skyIslands$isProvoked();

                LOGGER.info("[Sky-Islands]  - id={} virtualPos=({}, {}, {}) heading=({}, {})",
                        shortId(s.id()),
                        round1(s.pos().x), round1(s.pos().y), round1(s.pos().z),
                        round2(s.headingX()), round2(s.headingZ()));

                if (loadedDragon != null) {
                    LOGGER.info("[Sky-Islands]    loaded entityPos=({}, {}, {}) phase={} provoked={}",
                            round1(loadedDragon.getX()), round1(loadedDragon.getY()), round1(loadedDragon.getZ()),
                            phase, provoked);
                }
                shown++;
            }
        }

        if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
            chunkPreloader.releaseUnused(world, serverTicks, config.releaseTicketsAfterTicks);
        }
    }

    private static VirtualDragonStore.VirtualDragonState advanceVirtualState(ServerWorld world, VirtualDragonStore.VirtualDragonState state) {
        if (!config.virtualTravelEnabled) {
            return state;
        }

        final boolean debug = LOGGER.isDebugEnabled();

        long dt = Math.max(1, serverTicks - state.lastTick());
        long dtForTurn = Math.min(dt, 20);

        // First, update heading based on roam constraints.
        Vec3d steered = steerHeading(world, state.id(), state.pos(), state.headingX(), state.headingZ(), dtForTurn);
        double hx = steered.x;
        double hz = steered.z;

        Vec3d pos = state.pos();
        Vec3d moved = new Vec3d(
                pos.x + state.headingX() * config.virtualSpeedBlocksPerTick * dt,
                pos.y,
                pos.z + state.headingZ() * config.virtualSpeedBlocksPerTick * dt
        );

        // Bounce off world border by flipping heading when out of bounds.
        double minX = world.getWorldBorder().getBoundWest() + 64;
        double maxX = world.getWorldBorder().getBoundEast() - 64;
        double minZ = world.getWorldBorder().getBoundNorth() + 64;
        double maxZ = world.getWorldBorder().getBoundSouth() - 64;

        // Use the steered heading for movement.
        moved = new Vec3d(
            pos.x + hx * config.virtualSpeedBlocksPerTick * dt,
            pos.y,
            pos.z + hz * config.virtualSpeedBlocksPerTick * dt
        );

        if (moved.x < minX || moved.x > maxX) {
            hx = -hx;
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} bounceX posX={} bounds=[{}, {}]", shortId(state.id()), round1(moved.x), round1(minX), round1(maxX));
            }
        }
        if (moved.z < minZ || moved.z > maxZ) {
            hz = -hz;
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} bounceZ posZ={} bounds=[{}, {}]", shortId(state.id()), round1(moved.z), round1(minZ), round1(maxZ));
            }
        }

        // Optional gentle direction changes only when not actively steering.
        // Default config disables this so roaming stays in a straight line.
        if (config.directionJitterEnabled && !returnTargetHeading.containsKey(state.id())) {
            int interval = config.directionChangeIntervalTicks;
            long offset = positiveMod(mix64(state.id()), interval);
            if ((serverTicks + offset) % interval == 0) {
                Random r = new Random(mix64(state.id()) ^ serverTicks);
                double delta = (r.nextDouble() - 0.5) * 0.35;
            double cos = Math.cos(delta);
            double sin = Math.sin(delta);
            double nhx = hx * cos - hz * sin;
            double nhz = hx * sin + hz * cos;
            hx = nhx;
            hz = nhz;

            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} directionJitter deltaRad={} heading=({}, {})",
                        shortId(state.id()), round2(delta), round2(hx), round2(hz));
            }
            }
        }

        VirtualDragonStore.VirtualDragonState updated = state.withPos(new Vec3d(
                clamp(moved.x, minX, maxX),
                moved.y,
                clamp(moved.z, minZ, maxZ)
        ), serverTicks);

        return updated.withHeading(hx, hz, serverTicks);
    }

    private static Vec3d steerHeading(ServerWorld world, UUID id, Vec3d pos, double hx, double hz, long dtForTurn) {
        final boolean debug = LOGGER.isDebugEnabled();
        BlockPos spawn = world.getLevelProperties().getSpawnPoint() != null
                ? world.getLevelProperties().getSpawnPoint().getPos()
                : BlockPos.ORIGIN;

        double dxFromSpawn = pos.x - (spawn.getX() + 0.5);
        double dzFromSpawn = pos.z - (spawn.getZ() + 0.5);
        double distSq = dxFromSpawn * dxFromSpawn + dzFromSpawn * dzFromSpawn;

        double minDist = (double) config.roamMinDistanceBlocks;
        double maxDist = (double) config.roamMaxDistanceBlocks;

        boolean tooClose = distSq < minDist * minDist;
        boolean tooFar = distSq > maxDist * maxDist;

        if (!tooClose && !tooFar) {
            // In the safe band.
            if (debug && returnTargetHeading.containsKey(id)) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} steer safeBand clearReturnTarget distSq={}", shortId(id), round1(distSq));
            }
            returnTargetHeading.remove(id);
            returnTurnUntilTick.remove(id);
            return new Vec3d(hx, 0.0, hz);
        }

        // Decide (occasionally) on a stable target heading to avoid "bouncing".
        long nextDecision = nextReturnDecisionTick.getOrDefault(id, 0L);
        long until = returnTurnUntilTick.getOrDefault(id, 0L);

        if (serverTicks >= nextDecision || serverTicks >= until || !returnTargetHeading.containsKey(id)) {
            double tx;
            double tz;

            // If too far, head toward spawn. If too close, head away from spawn.
            if (tooFar) {
                tx = -dxFromSpawn;
                tz = -dzFromSpawn;
            } else {
                tx = dxFromSpawn;
                tz = dzFromSpawn;
            }

            double len = Math.sqrt(tx * tx + tz * tz);
            if (len < 1.0e-6) {
                tx = 1;
                tz = 0;
                len = 1;
            }
            tx /= len;
            tz /= len;

            // Random acute-ish offset: not a direct line, not a U-turn.
            double offset = 0.35 + world.getRandom().nextDouble() * 0.75; // ~20° to ~63°
            if (world.getRandom().nextBoolean()) {
                offset = -offset;
            }

            double cos = Math.cos(offset);
            double sin = Math.sin(offset);
            double rtx = tx * cos - tz * sin;
            double rtz = tx * sin + tz * cos;

            returnTargetHeading.put(id, new Vec3d(rtx, 0.0, rtz));
            returnTurnUntilTick.put(id, serverTicks + 20 * 20);
            nextReturnDecisionTick.put(id, serverTicks + 20 * 15);

            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} steer decide {} dist={} target=({}, {}) untilTick={} nextDecisionTick={}",
                        shortId(id),
                        (tooFar ? "tooFar" : "tooClose"),
                        round1(Math.sqrt(distSq)),
                        round2(rtx), round2(rtz),
                        returnTurnUntilTick.get(id),
                        nextReturnDecisionTick.get(id));
            }
        }

        Vec3d target = returnTargetHeading.get(id);
        if (target == null) {
            return new Vec3d(hx, 0.0, hz);
        }

        // Keep virtual heading changes slow and smooth. Otherwise, an unload -> virtual -> reload cycle
        // can appear to "snap" the dragon 90°+ if it hits the roam bounds while virtual.
        return rotateHeadingToward(hx, hz, target.x, target.z, 0.005 * Math.max(1, dtForTurn));
    }

    private static Vec3d rotateHeadingToward(double hx, double hz, double tx, double tz, double maxDeltaRadians) {
        double curA = Math.atan2(hz, hx);
        double tarA = Math.atan2(tz, tx);
        double diff = wrapToPi(tarA - curA);

        double step = clamp(diff, -maxDeltaRadians, maxDeltaRadians);
        double newA = curA + step;
        return new Vec3d(Math.cos(newA), 0.0, Math.sin(newA));
    }

    private static double wrapToPi(double a) {
        while (a <= -Math.PI) {
            a += Math.PI * 2.0;
        }
        while (a > Math.PI) {
            a -= Math.PI * 2.0;
        }
        return a;
    }

    private static long mix64(UUID id) {
        long z = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        z ^= (z >>> 33);
        z *= 0xff51afd7ed558ccdL;
        z ^= (z >>> 33);
        z *= 0xc4ceb9fe1a85ec53L;
        z ^= (z >>> 33);
        return z;
    }

    private static long positiveMod(long v, long m) {
        if (m <= 0) {
            return 0;
        }
        long r = v % m;
        return r < 0 ? (r + m) : r;
    }

    private static boolean isAnyPlayerNear(ServerWorld world, Vec3d pos, int radiusBlocks) {
        double radiusSq = (double) radiusBlocks * (double) radiusBlocks;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(pos) <= radiusSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSpawnChunkLoaded(ServerWorld world, Vec3d pos) {
        BlockPos bp = BlockPos.ofFloored(pos);
        return world.isChunkLoaded(bp);
    }

    private static boolean isSpawnReady(ServerWorld world, VirtualDragonStore.VirtualDragonState state) {
        if (!config.forceChunkLoadingEnabled || chunkPreloader == null) {
            return isSpawnChunkLoaded(world, state.pos());
        }

        ChunkPos center = new ChunkPos(BlockPos.ofFloored(state.pos()));
        return chunkPreloader.isChunkLoaded(world, center);
    }

    private static VirtualDragonStore.VirtualDragonState rewindIfTooCloseToPlayers(ServerWorld world, VirtualDragonStore.VirtualDragonState state) {
        int minAllowed = getMinSpawnDistanceBlocks();
        if (minAllowed <= 0) {
            return state;
        }

        final boolean debug = LOGGER.isDebugEnabled();

        double minSq = minSquaredDistanceToAnyPlayer(world, state.pos());
        if (minSq == Double.POSITIVE_INFINITY) {
            return state;
        }

        double min = Math.sqrt(minSq);
        if (min >= minAllowed) {
            return state;
        }

        // Move the spawn point backward along the tracked heading so we can spawn it outside the player's view,
        // while keeping the same "incoming" direction.
        double shiftBack = (minAllowed - min) + 32.0;

        Vec3d pos = state.pos();
        Vec3d rewound = new Vec3d(
                pos.x - state.headingX() * shiftBack,
                pos.y,
                pos.z - state.headingZ() * shiftBack
        );

        double minX = world.getWorldBorder().getBoundWest() + 64;
        double maxX = world.getWorldBorder().getBoundEast() - 64;
        double minZ = world.getWorldBorder().getBoundNorth() + 64;
        double maxZ = world.getWorldBorder().getBoundSouth() - 64;

        rewound = new Vec3d(
                clamp(rewound.x, minX, maxX),
                rewound.y,
                clamp(rewound.z, minZ, maxZ)
        );

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] id={} rewindSpawn minDist={} minAllowed={} shiftBack={} newPos=({}, {}, {})",
                shortId(state.id()),
                round1(min),
                minAllowed,
                round1(shiftBack),
                round1(rewound.x), round1(rewound.y), round1(rewound.z));
        }

        return state.withPos(rewound, serverTicks);
    }

    private static double minSquaredDistanceToAnyPlayer(ServerWorld world, Vec3d pos) {
        double min = Double.POSITIVE_INFINITY;
        for (ServerPlayerEntity player : world.getPlayers()) {
            double d = player.squaredDistanceTo(pos);
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    private static Set<ChunkPos> computeDesiredChunks(VirtualDragonStore.VirtualDragonState state) {
        Set<ChunkPos> chunks = new HashSet<>();

        ChunkPos center = new ChunkPos(BlockPos.ofFloored(state.pos()));
        int r = config.preloadRadiusChunks;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                chunks.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        for (int i = 1; i <= config.preloadAheadChunks; i++) {
            double aheadBlocks = i * 16.0;
            int ax = (int) Math.floor(state.pos().x + state.headingX() * aheadBlocks);
            int az = (int) Math.floor(state.pos().z + state.headingZ() * aheadBlocks);
            chunks.add(new ChunkPos(BlockPos.ofFloored(ax, 0, az)));
        }

        return chunks;
    }

    private static int getActivationRadiusBlocks() {
        return effectiveActivationRadiusBlocks > 0 ? effectiveActivationRadiusBlocks : config.activationRadiusBlocks;
    }

    private static int getDespawnRadiusBlocks() {
        return effectiveDespawnRadiusBlocks > 0 ? effectiveDespawnRadiusBlocks : config.despawnRadiusBlocks;
    }

    private static int getMinSpawnDistanceBlocks() {
        return effectiveMinSpawnDistanceBlocks >= 0 ? effectiveMinSpawnDistanceBlocks : config.minSpawnDistanceBlocks;
    }

    private static void updateEffectiveDistancesFromServerSettings(MinecraftServer server) {
        if (server == null || config == null) {
            return;
        }

        if (!config.autoDistancesFromServer) {
            if (!effectiveDistancesInitialized) {
                effectiveActivationRadiusBlocks = config.activationRadiusBlocks;
                effectiveDespawnRadiusBlocks = config.despawnRadiusBlocks;
                effectiveMinSpawnDistanceBlocks = config.minSpawnDistanceBlocks;
                effectiveDistancesInitialized = true;
            }
            return;
        }

        // Compute once per startup (these settings don't change often and we don't want per-tick churn).
        if (effectiveDistancesInitialized) {
            return;
        }

        int viewChunks = server.getPlayerManager().getViewDistance();
        int simChunks = server.getPlayerManager().getSimulationDistance();

        int viewBlocks = Math.max(0, viewChunks) * 16;
        int simBlocks = Math.max(0, simChunks) * 16;

        // Entity load radius: use simulation distance (when entities normally tick for players).
        // Despawn radius: add a small hysteresis so we don't flap on the boundary.
        effectiveActivationRadiusBlocks = Math.max(64, simBlocks);
        effectiveDespawnRadiusBlocks = Math.max(effectiveActivationRadiusBlocks + 64, (simChunks + 4) * 16);

        // Spawn rewind distance: use view distance so spawns happen outside view (avoid pop-in).
        effectiveMinSpawnDistanceBlocks = Math.max(0, viewBlocks);

        effectiveDistancesInitialized = true;

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons][manager] autoDistancesFromServer enabled viewChunks={} simChunks={} activation={} despawn={} minSpawnDistance={}",
                    viewChunks,
                    simChunks,
                    effectiveActivationRadiusBlocks,
                    effectiveDespawnRadiusBlocks,
                    effectiveMinSpawnDistanceBlocks);
        }
    }

    private static EnderDragonEntity spawnDragonFromVirtual(ServerWorld world, VirtualDragonStore.VirtualDragonState state) {
        final boolean debug = LOGGER.isDebugEnabled();
        // Safety: avoid ever having 2 loaded entities for the same internal id.
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof EnderDragonEntity existing
                    && isManaged(existing)
                    && DragonIdTags.getId(existing).isPresent()
                    && DragonIdTags.getId(existing).get().equals(state.id())) {
                if (debug) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] id={} spawn skip (already loaded) uuid={} pos=({}, {}, {})",
                            shortId(state.id()),
                            existing.getUuidAsString(),
                            round1(existing.getX()), round1(existing.getY()), round1(existing.getZ()));
                }
                return existing;
            }
        }

        // If chunk tickets load an old/unmanaged dragon from disk (or another mod spawns one), it can look
        // like "an unmanaged dragon spawned" exactly when we materialize a managed virtual dragon.
        // Sky-Islands only intentionally spawns managed dragons, so proactively discard unmanaged dragons
        // near the intended spawn location.
        Vec3d spawnPos = state.pos();
        if (config.headFearEnabled) {
            spawnPos = pushPosOutOfHeadExclusion(world, spawnPos);
        }
        cleanupUnmanagedDragonsNear(world, spawnPos, Math.max(96.0, getActivationRadiusBlocks()));

        EnderDragonEntity dragon = EntityType.ENDER_DRAGON.create(world, SpawnReason.EVENT);
        if (dragon == null) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} spawn failed (create returned null)", shortId(state.id()));
            }
            return null;
        }

        // Never spawn inside a dragon-head orbit exclusion zone.
        if (config.headFearEnabled) {
            Vec3d before = state.pos();
            if (debug && (before.x != spawnPos.x || before.z != spawnPos.z)) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} spawn pushed out of headExclusion from=({}, {}, {}) to=({}, {}, {})",
                        shortId(state.id()),
                        round1(before.x), round1(before.y), round1(before.z),
                        round1(spawnPos.x), round1(spawnPos.y), round1(spawnPos.z));
            }
        }

        dragon.refreshPositionAndAngles(spawnPos.x, spawnPos.y, spawnPos.z, 0.0f, 0.0f);
        dragon.addCommandTag(MANAGED_TAG);
        dragon.addCommandTag(DragonIdTags.toTag(state.id()));

        BlockPos origin = BlockPos.ofFloored(spawnPos);
        dragon.setFightOrigin(origin);
        dragon.getPhaseManager().setPhase(PhaseType.HOLDING_PATTERN);

        if (!world.spawnEntity(dragon)) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} spawn failed (spawnEntity=false) pos=({}, {}, {})",
                        shortId(state.id()),
                        round1(spawnPos.x), round1(spawnPos.y), round1(spawnPos.z));
            }
            return null;
        }

        LOGGER.info("[Sky-Islands] Spawned managed dragon id={} uuid={} at ({}, {}, {})",
            shortId(state.id()),
            dragon.getUuidAsString(),
            round1(spawnPos.x), round1(spawnPos.y), round1(spawnPos.z));

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] id={} spawn ok tags={} phase={} fightOrigin={}",
                    shortId(state.id()),
                    dragon.getCommandTags().size(),
                    dragon.getPhaseManager().getCurrent().getType(),
                    dragon.getFightOrigin());
        }

        nextHeadingNudgeTick.put(state.id(), serverTicks + 1);
        lastLoadedEntityPos.remove(state.id());
        loadedStuckTicks.put(state.id(), 0);
        loadedSpawnGraceUntilTick.put(state.id(), serverTicks + 100);
        return dragon;
    }

    private static void cleanupUnmanagedDragonsNear(ServerWorld world, Vec3d center, double radiusBlocks) {
        if (world == null || center == null || radiusBlocks <= 0) {
            return;
        }

        double r2 = radiusBlocks * radiusBlocks;
        int removed = 0;

        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof EnderDragonEntity dragon)) {
                continue;
            }
            if (isManaged(dragon)) {
                continue;
            }

            double dx = dragon.getX() - center.x;
            double dy = dragon.getY() - center.y;
            double dz = dragon.getZ() - center.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 > r2) {
                continue;
            }

            removed++;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][dragons][manager] discarded unmanaged dragon uuid={} nearSpawn=({}, {}, {}) d2={}",
                        dragon.getUuidAsString(),
                        round1(center.x), round1(center.y), round1(center.z),
                        round1(d2));
            }
            dragon.discard();
        }

        if (removed > 0) {
            LOGGER.warn("[Sky-Islands] Discarded {} unmanaged Ender Dragon(s) near managed spawn at ({}, {}, {}).",
                    removed,
                    round1(center.x), round1(center.y), round1(center.z));
        }
    }

    private static BlockPos pickSpawnPos(ServerWorld world) {
        final boolean debug = LOGGER.isDebugEnabled();
        WorldProperties.SpawnPoint spawnPoint = world.getLevelProperties().getSpawnPoint();
        BlockPos spawn = spawnPoint != null ? spawnPoint.getPos() : BlockPos.ORIGIN;
        int inner = Math.max(0, config.roamMinDistanceBlocks);
        int outer = Math.max(inner + 1, config.roamMaxDistanceBlocks);

        int y = config.spawnY;
        if (config.spawnYRandomRange > 0) {
            y = config.spawnY + world.getRandom().nextBetween(-config.spawnYRandomRange, config.spawnYRandomRange);
        }

        double minX = world.getWorldBorder().getBoundWest() + 64;
        double maxX = world.getWorldBorder().getBoundEast() - 64;
        double minZ = world.getWorldBorder().getBoundNorth() + 64;
        double maxZ = world.getWorldBorder().getBoundSouth() - 64;

        for (int attempt = 0; attempt < 16; attempt++) {
            double theta = world.getRandom().nextDouble() * (Math.PI * 2.0);
            double r = Math.sqrt(world.getRandom().nextDouble() * ((double) outer * (double) outer - (double) inner * (double) inner) + (double) inner * (double) inner);

            int x = (int) Math.round(spawn.getX() + (Math.cos(theta) * r));
            int z = (int) Math.round(spawn.getZ() + (Math.sin(theta) * r));
            x = (int) Math.round(clamp(x, minX, maxX));
            z = (int) Math.round(clamp(z, minZ, maxZ));
            BlockPos candidate = new BlockPos(x, y, z);
            if (!config.headFearEnabled) {
                if (debug) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] pickSpawnPos candidate=({}, {}, {}) (headFear disabled)", x, y, z);
                }
                return candidate;
            }

            Vec3d pos = new Vec3d(candidate.getX() + 0.5, candidate.getY() + 0.5, candidate.getZ() + 0.5);
            if (!isInHeadExclusionZone(world, pos)) {
                if (debug) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] pickSpawnPos ok candidate=({}, {}, {})", x, y, z);
                }
                return candidate;
            }

            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] pickSpawnPos reject headExclusion candidate=({}, {}, {})", x, y, z);
            }
        }

        // Fallback: return a candidate; spawn will still be pushed out of exclusion zones if needed.
        double theta = world.getRandom().nextDouble() * (Math.PI * 2.0);
        double r = Math.sqrt(world.getRandom().nextDouble() * ((double) outer * (double) outer - (double) inner * (double) inner) + (double) inner * (double) inner);
        int x = (int) Math.round(spawn.getX() + (Math.cos(theta) * r));
        int z = (int) Math.round(spawn.getZ() + (Math.sin(theta) * r));
        x = (int) Math.round(clamp(x, minX, maxX));
        z = (int) Math.round(clamp(z, minZ, maxZ));
        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] pickSpawnPos fallback candidate=({}, {}, {})", x, y, z);
        }
        return new BlockPos(x, y, z);
    }

    private static boolean applyHeadFearOrbit(ServerWorld world, UUID id, EnderDragonEntity dragon, VirtualDragonStore.VirtualDragonState state) {
        final boolean debug = LOGGER.isDebugEnabled();
        BlockPos currentHead = fearedHeadPos.get(id);
        Vec3d currentTarget = fearedHeadAvoidTarget.get(id);

        if (currentHead != null && (headTracker == null || !headTracker.isStillHead(world, currentHead))) {
            currentHead = null;
            fearedHeadPos.remove(id);
            fearedHeadAvoidTarget.remove(id);
            currentTarget = null;
        }

        long nextScan = fearedHeadNextScanTick.getOrDefault(id, 0L);
        boolean canRescan = serverTicks >= nextScan;

        if (canRescan) {
            BlockPos nearCenter = dragon.getBlockPos();
            BlockPos aheadCenter = nearCenter;
            if (config.headScanAheadBlocks > 0) {
                int sx = (int) Math.floor(dragon.getX() + state.headingX() * (double) config.headScanAheadBlocks);
                int sz = (int) Math.floor(dragon.getZ() + state.headingZ() * (double) config.headScanAheadBlocks);
                aheadCenter = new BlockPos(sx, dragon.getBlockY(), sz);
            }

            HeadScan near = scanDragonHeadsAround(world, nearCenter, config.headSearchRadiusBlocks, config.headSearchRadiusBlocks, 64);
            HeadScan ahead = aheadCenter.equals(nearCenter)
                    ? new HeadScan(null, List.of())
                    : scanDragonHeadsAround(world, aheadCenter, config.headSearchRadiusBlocks, config.headSearchRadiusBlocks, 64);

            List<BlockPos> merged = mergeHeadLists(near.heads, ahead.heads, 96);
            fearedHeadNextScanTick.put(id, serverTicks + config.headScanIntervalTicks);

            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} headScan nearCenter={} aheadCenter={} nearHeads={} aheadHeads={} mergedHeads={}",
                        shortId(id),
                        nearCenter,
                        aheadCenter,
                        near.heads.size(),
                        ahead.heads.size(),
                        merged.size());
            }

            Vec3d dragonPos = new Vec3d(dragon.getX(), dragon.getY(), dragon.getZ());
            Vec3d heading = new Vec3d(state.headingX(), 0.0, state.headingZ());
            BlockPos threat = chooseThreatHead(dragonPos, heading, merged);

            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} headScan threat={} currentHead={}",
                        shortId(id),
                        threat,
                        currentHead);
            }

            if (threat != null) {
                boolean same = threat.equals(currentHead);
                long lockUntil = fearedHeadLockUntilTick.getOrDefault(id, 0L);
                if (currentHead == null || (serverTicks >= lockUntil && !same)) {
                    Vec3d target = computeBypassTarget(world, threat, heading, merged);
                    if (target != null) {
                        fearedHeadPos.put(id, threat);
                        fearedHeadAvoidTarget.put(id, target);
                        fearedHeadLockUntilTick.put(id, serverTicks + config.headOrbitSwitchCooldownTicks);
                        currentHead = threat;
                        currentTarget = target;

                        if (debug) {
                            LOGGER.debug("[Sky-Islands][dragons][manager] id={} headAvoid set head={} target=({}, {}) lockUntil={}",
                                    shortId(id),
                                    threat,
                                    round1(target.x), round1(target.z),
                                    fearedHeadLockUntilTick.get(id));
                        }
                    }
                }
            }
        }

        if (currentHead == null) {
            return false;
        }

        // If we have an active bypass target, steer to it until we've passed the head.
        if (currentTarget == null) {
            // Should be rare; clear and fall back to normal roaming.
            fearedHeadPos.remove(id);
            return false;
        }

        Vec3d heading = new Vec3d(state.headingX(), 0.0, state.headingZ());
        Vec3d dragonPos = new Vec3d(dragon.getX(), dragon.getY(), dragon.getZ());
        double exclusion = (double) config.headOrbitRadiusBlocks + (double) config.headAvoidSpawnBufferBlocks;

        if (hasPassedHead(dragonPos, heading, currentHead, exclusion)) {
            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] id={} headAvoid done passedHead={} exclusion={}",
                        shortId(id), currentHead, round1(exclusion));
            }
            fearedHeadPos.remove(id);
            fearedHeadAvoidTarget.remove(id);
            return false;
        }

        double oy = Math.max(state.pos().y, (double) currentHead.getY() + (double) config.headOrbitYAboveHeadBlocks);
        BlockPos origin = BlockPos.ofFloored(currentTarget.x, oy, currentTarget.z);
        dragon.setFightOrigin(origin);
        nextHeadingNudgeTick.put(id, serverTicks + 10);

        if (debug && (serverTicks % 20L) == 0L) {
            LOGGER.debug("[Sky-Islands][dragons][manager] id={} headAvoid origin=({}, {}, {}) head={} target=({}, {})",
                    shortId(id),
                    origin.getX(), origin.getY(), origin.getZ(),
                    currentHead,
                    round1(currentTarget.x), round1(currentTarget.z));
        }
        return true;
    }

    private static boolean hasPassedHead(Vec3d dragonPos, Vec3d heading, BlockPos head, double exclusionRadius) {
        final boolean trace = LOGGER.isTraceEnabled();
        Vec3d h = new Vec3d(heading.x, 0.0, heading.z);
        double hLen = Math.sqrt(h.x * h.x + h.z * h.z);
        if (hLen < 1.0e-6) {
            return false;
        }
        h = new Vec3d(h.x / hLen, 0.0, h.z / hLen);

        double cx = head.getX() + 0.5;
        double cz = head.getZ() + 0.5;
        double dx = dragonPos.x - cx;
        double dz = dragonPos.z - cz;
        double distSq = dx * dx + dz * dz;
        if (distSq < (exclusionRadius * exclusionRadius)) {
            return false;
        }

        double along = dx * h.x + dz * h.z;
        // Consider it "passed" once it's well ahead of the head along the roaming heading.
        boolean passed = along > (exclusionRadius * 0.75);
        if (trace && passed) {
            LOGGER.trace("[Sky-Islands][dragons][manager] headAvoid passed head={} along={} threshold={} distSq={}",
                    head,
                    round1(along),
                    round1(exclusionRadius * 0.75),
                    round1(distSq));
        }
        return passed;
    }

    private static BlockPos chooseThreatHead(Vec3d dragonPos, Vec3d heading, List<BlockPos> heads) {
        final boolean debug = LOGGER.isDebugEnabled();
        if (heads.isEmpty()) {
            return null;
        }

        Vec3d h = new Vec3d(heading.x, 0.0, heading.z);
        double hLen = Math.sqrt(h.x * h.x + h.z * h.z);
        if (hLen < 1.0e-6) {
            return chooseNearestHead(dragonPos, heads);
        }
        h = new Vec3d(h.x / hLen, 0.0, h.z / hLen);

        double exclusion = (double) config.headOrbitRadiusBlocks + (double) config.headAvoidSpawnBufferBlocks;
        double exclusionSq = exclusion * exclusion;

        BlockPos best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (BlockPos p : heads) {
            double cx = p.getX() + 0.5;
            double cz = p.getZ() + 0.5;
            double rx = cx - dragonPos.x;
            double rz = cz - dragonPos.z;

            double along = rx * h.x + rz * h.z;
            double lateralX = rx - along * h.x;
            double lateralZ = rz - along * h.z;
            double lateralSq = lateralX * lateralX + lateralZ * lateralZ;

            // Threat if it's close to our forward path corridor, or we're already inside its exclusion.
            double distSq = rx * rx + rz * rz;
            boolean inside = distSq < exclusionSq;
            boolean nearPath = lateralSq < (exclusionSq * 1.05);
            if (!inside && (!nearPath || along < -exclusion)) {
                continue;
            }

            // Score: prioritize heads in front of us, closest along the path.
            double score = (along >= 0.0 ? along : (exclusion + Math.abs(along))) + Math.sqrt(lateralSq) * 0.25;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }

        if (debug && best != null) {
            LOGGER.debug("[Sky-Islands][dragons][manager] headAvoid chooseThreat best={} score={} totalHeads={}",
                    best,
                    round1(bestScore),
                    heads.size());
        }
        return best;
    }

    private static Vec3d computeBypassTarget(ServerWorld world, BlockPos head, Vec3d heading, List<BlockPos> nearbyHeads) {
        final boolean debug = LOGGER.isDebugEnabled();
        Vec3d h = new Vec3d(heading.x, 0.0, heading.z);
        double hLen = Math.sqrt(h.x * h.x + h.z * h.z);
        if (hLen < 1.0e-6) {
            h = new Vec3d(1, 0, 0);
            hLen = 1;
        }
        h = new Vec3d(h.x / hLen, 0.0, h.z / hLen);

        double cx = head.getX() + 0.5;
        double cz = head.getZ() + 0.5;

        double exclusion = (double) config.headOrbitRadiusBlocks + (double) config.headAvoidSpawnBufferBlocks;
        double avoidR = exclusion;

        Vec3d left = new Vec3d(-h.z, 0.0, h.x);
        Vec3d right = new Vec3d(h.z, 0.0, -h.x);

        // Try both sides and pick the first side that can find a point not inside another head zone.
        Vec3d best = null;
        double bestPenalty = Double.POSITIVE_INFINITY;
        Vec3d[] sides = new Vec3d[]{left, right};

        for (Vec3d side : sides) {
            double baseAngle = Math.atan2(side.z, side.x);
            for (int attempt = 0; attempt < 8; attempt++) {
                double tryAngle = baseAngle + (attempt * 0.35);
                double ox = cx + Math.cos(tryAngle) * exclusion;
                double oz = cz + Math.sin(tryAngle) * exclusion;
                // Add a forward lead so it "wraps" around and continues past, instead of orbiting.
                ox += h.x * (exclusion * 0.75);
                oz += h.z * (exclusion * 0.75);

                boolean blocked = isPointInAnyOtherHeadZone(nearbyHeads, head, ox, oz, avoidR);
                if (!blocked) {
                    if (debug) {
                        LOGGER.debug("[Sky-Islands][dragons][manager] headAvoid bypass ok head={} side=({}, {}) attempt={} target=({}, {})",
                                head,
                                round2(side.x), round2(side.z),
                                attempt,
                                round1(ox), round1(oz));
                    }
                    return new Vec3d(ox, 0.0, oz);
                }

                // Track the least-bad option as a fallback.
                double penalty = nearestOtherHeadDistanceSq(nearbyHeads, head, ox, oz);
                if (penalty < bestPenalty) {
                    bestPenalty = penalty;
                    best = new Vec3d(ox, 0.0, oz);
                }
            }
        }

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] headAvoid bypass fallback head={} target={} bestPenaltySq={} nearbyHeads={}",
                    head,
                    best,
                    (bestPenalty == Double.POSITIVE_INFINITY ? "<inf>" : String.valueOf(round1(bestPenalty))),
                    nearbyHeads.size());
        }
        return best;
    }

    private static double nearestOtherHeadDistanceSq(List<BlockPos> heads, BlockPos chosenHead, double x, double z) {
        double best = Double.POSITIVE_INFINITY;
        for (BlockPos p : heads) {
            if (p.equals(chosenHead)) {
                continue;
            }
            double cx = p.getX() + 0.5;
            double cz = p.getZ() + 0.5;
            double dx = x - cx;
            double dz = z - cz;
            double d2 = dx * dx + dz * dz;
            if (d2 < best) {
                best = d2;
            }
        }
        return best;
    }

    private static boolean isPointInAnyOtherHeadZone(List<BlockPos> heads, BlockPos chosenHead, double x, double z, double avoidRadius) {
        double avoidSq = avoidRadius * avoidRadius;
        for (BlockPos p : heads) {
            if (p.equals(chosenHead)) {
                continue;
            }
            double cx = p.getX() + 0.5;
            double cz = p.getZ() + 0.5;
            double dx = x - cx;
            double dz = z - cz;
            if ((dx * dx + dz * dz) < avoidSq) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInHeadExclusionZone(ServerWorld world, Vec3d pos) {
        final boolean debug = LOGGER.isDebugEnabled();
        HeadScan scan = scanDragonHeadsAround(world, BlockPos.ofFloored(pos), config.headSearchRadiusBlocks, config.headSearchRadiusBlocks, 64);
        if (scan.heads.isEmpty()) {
            return false;
        }

        double avoid = (double) config.headOrbitRadiusBlocks + (double) config.headAvoidSpawnBufferBlocks;
        double avoidSq = avoid * avoid;
        for (BlockPos p : scan.heads) {
            double cx = p.getX() + 0.5;
            double cz = p.getZ() + 0.5;
            double dx = pos.x - cx;
            double dz = pos.z - cz;
            if ((dx * dx + dz * dz) < avoidSq) {
                if (debug) {
                    LOGGER.debug("[Sky-Islands][dragons][manager] headExclusion hit pos=({}, {}, {}) head={} avoidR={}",
                            round1(pos.x), round1(pos.y), round1(pos.z),
                            p,
                            round1(avoid));
                }
                return true;
            }
        }
        return false;
    }

    private static Vec3d pushPosOutOfHeadExclusion(ServerWorld world, Vec3d pos) {
        final boolean debug = LOGGER.isDebugEnabled();
        HeadScan scan = scanDragonHeadsAround(world, BlockPos.ofFloored(pos), config.headSearchRadiusBlocks, config.headSearchRadiusBlocks, 128);
        if (scan.heads.isEmpty()) {
            return pos;
        }

        double avoid = (double) config.headOrbitRadiusBlocks + (double) config.headAvoidSpawnBufferBlocks;
        double avoidSq = avoid * avoid;

        Vec3d adjusted = pos;
        for (int iter = 0; iter < 6; iter++) {
            BlockPos nearest = null;
            double nearestSq = Double.POSITIVE_INFINITY;
            for (BlockPos p : scan.heads) {
                double cx = p.getX() + 0.5;
                double cz = p.getZ() + 0.5;
                double dx = adjusted.x - cx;
                double dz = adjusted.z - cz;
                double d2 = dx * dx + dz * dz;
                if (d2 < nearestSq) {
                    nearestSq = d2;
                    nearest = p;
                }
            }

            if (nearest == null || nearestSq >= avoidSq) {
                break;
            }

            double cx = nearest.getX() + 0.5;
            double cz = nearest.getZ() + 0.5;
            double dx = adjusted.x - cx;
            double dz = adjusted.z - cz;
            double len = Math.sqrt(dx * dx + dz * dz);
            if (len < 1.0e-3) {
                dx = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            dx /= len;
            dz /= len;

            double push = (avoid - len) + 8.0;
            adjusted = new Vec3d(adjusted.x + dx * push, adjusted.y, adjusted.z + dz * push);

            if (debug) {
                LOGGER.debug("[Sky-Islands][dragons][manager] headExclusion push iter={} nearest={} len={} push={} adjusted=({}, {}, {})",
                        iter,
                        nearest,
                        round1(len),
                        round1(push),
                        round1(adjusted.x), round1(adjusted.y), round1(adjusted.z));
            }
        }

        if (debug && (adjusted.x != pos.x || adjusted.z != pos.z)) {
            LOGGER.debug("[Sky-Islands][dragons][manager] headExclusion push done from=({}, {}, {}) to=({}, {}, {}) headsSeen={}",
                    round1(pos.x), round1(pos.y), round1(pos.z),
                    round1(adjusted.x), round1(adjusted.y), round1(adjusted.z),
                    scan.heads.size());
        }

        return adjusted;
    }

    private record HeadScan(BlockPos chosen, List<BlockPos> heads) {
    }

    private static List<BlockPos> mergeHeadLists(List<BlockPos> a, List<BlockPos> b, int limit) {
        final boolean trace = LOGGER.isTraceEnabled();
        if (a.isEmpty()) {
            if (trace && b.size() > limit) {
                LOGGER.trace("[Sky-Islands][dragons][manager] headMerge a=0 b={} limit={} (trunc)", b.size(), limit);
            }
            return b.size() <= limit ? b : b.subList(0, limit);
        }
        if (b.isEmpty()) {
            if (trace && a.size() > limit) {
                LOGGER.trace("[Sky-Islands][dragons][manager] headMerge a={} b=0 limit={} (trunc)", a.size(), limit);
            }
            return a.size() <= limit ? a : a.subList(0, limit);
        }

        java.util.LinkedHashSet<BlockPos> set = new java.util.LinkedHashSet<>(Math.min(limit, a.size() + b.size()));
        for (BlockPos p : a) {
            if (set.size() >= limit) break;
            set.add(p);
        }
        for (BlockPos p : b) {
            if (set.size() >= limit) break;
            set.add(p);
        }

        if (trace) {
            int merged = set.size();
            if (merged >= limit || a.size() + b.size() != merged) {
                LOGGER.trace("[Sky-Islands][dragons][manager] headMerge a={} b={} merged={} limit={}", a.size(), b.size(), merged, limit);
            }
        }

        return java.util.List.copyOf(set);
    }

    private static BlockPos chooseNearestHead(Vec3d dragonPos, List<BlockPos> heads) {
        final boolean trace = LOGGER.isTraceEnabled();
        BlockPos best = null;
        double bestSq = Double.POSITIVE_INFINITY;
        for (BlockPos p : heads) {
            double cx = p.getX() + 0.5;
            double cy = p.getY() + 0.5;
            double cz = p.getZ() + 0.5;
            double dx = dragonPos.x - cx;
            double dy = dragonPos.y - cy;
            double dz = dragonPos.z - cz;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestSq) {
                bestSq = d2;
                best = p;
            }
        }

        if (trace && best != null) {
            LOGGER.trace("[Sky-Islands][dragons][manager] headAvoid chooseNearest best={} distSq={} totalHeads={}",
                    best,
                    round1(bestSq),
                    heads.size());
        }
        return best;
    }

    private static HeadScan scanDragonHeadsAround(ServerWorld world, BlockPos center, int radius, int vertical, int maxFound) {
        if (headTracker == null) {
            return new HeadScan(null, List.of());
        }

        List<BlockPos> found = headTracker.findHeadsNear(world, center, radius, vertical, maxFound);
        if (found.isEmpty()) {
            return new HeadScan(null, found);
        }

        BlockPos chosen = null;
        double bestSq = Double.POSITIVE_INFINITY;

        int cx = center.getX();
        int cy = center.getY();
        int cz = center.getZ();

        for (BlockPos p : found) {
            double dx = (double) (p.getX() - cx);
            double dy = (double) (p.getY() - cy);
            double dz = (double) (p.getZ() - cz);
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < bestSq) {
                bestSq = d2;
                chosen = p;
            }
        }

        return new HeadScan(chosen, found);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, 8);
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static java.util.Optional<UUID> getOrAssignId(EnderDragonEntity dragon) {
        final boolean debug = LOGGER.isDebugEnabled();
        java.util.Optional<UUID> existing = DragonIdTags.getId(dragon);
        if (existing.isPresent()) {
            if (debug && (serverTicks % 1200L) == 0L) {
                UUID id = existing.get();
                LOGGER.debug("[Sky-Islands][dragons][manager] getOrAssignId existing id={} uuid={} managed={} tags={}",
                        shortId(id),
                        dragon.getUuidAsString(),
                        isManaged(dragon),
                        dragon.getCommandTags().size());
            }
            return existing;
        }

        // If a dragon is marked managed but lacks our internal id tag (e.g. old version spawned it),
        // assign one so it participates in loaded accounting and virtual travel.
        UUID id = UUID.randomUUID();
        dragon.addCommandTag(DragonIdTags.toTag(id));

        if (debug) {
            LOGGER.debug("[Sky-Islands][dragons][manager] getOrAssignId assigned id={} uuid={} managed={} tagsNow={}",
                shortId(id),
                dragon.getUuidAsString(),
                isManaged(dragon),
                dragon.getCommandTags().size());
        }

        LOGGER.warn("[Sky-Islands] Managed dragon missing internal id; assigned new id={} uuid={} entityPos=({}, {}, {})",
                shortId(id),
                dragon.getUuidAsString(),
                round1(dragon.getX()), round1(dragon.getY()), round1(dragon.getZ()));

        return java.util.Optional.of(id);
    }
}
