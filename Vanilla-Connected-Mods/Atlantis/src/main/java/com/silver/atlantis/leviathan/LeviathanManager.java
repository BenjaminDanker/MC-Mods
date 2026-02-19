package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.DefaultAttributeRegistry;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.WorldProperties;
import net.minecraft.world.border.WorldBorder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.UUID;

public final class LeviathanManager {
    public static final String MANAGED_TAG = "atlantis_managed_leviathan";

    private static final long MINIMUM_ENSURE_INTERVAL_TICKS = 100L;
    private static final long RECOVERY_INTERVAL_TICKS = 200L;
    private static final double PREDATOR_RETREAT_RADIUS_BLOCKS = 130.0d;
    private static final double PREDATOR_CIRCLE_RADIUS_BLOCKS = 130.0d;
    private static final double PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS = 90.0d;
    private static final double PREDATOR_RETREAT_REACHED_EPSILON_BLOCKS = 14.0d;
    private static final double HEALTH_SCALING_STRENGTH_FROM_ENTITY_SCALE = 0.60d;
    private static final double MIN_HEALTH_SCALING_STRENGTH = 2.0d;
    private static final String SQUID_ENTITY_TYPE_ID = "minecraft:squid";
    private static final String GLOW_SQUID_ENTITY_TYPE_ID = "minecraft:glow_squid";
    private static final double DEFAULT_FISH_SCALE_TYPE_BIAS = 1.50d;
    private static final double SQUID_SCALE_TYPE_BIAS = 0.60d;
    private static final double GLOW_SQUID_SCALE_TYPE_BIAS = 0.60d;

    private static volatile boolean initialized;
    private static LeviathansConfig config;
    private static VirtualLeviathanStore virtualStore;
    private static final Map<String, EntityType<?>> configuredEntityTypesById = new HashMap<>();
    private static List<String> configuredEntityTypeIds = List.of();
    private static LeviathanChunkPreloader chunkPreloader;
    private static final Map<UUID, CombatRuntime> combatRuntimeById = new HashMap<>();
    private static final Set<UUID> inactiveChunkReleaseDone = new LinkedHashSet<>();
    private static final Set<UUID> loadedIdsLastTick = new LinkedHashSet<>();

    private static long serverTicks;
    private static long lastEnsureTick;
    private static long lastRecoveryTick;
    private static int effectiveActivationRadiusBlocks;
    private static int effectiveDespawnRadiusBlocks;
    private static int effectiveMinSpawnDistanceBlocks;
    private static boolean warnedAutoDistanceAdjustment;
    private static int initialServerViewDistanceChunks = -1;

    private enum CombatSubstate {
        ACQUIRE,
        CHARGE,
        PASS_THROUGH,
        TURN_BACK,
        REACQUIRE
    }

    private static final class CombatRuntime {
        private CombatSubstate substate = CombatSubstate.ACQUIRE;
        private UUID targetUuid;
        private Vec3d anchorPos;
        private Vec3d previousPos;
        private Vec3d chargeDirection = new Vec3d(1.0, 0.0, 0.0);
        private long phaseStartTick;
        private long cooldownUntilTick;
        private long invalidLineTicks;
        private long passId;
        private long stalledTicks;
        private long missingTargetUntilTick = -1L;
        private Vec3d passStartPos;
        private Vec3d retreatCenter;
        private Vec3d retreatTarget;
        private long retreatHoldUntilTick;
        private final Map<UUID, Long> lastHitTickByPlayer = new HashMap<>();
        private final Map<UUID, Long> lastHitPassByPlayer = new HashMap<>();

        private void resetToPassive() {
            substate = CombatSubstate.ACQUIRE;
            targetUuid = null;
            anchorPos = null;
            cooldownUntilTick = 0L;
            invalidLineTicks = 0L;
            stalledTicks = 0L;
            missingTargetUntilTick = -1L;
            passStartPos = null;
            retreatCenter = null;
            retreatTarget = null;
            retreatHoldUntilTick = 0L;
        }
    }

    private LeviathanManager() {
    }

    public static void init(LeviathansConfig newConfig, VirtualLeviathanStore newStore) {
        if (newConfig == null) {
            throw new IllegalStateException("LeviathanManager requires non-null config");
        }
        if (newStore == null) {
            throw new IllegalStateException("LeviathanManager requires non-null virtual store");
        }

        config = newConfig;
        virtualStore = newStore;
        applyConfiguredEntityTypes(newConfig);
        chunkPreloader = new LeviathanChunkPreloader(newConfig.preloadTicketLevel);
        effectiveActivationRadiusBlocks = newConfig.activationRadiusBlocks;
        effectiveDespawnRadiusBlocks = newConfig.despawnRadiusBlocks;
        effectiveMinSpawnDistanceBlocks = newConfig.minSpawnDistanceBlocks;
        warnedAutoDistanceAdjustment = false;
        initialServerViewDistanceChunks = -1;
        loadedIdsLastTick.clear();

        if (!initialized) {
            ServerTickEvents.END_SERVER_TICK.register(LeviathanManager::tick);
            initialized = true;
        }

        AtlantisMod.LOGGER.info("[Atlantis][leviathan] manager init entityTypeId={} min={} virtualCount={}",
            config.entityTypeId,
            config.minimumLeviathans,
            virtualStore.size());
    }

    public static void onConfigReload(LeviathansConfig newConfig) {
        config = newConfig;
        applyConfiguredEntityTypes(newConfig);
        chunkPreloader = new LeviathanChunkPreloader(newConfig.preloadTicketLevel);
        inactiveChunkReleaseDone.clear();
        effectiveActivationRadiusBlocks = newConfig.activationRadiusBlocks;
        effectiveDespawnRadiusBlocks = newConfig.despawnRadiusBlocks;
        effectiveMinSpawnDistanceBlocks = newConfig.minSpawnDistanceBlocks;
        warnedAutoDistanceAdjustment = false;
        loadedIdsLastTick.clear();
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] manager config reload applied entityTypeId={} min={} activation={} despawn={}",
            newConfig.entityTypeId,
            newConfig.minimumLeviathans,
            newConfig.activationRadiusBlocks,
            newConfig.despawnRadiusBlocks);
    }

    public static int dump(ServerCommandSource source, boolean includeVirtual, boolean includeLoaded) {
        if (config == null || virtualStore == null) {
            source.sendError(Text.literal("Leviathan manager not initialized."));
            return 0;
        }

        ServerWorld overworld = source.getServer().getOverworld();
        if (overworld == null) {
            source.sendError(Text.literal("No overworld available."));
            return 0;
        }

        List<VirtualLeviathanStore.VirtualLeviathanState> snapshot = virtualStore.snapshot();
        Map<UUID, Entity> loaded = loadedManagedById(overworld);

        int virtualCount = includeVirtual ? snapshot.size() : 0;
        int loadedCount = includeLoaded ? loaded.size() : 0;

        source.sendFeedback(() -> Text.literal("Atlantis leviathans: virtual=" + virtualCount + " loaded=" + loadedCount), false);

        int shown = 0;
        for (VirtualLeviathanStore.VirtualLeviathanState state : snapshot) {
            if (shown >= 25) {
                break;
            }

            String line = String.format(Locale.ROOT,
                " - id=%s type=%s pos=(%.1f, %.1f, %.1f) heading=(%.2f, %.2f) scaleMul=%.2f dmgMul=%.2f hpMul=%.2f",
                shortId(state.id()),
                state.entityTypeId(),
                state.pos().x,
                state.pos().y,
                state.pos().z,
                state.headingX(),
                state.headingZ(),
                state.scaleMultiplier(),
                state.damageMultiplier(),
                state.healthMultiplier());
            source.sendFeedback(() -> Text.literal(line), false);

            if (includeLoaded) {
                Entity entity = loaded.get(state.id());
                if (entity != null) {
                    CombatRuntime runtime = combatRuntimeById.get(state.id());
                    String engagementState = runtime == null || runtime.targetUuid == null ? "PASSIVE" : "ENGAGED/" + runtime.substate;
                    String target = runtime == null || runtime.targetUuid == null ? "<none>" : shortId(runtime.targetUuid);
                    String loadedTypeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
                    String loadedLine = String.format(Locale.ROOT,
                        "   loadedType=%s loadedPos=(%.1f, %.1f, %.1f) engagement=%s target=%s",
                        loadedTypeId,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        engagementState,
                        target);
                    source.sendFeedback(() -> Text.literal(loadedLine), false);
                }
            }
            shown++;
        }

        if (snapshot.size() > shown) {
            final int shownFinal = shown;
            source.sendFeedback(() -> Text.literal("(output truncated; showing first " + shownFinal + ")"), false);
        }

        return 1;
    }

    private static void tick(MinecraftServer server) {
        if (config == null || virtualStore == null || configuredEntityTypesById.isEmpty()) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] tick skipped: not initialized config={} store={} entityType={}",
                config != null,
                virtualStore != null,
                !configuredEntityTypesById.isEmpty());
            return;
        }

        serverTicks++;

        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            return;
        }

        updateEffectiveDistancesFromServerSettings(server);

        if (config.virtualStateFlushIntervalMinutes > 0) {
            long interval = (long) config.virtualStateFlushIntervalMinutes * 60L * 20L;
            if (interval > 0 && (serverTicks % interval) == 0L) {
                virtualStore.flush();
            }
        }

        if (serverTicks - lastEnsureTick >= MINIMUM_ENSURE_INTERVAL_TICKS) {
            ensureMinimumPopulation(overworld);
            lastEnsureTick = serverTicks;
        }

        Map<UUID, Entity> loadedById = loadedManagedById(overworld);
        Set<UUID> disappearedLoadedIds = new LinkedHashSet<>(loadedIdsLastTick);
        disappearedLoadedIds.removeAll(loadedById.keySet());
        combatRuntimeById.keySet().retainAll(loadedById.keySet());
        int chunkBudget = config.forceChunkLoadingEnabled ? config.maxChunkLoadsPerTick : 0;

        if (serverTicks - lastRecoveryTick >= RECOVERY_INTERVAL_TICKS) {
            reconcileLoadedToVirtual(loadedById);
            lastRecoveryTick = serverTicks;
        }

        List<VirtualLeviathanStore.VirtualLeviathanState> snapshot = virtualStore.snapshot();
        if ((serverTicks % 200L) == 0L) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] tick summary tick={} virtual={} loaded={} combat={} activation={} despawn={} minSpawn={}",
                serverTicks,
                snapshot.size(),
                loadedById.size(),
                combatRuntimeById.size(),
                getActivationRadiusBlocks(),
                getDespawnRadiusBlocks(),
                getMinSpawnDistanceBlocks());
        }
        for (VirtualLeviathanStore.VirtualLeviathanState state : snapshot) {
            Entity loaded = loadedById.get(state.id());
            if (loaded == null) {
                if (disappearedLoadedIds.contains(state.id())
                    && isAnyPlayerNear(overworld, state.pos(), getActivationRadiusBlocks())) {
                    retireAndReplaceVirtualLeviathan(overworld, state, "loaded instance disappeared near players (likely killed)");
                    continue;
                }

                VirtualLeviathanStore.VirtualLeviathanState advanced = advanceVirtualState(overworld, state);
                virtualStore.upsert(advanced);

                if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                    if (isAnyPlayerNear(overworld, advanced.pos(), getActivationRadiusBlocks())) {
                        Set<ChunkPos> desired = computeDesiredChunks(advanced);
                        chunkBudget -= chunkPreloader.request(overworld, advanced.id(), desired, serverTicks, chunkBudget);
                        inactiveChunkReleaseDone.remove(advanced.id());
                    } else if (inactiveChunkReleaseDone.add(advanced.id())) {
                        chunkPreloader.release(overworld, advanced.id());
                    }
                }

                tryMaterialize(overworld, advanced, loadedById);
                continue;
            }

            inactiveChunkReleaseDone.remove(state.id());

            CombatRuntime runtime = combatRuntimeById.computeIfAbsent(state.id(), id -> {
                CombatRuntime created = new CombatRuntime();
                created.previousPos = posOf(loaded);
                return created;
            });

            boolean engaged = updateCombat(overworld, loaded, runtime);
            if (!engaged) {
                applyLoadedPassiveMovement(overworld, loaded, state);
            }

            VirtualLeviathanStore.VirtualLeviathanState sync = syncStateFromLoaded(loaded, state.id());

            if (!isAnyPlayerNear(overworld, sync.pos(), getDespawnRadiusBlocks())) {
                loaded.discard();
                virtualStore.upsert(sync);
                combatRuntimeById.remove(state.id());
                if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                    chunkPreloader.release(overworld, state.id());
                    inactiveChunkReleaseDone.add(state.id());
                }
                AtlantisMod.LOGGER.info("[Atlantis][leviathan] despawn id={} pos=({}, {}, {})",
                    shortId(sync.id()),
                    round1(sync.pos().x),
                    round1(sync.pos().y),
                    round1(sync.pos().z));
                continue;
            }

            virtualStore.upsert(sync);

            if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                Set<ChunkPos> desired = computeDesiredChunks(sync);
                chunkBudget -= chunkPreloader.request(overworld, sync.id(), desired, serverTicks, chunkBudget);
            }
        }

        if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
            chunkPreloader.releaseUnused(overworld, serverTicks, config.releaseTicketsAfterTicks);
        }

        loadedIdsLastTick.clear();
        loadedIdsLastTick.addAll(loadedById.keySet());
    }

    private static void retireAndReplaceVirtualLeviathan(ServerWorld world,
                                                         VirtualLeviathanStore.VirtualLeviathanState removed,
                                                         String reason) {
        virtualStore.remove(removed.id());
        combatRuntimeById.remove(removed.id());
        inactiveChunkReleaseDone.remove(removed.id());
        if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
            chunkPreloader.release(world, removed.id());
        }

        Random random = new Random(world.getRandom().nextLong() ^ serverTicks ^ removed.id().getMostSignificantBits());
        VirtualLeviathanStore.VirtualLeviathanState replacement = createVirtualLeviathan(world, random);
        virtualStore.upsert(replacement);

        AtlantisMod.LOGGER.info("[Atlantis][leviathan] retired virtual id={} and created replacement id={} reason={} oldPos=({}, {}, {}) newPos=({}, {}, {})",
            shortId(removed.id()),
            shortId(replacement.id()),
            reason,
            round1(removed.pos().x),
            round1(removed.pos().y),
            round1(removed.pos().z),
            round1(replacement.pos().x),
            round1(replacement.pos().y),
            round1(replacement.pos().z));
    }

    private static void ensureMinimumPopulation(ServerWorld world) {
        int deficit = config.minimumLeviathans - virtualStore.size();
        if (deficit <= 0) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] ensure minimum satisfied current={} minimum={}", virtualStore.size(), config.minimumLeviathans);
            return;
        }

        AtlantisMod.LOGGER.info("[Atlantis][leviathan] ensuring minimum population deficit={} current={} minimum={}",
            deficit,
            virtualStore.size(),
            config.minimumLeviathans);

        Random random = new Random(world.getRandom().nextLong() ^ serverTicks);
        for (int i = 0; i < deficit; i++) {
            VirtualLeviathanStore.VirtualLeviathanState created = createVirtualLeviathan(world, random);
            virtualStore.upsert(created);
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] virtual create id={} pos=({}, {}, {}) heading=({}, {})",
                shortId(created.id()),
                round1(created.pos().x),
                round1(created.pos().y),
                round1(created.pos().z),
                round2(created.headingX()),
                round2(created.headingZ()));
        }
    }

    private static VirtualLeviathanStore.VirtualLeviathanState createVirtualLeviathan(ServerWorld world, Random random) {
        BlockPos spawn = resolveWorldSpawn(world);
        WorldBorder border = world.getWorldBorder();

        double spawnAngle = random.nextDouble() * Math.PI * 2.0;
        double spawnDistance = config.roamMinDistanceBlocks
            + random.nextDouble() * Math.max(1.0, (double) (config.roamMaxDistanceBlocks - config.roamMinDistanceBlocks));
        double spawnX = spawn.getX() + Math.cos(spawnAngle) * spawnDistance;
        double spawnZ = spawn.getZ() + Math.sin(spawnAngle) * spawnDistance;
        int yJitter = config.spawnYRandomRange <= 0 ? 0 : random.nextInt(config.spawnYRandomRange * 2 + 1) - config.spawnYRandomRange;
        double spawnY = config.spawnY + yJitter;

        Vec3d chosenPos = new Vec3d(
            clamp(spawnX, border.getBoundWest() + 8.0, border.getBoundEast() - 8.0),
            spawnY,
            clamp(spawnZ, border.getBoundNorth() + 8.0, border.getBoundSouth() - 8.0));

        double angle = random.nextDouble() * Math.PI * 2.0;
        String entityTypeId = selectRandomEntityTypeId(random);
        double scaleMultiplier = computeSpawnScaleMultiplier(chosenPos.y, entityTypeId);
        double damageMultiplier = computeSpawnDamageMultiplier(chosenPos.y);
        double healthMultiplier = computeSpawnHealthMultiplier(chosenPos.y);
        return new VirtualLeviathanStore.VirtualLeviathanState(
            UUID.randomUUID(),
            chosenPos,
            Math.cos(angle),
            Math.sin(angle),
            serverTicks,
            entityTypeId,
            chosenPos.y,
            scaleMultiplier,
            damageMultiplier,
            healthMultiplier
        );
    }

    private static VirtualLeviathanStore.VirtualLeviathanState advanceVirtualState(ServerWorld world, VirtualLeviathanStore.VirtualLeviathanState state) {
        if (!config.virtualTravelEnabled) {
            return state;
        }

        long dt = Math.max(1L, serverTicks - state.lastTick());
        Vec3d steered = steerHeading(world, state.pos(), state.headingX(), state.headingZ(), Math.min(20L, dt));

        Vec3d moved = new Vec3d(
            state.pos().x + steered.x * config.virtualSpeedBlocksPerTick * dt,
            state.pos().y,
            state.pos().z + steered.z * config.virtualSpeedBlocksPerTick * dt
        );

        WorldBorder border = world.getWorldBorder();
        moved = new Vec3d(
            clamp(moved.x, border.getBoundWest() + 8.0, border.getBoundEast() - 8.0),
            moved.y,
            clamp(moved.z, border.getBoundNorth() + 8.0, border.getBoundSouth() - 8.0)
        );

        Vec3d virtualHeading = normalizeXZ(steered.x, steered.z);
        Vec3d corrected = new Vec3d(moved.x, moved.y, moved.z);
        return new VirtualLeviathanStore.VirtualLeviathanState(
            state.id(),
            corrected,
            virtualHeading.x,
            virtualHeading.z,
            serverTicks,
            state.entityTypeId(),
            state.spawnYAtCreation(),
            state.scaleMultiplier(),
            state.damageMultiplier(),
            state.healthMultiplier()
        );
    }

    private static Vec3d steerHeading(ServerWorld world, Vec3d pos, double headingX, double headingZ, long dt) {
        BlockPos spawn = resolveWorldSpawn(world);
        Vec3d fromSpawn = new Vec3d(pos.x - spawn.getX(), 0.0, pos.z - spawn.getZ());
        double dist = Math.sqrt(fromSpawn.x * fromSpawn.x + fromSpawn.z * fromSpawn.z);

        Vec3d desired = new Vec3d(headingX, 0.0, headingZ);
        if (dist < config.roamMinDistanceBlocks) {
            desired = normalizeXZ(fromSpawn.x, fromSpawn.z);
        } else if (dist > config.roamMaxDistanceBlocks) {
            desired = normalizeXZ(-fromSpawn.x, -fromSpawn.z);
        }

        Vec3d current = normalizeXZ(headingX, headingZ);
        double maxTurnRadians = 0.09d * Math.max(1L, dt);
        return turnLimited(current, desired, maxTurnRadians);
    }

    private static Vec3d turnLimited(Vec3d current, Vec3d desired, double maxTurnRadians) {
        double currentAngle = Math.atan2(current.z, current.x);
        double desiredAngle = Math.atan2(desired.z, desired.x);
        double delta = wrapRadians(desiredAngle - currentAngle);
        double limited = clamp(delta, -maxTurnRadians, maxTurnRadians);
        return normalizeXZ(Math.cos(currentAngle + limited), Math.sin(currentAngle + limited));
    }

    private static void tryMaterialize(ServerWorld world,
                                       VirtualLeviathanStore.VirtualLeviathanState state,
                                       Map<UUID, Entity> loadedById) {
        if (loadedById.containsKey(state.id())) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped already loaded id={}", shortId(state.id()));
            return;
        }
        if (!isAnyPlayerNear(world, state.pos(), getActivationRadiusBlocks())) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped no nearby player id={} activationRadius={}", shortId(state.id()), getActivationRadiusBlocks());
            return;
        }
        if (isAnyPlayerNear(world, state.pos(), getMinSpawnDistanceBlocks())) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped too close to player id={} minSpawnDistance={}", shortId(state.id()), getMinSpawnDistanceBlocks());
            return;
        }

        Entity alreadyLoaded = loadedById.get(state.id());
        if (alreadyLoaded != null && alreadyLoaded.isAlive()) {
            return;
        }

        ChunkPos center = new ChunkPos(BlockPos.ofFloored(state.pos()));
        if (!isSpawnReady(world, state.id(), center)) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped spawn not ready id={} chunk=({}, {})", shortId(state.id()), center.x, center.z);
            return;
        }
        if (!isWaterValid(world, state.pos())) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped water invalid id={} pos=({}, {}, {})",
                shortId(state.id()),
                round1(state.pos().x),
                round1(state.pos().y),
                round1(state.pos().z));
            return;
        }

        Entity entity = spawnLeviathanFromVirtual(world, state);
        loadedById.put(state.id(), entity);

        AtlantisMod.LOGGER.info("[Atlantis][leviathan] materialize id={} uuid={} pos=({}, {}, {})",
            shortId(state.id()),
            entity.getUuidAsString(),
            round1(entity.getX()),
            round1(entity.getY()),
            round1(entity.getZ()));
    }

    private static Entity spawnLeviathanFromVirtual(ServerWorld world, VirtualLeviathanStore.VirtualLeviathanState state) {
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] spawning loaded leviathan id={} entityTypeId={} pos=({}, {}, {}) heading=({}, {})",
            shortId(state.id()),
            state.entityTypeId(),
            round1(state.pos().x),
            round1(state.pos().y),
            round1(state.pos().z),
            round2(state.headingX()),
            round2(state.headingZ()));

        EntityType<?> entityType = configuredEntityTypesById.get(state.entityTypeId());
        if (entityType == null) {
            throw new IllegalStateException("Configured leviathan entityTypeId missing from validated pool: " + state.entityTypeId());
        }

        Entity entity = entityType.create(world, SpawnReason.EVENT);
        if (entity == null) {
            throw new IllegalStateException("Failed to create leviathan entity for configured entityTypeId=" + state.entityTypeId());
        }

        entity.refreshPositionAndAngles(state.pos().x, state.pos().y, state.pos().z, 0.0f, 0.0f);
        entity.addCommandTag(MANAGED_TAG);
        entity.addCommandTag(LeviathanIdTags.toTag(state.id()));

        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalStateException("Configured leviathan entity type is not LivingEntity. entityTypeId=" + state.entityTypeId());
        }

        EntityAttributeInstance scale = living.getAttributeInstance(EntityAttributes.SCALE);
        if (scale == null) {
            throw new IllegalStateException("entityScale requires SCALE attribute support but entity has no SCALE attribute. entityTypeId=" + state.entityTypeId());
        }
        scale.setBaseValue(config.entityScale * state.scaleMultiplier());

        EntityAttributeInstance maxHealth = living.getAttributeInstance(EntityAttributes.MAX_HEALTH);
        if (maxHealth == null) {
            throw new IllegalStateException("depthHealth requires MAX_HEALTH attribute support but entity has no MAX_HEALTH attribute. entityTypeId=" + state.entityTypeId());
        }
        double scaledMaxHealth = maxHealth.getBaseValue() * state.healthMultiplier();
        maxHealth.setBaseValue(Math.max(1.0d, scaledMaxHealth));
        living.setHealth((float) maxHealth.getValue());

        if (entity instanceof MobEntity mob) {
            mob.setPersistent();
            mob.setTarget(null);
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] marked spawned mob persistent id={} entityUuid={}",
                shortId(state.id()),
                entity.getUuidAsString());
        }

        Vec3d initialVelocity = new Vec3d(
            state.headingX() * config.virtualSpeedBlocksPerTick,
            0.0,
            state.headingZ() * config.virtualSpeedBlocksPerTick
        );
        entity.setVelocity(initialVelocity);

        if (!world.spawnEntity(entity)) {
            throw new IllegalStateException("Failed to spawn leviathan entity into world for id=" + state.id());
        }

        return entity;
    }

    private static boolean updateCombat(ServerWorld world, Entity entity, CombatRuntime runtime) {
        if (!config.combatEnabled) {
            runtime.resetToPassive();
            runtime.previousPos = posOf(entity);
            return false;
        }

        if (runtime.targetUuid == null) {
            ServerPlayerEntity initialTarget = findNearestTarget(world, posOf(entity), config.engageRadiusBlocks, config.engageVerticalRadiusBlocks);
            if (initialTarget == null) {
                runtime.previousPos = posOf(entity);
                return false;
            }
            runtime.targetUuid = initialTarget.getUuid();
            runtime.anchorPos = posOf(initialTarget);
            runtime.phaseStartTick = serverTicks;
            runtime.substate = CombatSubstate.ACQUIRE;
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement start leviathan={} target={}", shortId(entity.getUuid()), shortId(initialTarget.getUuid()));
        }

        ServerPlayerEntity target = world.getServer() == null ? null : world.getServer().getPlayerManager().getPlayer(runtime.targetUuid);
        Vec3d entityPos = posOf(entity);
        Vec3d previousPos = runtime.previousPos == null ? entityPos : runtime.previousPos;

        if (target == null || !target.isAlive() || target.isSpectator()) {
            if (runtime.anchorPos == null) {
                runtime.anchorPos = entityPos;
            }
            if (runtime.missingTargetUntilTick < 0L) {
                runtime.missingTargetUntilTick = serverTicks + config.disconnectLingerTicks;
                runtime.phaseStartTick = serverTicks;
            }
            if (serverTicks > runtime.missingTargetUntilTick) {
                AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (target missing timeout) leviathan={} target={}",
                    shortId(entity.getUuid()),
                    runtime.targetUuid == null ? "<none>" : shortId(runtime.targetUuid));
                runtime.resetToPassive();
                runtime.previousPos = entityPos;
                return false;
            }

            circleAroundAnchor(world, entity, runtime.anchorPos, config.passOvershootBlocks, config.virtualSpeedBlocksPerTick);
            runtime.substate = CombatSubstate.TURN_BACK;
            runtime.previousPos = entityPos;
            return true;
        }

        runtime.anchorPos = posOf(target);
        runtime.missingTargetUntilTick = -1L;

        if (runtime.substate == CombatSubstate.REACQUIRE
            && entityPos.distanceTo(posOf(target)) <= PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS) {
            if (serverTicks >= runtime.cooldownUntilTick) {
                AtlantisMod.LOGGER.info("[Atlantis][leviathan] predator retreat canceled by close target distance={} <= {} leviathan={} target={}",
                    round1(entityPos.distanceTo(posOf(target))),
                    round1(PREDATOR_CLOSE_RANGE_CANCEL_BLOCKS),
                    shortId(entity.getUuid()),
                    shortId(target.getUuid()));
                beginCharge(runtime, entityPos, target);
            } else {
                runtime.substate = CombatSubstate.ACQUIRE;
                runtime.phaseStartTick = serverTicks;
            }
        }

        if (!isWithinEngagementRange(entityPos, posOf(target), config.disengageRadiusBlocks, config.disengageVerticalRadiusBlocks)) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (out of range) leviathan={} target={} distance={} disengageRadius={}",
                shortId(entity.getUuid()),
                shortId(target.getUuid()),
                round1(entityPos.distanceTo(posOf(target))),
                config.disengageRadiusBlocks);
            runtime.resetToPassive();
            runtime.previousPos = entityPos;
            return false;
        }

        if (!hasLineOfEngagement(world, entity, target)) {
            runtime.invalidLineTicks++;
        } else {
            runtime.invalidLineTicks = 0L;
        }
        if (runtime.invalidLineTicks > config.lineOfEngagementTimeoutTicks) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] engagement ended (line timeout) leviathan={} target={} invalidTicks={} timeout={}",
                shortId(entity.getUuid()),
                shortId(target.getUuid()),
                runtime.invalidLineTicks,
                config.lineOfEngagementTimeoutTicks);
            runtime.resetToPassive();
            runtime.previousPos = entityPos;
            return false;
        }

        switch (runtime.substate) {
            case ACQUIRE -> {
                if (serverTicks >= runtime.cooldownUntilTick) {
                    beginCharge(runtime, entityPos, target);
                } else {
                    runtime.substate = CombatSubstate.TURN_BACK;
                    runtime.phaseStartTick = serverTicks;
                    circleAroundAnchor(world, entity, posOf(target), Math.max(8.0d, config.passOvershootBlocks), config.virtualSpeedBlocksPerTick);
                }
            }
            case CHARGE -> {
                Vec3d guidedDirection = refineChargeDirection(runtime, entityPos, target, 6.0d, 0.045d);
                runtime.chargeDirection = guidedDirection;
                detectChargeHit(world, entity, target, runtime, previousPos, entityPos);
                Vec3d steered = chooseSubmergedDirection(world, entityPos, guidedDirection, true);
                entity.setVelocity(steered.multiply(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if (detectChargeStall(entity, runtime, previousPos, entityPos)) {
                    break;
                }

                if ((serverTicks - runtime.phaseStartTick) >= config.chargeDurationTicks) {
                    runtime.substate = CombatSubstate.PASS_THROUGH;
                    runtime.phaseStartTick = serverTicks;
                    runtime.cooldownUntilTick = serverTicks + config.chargeCooldownTicks;
                    runtime.stalledTicks = 0L;
                    runtime.passStartPos = entityPos;
                }
            }
            case PASS_THROUGH -> {
                detectChargeHit(world, entity, target, runtime, previousPos, entityPos);
                Vec3d steered = chooseSubmergedDirection(world, entityPos, runtime.chargeDirection, true);
                entity.setVelocity(steered.multiply(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if (detectChargeStall(entity, runtime, previousPos, entityPos)) {
                    break;
                }

                if (runtime.passStartPos == null) {
                    runtime.passStartPos = previousPos == null ? entityPos : previousPos;
                }

                if (entityPos.distanceTo(runtime.passStartPos) >= PREDATOR_RETREAT_RADIUS_BLOCKS) {
                    startPredatorRetreat(runtime, entityPos, posOf(target), target.getUuid());
                }
            }
            case TURN_BACK -> {
                predatorRetreatStep(world, entity, runtime, posOf(target), target.getUuid());
            }
            case REACQUIRE -> {
                if (serverTicks >= runtime.cooldownUntilTick) {
                    runtime.substate = CombatSubstate.ACQUIRE;
                    runtime.phaseStartTick = serverTicks;
                } else {
                    predatorCircleStep(world, entity, runtime);
                }
            }
        }

        runtime.previousPos = entityPos;
        return true;
    }

    private static void beginCharge(CombatRuntime runtime, Vec3d entityPos, ServerPlayerEntity target) {
        Vec3d intercept = posOf(target).add(target.getVelocity().multiply(Math.min(10.0d, config.chargeDurationTicks * 0.25d)));
        Vec3d raw = intercept.subtract(entityPos);
        double len = raw.length();
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Charge direction invalid (zero length) for target=" + target.getUuidAsString());
        }

        runtime.chargeDirection = raw.multiply(1.0d / len);
        runtime.substate = CombatSubstate.CHARGE;
        runtime.phaseStartTick = serverTicks;
        runtime.stalledTicks = 0L;
        runtime.passId++;
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] charge begin target={} passId={} direction=({}, {}, {})",
            shortId(target.getUuid()),
            runtime.passId,
            round2(runtime.chargeDirection.x),
            round2(runtime.chargeDirection.y),
            round2(runtime.chargeDirection.z));
    }

    private static Vec3d refineChargeDirection(CombatRuntime runtime,
                                               Vec3d entityPos,
                                               ServerPlayerEntity target,
                                               double maxHorizontalTurnDegrees,
                                               double maxVerticalStep) {
        Vec3d current = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3d(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double lookaheadTicks = clamp(config.chargeDurationTicks * 0.15d, 2.0d, 7.0d);
        Vec3d intercept = posOf(target).add(target.getVelocity().multiply(lookaheadTicks));
        Vec3d toIntercept = intercept.subtract(entityPos);
        double len = toIntercept.length();
        if (len < 1.0e-6d) {
            return current;
        }

        Vec3d desired = toIntercept.multiply(1.0d / len);
        double maxTurnRadians = Math.toRadians(Math.max(0.5d, maxHorizontalTurnDegrees));
        return steerChargeDirection3d(current, desired, maxTurnRadians, Math.max(0.01d, maxVerticalStep));
    }

    private static Vec3d steerChargeDirection3d(Vec3d current,
                                                Vec3d desired,
                                                double maxHorizontalTurnRadians,
                                                double maxVerticalStep) {
        Vec3d currentNorm = normalizeVectorStrict(current);
        Vec3d desiredNorm = normalizeVectorStrict(desired);

        Vec3d currentHorizontal = new Vec3d(currentNorm.x, 0.0d, currentNorm.z);
        Vec3d desiredHorizontal = new Vec3d(desiredNorm.x, 0.0d, desiredNorm.z);

        Vec3d steeredHorizontal;
        double currentHorizontalSq = currentHorizontal.lengthSquared();
        double desiredHorizontalSq = desiredHorizontal.lengthSquared();
        if (currentHorizontalSq < 1.0e-6d && desiredHorizontalSq < 1.0e-6d) {
            steeredHorizontal = new Vec3d(1.0d, 0.0d, 0.0d);
        } else if (currentHorizontalSq < 1.0e-6d) {
            steeredHorizontal = normalizeXZ(desiredHorizontal.x, desiredHorizontal.z);
        } else if (desiredHorizontalSq < 1.0e-6d) {
            steeredHorizontal = normalizeXZ(currentHorizontal.x, currentHorizontal.z);
        } else {
            steeredHorizontal = turnLimited(
                normalizeXZ(currentHorizontal.x, currentHorizontal.z),
                normalizeXZ(desiredHorizontal.x, desiredHorizontal.z),
                maxHorizontalTurnRadians);
        }

        double steeredY = currentNorm.y + clamp(desiredNorm.y - currentNorm.y, -maxVerticalStep, maxVerticalStep);
        return normalizeVectorStrict(new Vec3d(steeredHorizontal.x, steeredY, steeredHorizontal.z));
    }

    private static boolean detectChargeStall(Entity entity, CombatRuntime runtime, Vec3d previousPos, Vec3d currentPos) {
        if (previousPos == null || currentPos == null) {
            runtime.stalledTicks = 0L;
            return false;
        }
        if (runtime.substate != CombatSubstate.CHARGE && runtime.substate != CombatSubstate.PASS_THROUGH) {
            runtime.stalledTicks = 0L;
            return false;
        }

        double movedSq = currentPos.squaredDistanceTo(previousPos);
        if (movedSq < 0.0025d) {
            runtime.stalledTicks++;
        } else {
            runtime.stalledTicks = 0L;
            return false;
        }

        if (runtime.stalledTicks < 12L) {
            return false;
        }

        AtlantisMod.LOGGER.warn("[Atlantis][leviathan] charge stalled; forcing turn-back leviathan={} substate={} stalledTicks={} movedSq={}",
            shortId(entity.getUuid()),
            runtime.substate,
            runtime.stalledTicks,
            round2(movedSq));
        runtime.substate = CombatSubstate.TURN_BACK;
        runtime.phaseStartTick = serverTicks;
        runtime.cooldownUntilTick = serverTicks + config.chargeCooldownTicks;
        runtime.stalledTicks = 0L;
        runtime.passStartPos = null;
        runtime.retreatCenter = currentPos;
        runtime.retreatTarget = null;
        runtime.retreatHoldUntilTick = 0L;
        return true;
    }

    private static void startPredatorRetreat(CombatRuntime runtime, Vec3d entityPos, Vec3d targetPos, UUID targetId) {
        runtime.substate = CombatSubstate.TURN_BACK;
        runtime.phaseStartTick = serverTicks;
        runtime.stalledTicks = 0L;
        runtime.passStartPos = null;
        runtime.retreatCenter = entityPos;
        runtime.retreatTarget = selectRetreatTarget(entityPos, targetPos, runtime.passId, targetId);
        runtime.retreatHoldUntilTick = 0L;
    }

    private static Vec3d selectRetreatTarget(Vec3d entityPos, Vec3d targetPos, long passId, UUID targetId) {
        Vec3d fromTarget = new Vec3d(entityPos.x - targetPos.x, 0.0d, entityPos.z - targetPos.z);
        Vec3d base;
        try {
            base = normalizeXZ(fromTarget.x, fromTarget.z);
        } catch (IllegalStateException ignored) {
            base = new Vec3d(1.0d, 0.0d, 0.0d);
        }

        long seed = passId ^ targetId.getLeastSignificantBits() ^ targetId.getMostSignificantBits();
        Random random = new Random(seed);
        double angleJitter = (random.nextDouble() * Math.PI * 1.2d) - (Math.PI * 0.6d);
        double baseAngle = Math.atan2(base.z, base.x) + angleJitter;

        double tx = targetPos.x + Math.cos(baseAngle) * PREDATOR_RETREAT_RADIUS_BLOCKS;
        double tz = targetPos.z + Math.sin(baseAngle) * PREDATOR_RETREAT_RADIUS_BLOCKS;
        return new Vec3d(tx, targetPos.y, tz);
    }

    private static void predatorRetreatStep(ServerWorld world,
                                            Entity entity,
                                            CombatRuntime runtime,
                                            Vec3d targetPos,
                                            UUID targetId) {
        Vec3d entityPos = posOf(entity);

        if (runtime.retreatTarget == null) {
            runtime.retreatTarget = selectRetreatTarget(entityPos, targetPos, runtime.passId, targetId);
        }
        if (runtime.retreatCenter == null) {
            runtime.retreatCenter = entityPos;
        }

        Vec3d retreatTarget = new Vec3d(runtime.retreatTarget.x, entityPos.y, runtime.retreatTarget.z);
        Vec3d toRetreat = retreatTarget.subtract(entityPos);
        double retreatDistance = toRetreat.length();

        if (retreatDistance <= PREDATOR_RETREAT_REACHED_EPSILON_BLOCKS) {
            runtime.substate = CombatSubstate.REACQUIRE;
            runtime.phaseStartTick = serverTicks;
            long seed = runtime.passId ^ targetId.getMostSignificantBits() ^ targetId.getLeastSignificantBits() ^ Double.doubleToLongBits(entityPos.x + entityPos.z);
            Random random = new Random(seed);
            int minHold = 30;
            int maxHold = 100;
            runtime.retreatHoldUntilTick = serverTicks + minHold + random.nextInt((maxHold - minHold) + 1);
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] predator retreat reached; entering circling phase holdUntilTick={} id={}",
                runtime.retreatHoldUntilTick,
                shortId(entity.getUuid()));
            return;
        }

        Vec3d centerToEntity = new Vec3d(entityPos.x - runtime.retreatCenter.x, 0.0d, entityPos.z - runtime.retreatCenter.z);
        Vec3d tangent;
        try {
            Vec3d radial = normalizeXZ(centerToEntity.x, centerToEntity.z);
            tangent = new Vec3d(-radial.z, 0.0d, radial.x);
        } catch (IllegalStateException ignored) {
            tangent = new Vec3d(0.0d, 0.0d, 1.0d);
        }

        Vec3d toward;
        try {
            toward = normalizeXZ(toRetreat.x, toRetreat.z);
        } catch (IllegalStateException ignored) {
            toward = tangent;
        }
        Vec3d desired = normalizeVectorStrict(toward.multiply(0.75d).add(tangent.multiply(0.45d)));

        Vec3d steered = chooseSubmergedDirection(world, entityPos, desired, true);
        double recuperateSpeed = Math.max(0.06d, Math.min(config.virtualSpeedBlocksPerTick, config.chargeSpeedBlocksPerTick * 0.5d));
        entity.setVelocity(steered.multiply(recuperateSpeed));
        faceAlong(entity, steered);
    }

    private static void predatorCircleStep(ServerWorld world, Entity entity, CombatRuntime runtime) {
        Vec3d entityPos = posOf(entity);
        Vec3d center = runtime.retreatTarget == null ? entityPos : runtime.retreatTarget;

        double baseRadius = PREDATOR_CIRCLE_RADIUS_BLOCKS;
        double angleSpeed = 0.05d;
        double elapsed = Math.max(0.0d, (double) (serverTicks - runtime.phaseStartTick));
        double angle = elapsed * angleSpeed;
        if ((runtime.passId & 1L) == 0L) {
            angle *= -1.0d;
        }

        Vec3d orbitPoint = new Vec3d(
            center.x + Math.cos(angle) * baseRadius,
            entityPos.y,
            center.z + Math.sin(angle) * baseRadius
        );
        Vec3d desired = orbitPoint.subtract(entityPos);
        if (desired.length() < 1.0e-6d) {
            desired = new Vec3d(1.0d, 0.0d, 0.0d);
        }

        Vec3d steered = chooseSubmergedDirection(world, entityPos, desired.normalize(), true);
        double recuperateSpeed = Math.max(0.06d, Math.min(config.virtualSpeedBlocksPerTick, config.chargeSpeedBlocksPerTick * 0.45d));
        entity.setVelocity(steered.multiply(recuperateSpeed));
        faceAlong(entity, steered);
    }

    private static void detectChargeHit(ServerWorld world,
                                        Entity leviathan,
                                        ServerPlayerEntity target,
                                        CombatRuntime runtime,
                                        Vec3d segmentStart,
                                        Vec3d segmentEnd) {
        if (segmentStart == null || segmentEnd == null) {
            return;
        }

        Box expanded = target.getBoundingBox().expand(config.chargeHitboxExpandBlocks);
        boolean directIntersect = expanded.raycast(segmentStart, segmentEnd).isPresent();
        double leviathanRadius = Math.max(0.4d, leviathan.getWidth() * 0.5d);
        double targetRadius = Math.max(0.3d, target.getWidth() * 0.5d);
        double sweptRadius = leviathanRadius + targetRadius + config.chargeHitboxExpandBlocks;
        double centerDistanceSq = squaredDistancePointToSegment(target.getBoundingBox().getCenter(), segmentStart, segmentEnd);
        if (!directIntersect && centerDistanceSq > (sweptRadius * sweptRadius)) {
            return;
        }

        Vec3d toTarget = posOf(target).subtract(posOf(leviathan));
        double toTargetLen = toTarget.length();
        if (toTargetLen < 1.0e-6d) {
            return;
        }

        Vec3d toTargetNorm = toTarget.multiply(1.0d / toTargetLen);
        Vec3d forward = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3d(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double dot;
        double toTargetHorizontalSq = (toTargetNorm.x * toTargetNorm.x) + (toTargetNorm.z * toTargetNorm.z);
        double forwardHorizontalSq = (forward.x * forward.x) + (forward.z * forward.z);
        if (toTargetHorizontalSq >= 1.0e-6d && forwardHorizontalSq >= 1.0e-6d) {
            Vec3d toTargetHorizontal = normalizeXZ(toTargetNorm.x, toTargetNorm.z);
            Vec3d forwardHorizontal = normalizeXZ(forward.x, forward.z);
            dot = forwardHorizontal.dotProduct(toTargetHorizontal);
        } else {
            dot = forward.dotProduct(toTargetNorm);
        }
        if (dot < config.chargeDirectionDotThreshold) {
            return;
        }

        double sampledSpeed = Math.max(leviathan.getVelocity().length(), segmentEnd.distanceTo(segmentStart));
        double effectiveMinHitSpeed = Math.min(config.chargeMinHitSpeed, Math.max(0.1d, config.chargeSpeedBlocksPerTick * 0.35d));
        if (sampledSpeed < effectiveMinHitSpeed) {
            return;
        }

        long lastHitPass = runtime.lastHitPassByPlayer.getOrDefault(target.getUuid(), -1L);
        if (lastHitPass == runtime.passId) {
            return;
        }
        long lastHitTick = runtime.lastHitTickByPlayer.getOrDefault(target.getUuid(), Long.MIN_VALUE / 2L);
        if ((serverTicks - lastHitTick) < config.chargeHitCooldownTicks) {
            return;
        }

        boolean damaged;
        double effectiveDamage = resolveEffectiveChargeDamage(leviathan);
        double totalHealthBefore = target.getHealth() + target.getAbsorptionAmount();
        if (leviathan instanceof LivingEntity living) {
            damaged = target.damage(world, world.getDamageSources().mobAttack(living), (float) effectiveDamage);
        } else {
            damaged = target.damage(world, world.getDamageSources().generic(), (float) effectiveDamage);
        }

        if (!damaged) {
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge attempted but rejected target={} raw={} speed={} (likely shield/blocking or hurt cooldown)",
                shortId(target.getUuid()),
                round2(effectiveDamage),
                round2(sampledSpeed));
            return;
        }

        double totalHealthAfter = target.getHealth() + target.getAbsorptionAmount();
        double appliedDamage = Math.max(0.0d, totalHealthBefore - totalHealthAfter);
        double mitigatedDamage = Math.max(0.0d, effectiveDamage - appliedDamage);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge mitigation target={} raw={} applied={} mitigated={} armor={} toughness={} absorptionBefore={} absorptionAfter={}",
            shortId(target.getUuid()),
            round2(effectiveDamage),
            round2(appliedDamage),
            round2(mitigatedDamage),
            round2(target.getArmor()),
            round2(target.getAttributeValue(EntityAttributes.ARMOR_TOUGHNESS)),
            round2(totalHealthBefore - target.getHealth()),
            round2(totalHealthAfter - target.getHealth()));

        if (config.chargeKnockbackStrength > 0.0d) {
            Vec3d knock = forward.multiply(config.chargeKnockbackStrength);
            target.addVelocity(knock.x, Math.max(0.15d, knock.y + 0.15d), knock.z);
            target.velocityModified = true;
        }

        runtime.lastHitTickByPlayer.put(target.getUuid(), serverTicks);
        runtime.lastHitPassByPlayer.put(target.getUuid(), runtime.passId);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] charge hit leviathan={} target={} damage={} speed={} passId={}",
            shortId(leviathan.getUuid()),
            shortId(target.getUuid()),
            round2(effectiveDamage),
            round2(sampledSpeed),
            runtime.passId);
    }

    private static double squaredDistancePointToSegment(Vec3d point, Vec3d segmentStart, Vec3d segmentEnd) {
        Vec3d segment = segmentEnd.subtract(segmentStart);
        double segmentLenSq = segment.lengthSquared();
        if (segmentLenSq < 1.0e-12d) {
            return point.squaredDistanceTo(segmentStart);
        }
        double t = point.subtract(segmentStart).dotProduct(segment) / segmentLenSq;
        t = clamp(t, 0.0d, 1.0d);
        Vec3d closest = segmentStart.add(segment.multiply(t));
        return point.squaredDistanceTo(closest);
    }

    private static void applyLoadedPassiveMovement(ServerWorld world, Entity entity, VirtualLeviathanStore.VirtualLeviathanState state) {
        Vec3d heading = normalizeXZ(state.headingX(), state.headingZ());
        Vec3d steered = chooseSubmergedDirection(world, posOf(entity), heading);
        double speed = Math.max(0.01d, config.virtualSpeedBlocksPerTick);

        entity.setVelocity(steered.x * speed, steered.y * speed, steered.z * speed);
        faceAlong(entity, steered);
    }

    private static void circleAroundAnchor(ServerWorld world, Entity entity, Vec3d anchor, double radius, double speed) {
        double safeRadius = Math.max(8.0d, radius);
        double angle = (serverTicks % 3600L) * 0.055d;
        Vec3d orbitPoint = new Vec3d(
            anchor.x + Math.cos(angle) * safeRadius,
            anchor.y,
            anchor.z + Math.sin(angle) * safeRadius
        );

        Vec3d desired = orbitPoint.subtract(posOf(entity));
        if (desired.length() < 1.0e-6d) {
            return;
        }

        Vec3d steered = chooseSubmergedDirection(world, posOf(entity), desired.normalize());
        entity.setVelocity(steered.multiply(Math.max(0.01d, speed)));
        faceAlong(entity, steered);
    }

    private static ServerPlayerEntity findNearestTarget(ServerWorld world, Vec3d pos, int horizontalRadius, int verticalRadius) {
        double bestSq = Double.POSITIVE_INFINITY;
        ServerPlayerEntity best = null;
        double maxHorizontalSq = (double) horizontalRadius * (double) horizontalRadius;
        double maxVertical = Math.max(1.0d, (double) verticalRadius);

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Vec3d playerPos = posOf(player);
            double dx = playerPos.x - pos.x;
            double dz = playerPos.z - pos.z;
            double horizontalSq = dx * dx + dz * dz;
            if (horizontalSq > maxHorizontalSq) {
                continue;
            }
            double dy = Math.abs(playerPos.y - pos.y);
            if (dy > maxVertical) {
                continue;
            }
            if (horizontalSq < bestSq) {
                bestSq = horizontalSq;
                best = player;
            }
        }

        return best;
    }

    private static boolean isWithinEngagementRange(Vec3d from, Vec3d to, int horizontalRadius, int verticalRadius) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double horizontalSq = dx * dx + dz * dz;
        double maxHorizontalSq = (double) horizontalRadius * (double) horizontalRadius;
        if (horizontalSq > maxHorizontalSq) {
            return false;
        }
        double dy = Math.abs(to.y - from.y);
        return dy <= Math.max(1.0d, (double) verticalRadius);
    }

    private static boolean hasLineOfEngagement(ServerWorld world, Entity from, ServerPlayerEntity target) {
        HitResult hit = world.raycast(new RaycastContext(
            from.getEyePos(),
            target.getEyePos(),
            RaycastContext.ShapeType.COLLIDER,
            RaycastContext.FluidHandling.NONE,
            from
        ));
        return hit.getType() == HitResult.Type.MISS;
    }

    private static Vec3d chooseSubmergedDirection(ServerWorld world, Vec3d start, Vec3d preferred) {
        return chooseSubmergedDirection(world, start, preferred, false);
    }

    private static Vec3d chooseSubmergedDirection(ServerWorld world, Vec3d start, Vec3d preferred, boolean aggressiveAvoidance) {
        Vec3d normalized = normalizeVectorStrict(preferred);
        int probeDistance = Math.max(1, config.solidAvoidanceProbeDistanceBlocks);
        if (aggressiveAvoidance) {
            int chargeLookahead = Math.max(8, (int) Math.ceil(config.chargeSpeedBlocksPerTick * 12.0d));
            probeDistance = Math.max(probeDistance, chargeLookahead);
        }

        if (pathIsSubmergedClear(world, start, normalized, probeDistance)) {
            return normalized;
        }
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged path blocked, probing alternatives start=({}, {}, {}) preferred=({}, {}, {})",
            round1(start.x),
            round1(start.y),
            round1(start.z),
            round2(normalized.x),
            round2(normalized.y),
            round2(normalized.z));

        double baseYaw = Math.atan2(normalized.z, normalized.x);
        int[] yawOffsets = aggressiveAvoidance
            ? new int[] {15, -15, 30, -30, 45, -45, 65, -65, 90, -90, 120, -120, 150, -150, 170, -170}
            : new int[] {20, -20, 40, -40, 60, -60, 80, -80, 110, -110, 150, -150};
        double[] verticalOffsets = aggressiveAvoidance
            ? new double[] {0.0, 0.25, -0.25, 0.45, -0.45, 0.65, -0.65, 0.8, -0.8}
            : new double[] {0.0, 0.2, -0.2, 0.35, -0.35, 0.5, -0.5};

        for (int yawOffset : yawOffsets) {
            double yaw = baseYaw + Math.toRadians(yawOffset * config.solidAvoidanceTurnStrength);
            for (double vertical : verticalOffsets) {
                Vec3d candidate = normalizeVectorStrict(new Vec3d(Math.cos(yaw), vertical, Math.sin(yaw)));
                if (pathIsSubmergedClear(world, start, candidate, probeDistance)) {
                    AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged path alternative chosen candidate=({}, {}, {})",
                        round2(candidate.x),
                        round2(candidate.y),
                        round2(candidate.z));
                    return candidate;
                }
            }
        }

        if (aggressiveAvoidance) {
            Vec3d upForward = normalizeVectorStrict(new Vec3d(normalized.x * 0.4d, 0.9d, normalized.z * 0.4d));
            if (pathIsSubmergedClear(world, start, upForward, probeDistance)) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged emergency path chosen upForward=({}, {}, {})",
                    round2(upForward.x),
                    round2(upForward.y),
                    round2(upForward.z));
                return upForward;
            }

            Vec3d downForward = normalizeVectorStrict(new Vec3d(normalized.x * 0.4d, -0.9d, normalized.z * 0.4d));
            if (pathIsSubmergedClear(world, start, downForward, probeDistance)) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged emergency path chosen downForward=({}, {}, {})",
                    round2(downForward.x),
                    round2(downForward.y),
                    round2(downForward.z));
                return downForward;
            }

            Vec3d side = normalizeVectorStrict(new Vec3d(-normalized.z, 0.0d, normalized.x));
            if (pathIsSubmergedClear(world, start, side, probeDistance)) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged emergency path chosen side=({}, {}, {})",
                    round2(side.x),
                    round2(side.y),
                    round2(side.z));
                return side;
            }

            Vec3d oppositeSide = side.multiply(-1.0d);
            if (pathIsSubmergedClear(world, start, oppositeSide, probeDistance)) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged emergency path chosen oppositeSide=({}, {}, {})",
                    round2(oppositeSide.x),
                    round2(oppositeSide.y),
                    round2(oppositeSide.z));
                return oppositeSide;
            }
        }

        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] submerged path fallback to preferred vector");
        return normalized;
    }

    private static boolean pathIsSubmergedClear(ServerWorld world, Vec3d start, Vec3d direction, int probeDistance) {
        int maxProbe = Math.max(1, probeDistance);
        int verticalClearance = Math.max(0, config.solidAvoidanceVerticalClearanceBlocks);

        for (int i = 1; i <= maxProbe; i++) {
            Vec3d sample = start.add(direction.multiply(i));
            BlockPos base = BlockPos.ofFloored(sample);

            if (!isChunkLoadedForBlock(world, base)) {
                return false;
            }

            if (config.requireWaterForSpawn && !world.getFluidState(base).isOf(Fluids.WATER)) {
                return false;
            }

            BlockState at = world.getBlockState(base);
            if (at.isSolidBlock(world, base) && !world.getFluidState(base).isOf(Fluids.WATER)) {
                return false;
            }

            for (int up = 1; up <= verticalClearance; up++) {
                BlockPos check = base.up(up);
                if (!isChunkLoadedForBlock(world, check)) {
                    return false;
                }
                BlockState bs = world.getBlockState(check);
                if (bs.isSolidBlock(world, check) && !world.getFluidState(check).isOf(Fluids.WATER)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static Vec3d normalizeVectorStrict(Vec3d vector) {
        if (vector == null || !Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalStateException("Non-finite direction vector");
        }
        double len = vector.length();
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Zero-length direction vector");
        }
        return vector.multiply(1.0d / len);
    }

    private static void faceAlong(Entity entity, Vec3d direction) {
        Vec3d normalized = normalizeVectorStrict(direction);
        float yaw = (float) Math.toDegrees(Math.atan2(normalized.z, normalized.x)) - 90.0f;
        double horizontal = Math.sqrt(normalized.x * normalized.x + normalized.z * normalized.z);
        float pitch = (float) -Math.toDegrees(Math.atan2(normalized.y, horizontal));
        entity.setYaw(yaw);
        entity.setBodyYaw(yaw);
        entity.setHeadYaw(yaw);
        entity.setPitch(pitch);
    }

    private static VirtualLeviathanStore.VirtualLeviathanState syncStateFromLoaded(Entity entity, UUID id) {
        Vec3d velocity = entity.getVelocity();
        Vec3d heading = resolveHeadingForSync(entity, id, velocity);
        VirtualLeviathanStore.VirtualLeviathanState existing = virtualStore == null ? null : virtualStore.get(id);
        if (existing != null) {
            return new VirtualLeviathanStore.VirtualLeviathanState(
                id,
                posOf(entity),
                heading.x,
                heading.z,
                serverTicks,
                existing.entityTypeId(),
                existing.spawnYAtCreation(),
                existing.scaleMultiplier(),
                existing.damageMultiplier(),
                existing.healthMultiplier()
            );
        }

        String entityTypeId = Registries.ENTITY_TYPE.getId(entity.getType()).toString();
        double spawnY = entity.getY();
        return new VirtualLeviathanStore.VirtualLeviathanState(
            id,
            posOf(entity),
            heading.x,
            heading.z,
            serverTicks,
            entityTypeId,
            spawnY,
            computeSpawnScaleMultiplier(spawnY, entityTypeId),
            computeSpawnDamageMultiplier(spawnY),
            computeSpawnHealthMultiplier(spawnY)
        );
    }

    private static Vec3d resolveHeadingForSync(Entity entity, UUID id, Vec3d velocity) {
        double vx = velocity == null ? 0.0d : velocity.x;
        double vz = velocity == null ? 0.0d : velocity.z;
        double speedSq = vx * vx + vz * vz;
        if (speedSq >= 1.0e-6d) {
            return normalizeHeadingStrict(vx, vz, id);
        }

        CombatRuntime runtime = combatRuntimeById.get(id);
        if (runtime != null) {
            double rx = runtime.chargeDirection.x;
            double rz = runtime.chargeDirection.z;
            double rSq = rx * rx + rz * rz;
            if (rSq >= 1.0e-6d) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] sync heading fallback using combat direction id={}", shortId(id));
                return normalizeHeadingStrict(rx, rz, id);
            }
        }

        VirtualLeviathanStore.VirtualLeviathanState existing = virtualStore == null ? null : virtualStore.get(id);
        if (existing != null) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] sync heading fallback using virtual heading id={}", shortId(id));
            return normalizeHeadingStrict(existing.headingX(), existing.headingZ(), id);
        }

        double yawRadians = Math.toRadians(entity.getYaw() + 90.0d);
        double yx = Math.cos(yawRadians);
        double yz = Math.sin(yawRadians);
        double ySq = yx * yx + yz * yz;
        if (ySq >= 1.0e-6d) {
            AtlantisMod.LOGGER.warn("[Atlantis][leviathan] sync heading fallback using entity yaw id={} yaw={}", shortId(id), round1(entity.getYaw()));
            return normalizeHeadingStrict(yx, yz, id);
        }

        throw new IllegalStateException("Unable to resolve non-zero heading for loaded leviathan id=" + id);
    }

    private static Vec3d posOf(Entity entity) {
        return new Vec3d(entity.getX(), entity.getY(), entity.getZ());
    }

    private static void reconcileLoadedToVirtual(Map<UUID, Entity> loadedById) {
        for (Map.Entry<UUID, Entity> entry : loadedById.entrySet()) {
            UUID id = entry.getKey();
            Entity entity = entry.getValue();
            if (entity == null || !entity.isAlive()) {
                continue;
            }
            if (virtualStore.get(id) != null) {
                continue;
            }

            VirtualLeviathanStore.VirtualLeviathanState rebuilt = syncStateFromLoaded(entity, id);
            virtualStore.upsert(rebuilt);
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] recovered missing virtual state id={} from loaded entity pos=({}, {}, {})",
                shortId(id),
                round1(rebuilt.pos().x),
                round1(rebuilt.pos().y),
                round1(rebuilt.pos().z));
        }
    }

    private static Map<UUID, Entity> loadedManagedById(ServerWorld world) {
        Map<UUID, Entity> loaded = new HashMap<>();
        for (Entity entity : world.iterateEntities()) {
            if (!isManaged(entity)) {
                continue;
            }

            Optional<UUID> id = LeviathanIdTags.getId(entity);
            if (id.isEmpty()) {
                UUID assigned = UUID.randomUUID();
                entity.addCommandTag(LeviathanIdTags.toTag(assigned));
                id = Optional.of(assigned);
                AtlantisMod.LOGGER.warn("[Atlantis][leviathan] managed entity missing id tag; assigned id={} uuid={}",
                    shortId(assigned),
                    entity.getUuidAsString());
            }

            loaded.put(id.get(), entity);
        }
        AtlantisMod.LOGGER.debug("[Atlantis][leviathan] loaded managed scan complete count={}", loaded.size());
        return loaded;
    }

    public static boolean isManaged(Entity entity) {
        return entity.getCommandTags().contains(MANAGED_TAG);
    }

    private static void applyConfiguredEntityTypes(LeviathansConfig newConfig) {
        configuredEntityTypesById.clear();
        List<String> ids = new ArrayList<>();
        for (String entityTypeId : newConfig.entityTypeIds) {
            EntityType<?> entityType = resolveConfiguredEntityType(entityTypeId);
            validateScaleCompatibility(entityType, entityTypeId);
            configuredEntityTypesById.put(entityTypeId, entityType);
            ids.add(entityTypeId);
        }
        if (ids.isEmpty()) {
            throw new IllegalStateException("No configured leviathan entity types available after validation");
        }
        configuredEntityTypeIds = List.copyOf(ids);
        AtlantisMod.LOGGER.info("[Atlantis][leviathan] configured entity type pool size={} ids={}", configuredEntityTypeIds.size(), configuredEntityTypeIds);
    }

    private static String selectRandomEntityTypeId(Random random) {
        if (configuredEntityTypeIds.isEmpty()) {
            throw new IllegalStateException("No configured leviathan entity types available for selection");
        }
        int index = random.nextInt(configuredEntityTypeIds.size());
        return configuredEntityTypeIds.get(index);
    }

    private static double computeDepthProgress(double spawnY) {
        double top = (double) config.depthScaleTopY;
        double bottom = (double) config.depthScaleBottomY;
        if (bottom >= top) {
            return 0.0d;
        }
        double clamped = clamp(spawnY, bottom, top);
        return (top - clamped) / (top - bottom);
    }

    private static double lerp(double start, double end, double t) {
        return start + ((end - start) * clamp(t, 0.0d, 1.0d));
    }

    private static double computeSpawnScaleMultiplier(double spawnY) {
        return lerp(config.depthScaleAtTop, config.depthScaleAtBottom, computeDepthProgress(spawnY));
    }

    private static double computeSpawnScaleMultiplier(double spawnY, String entityTypeId) {
        double depthScaleMultiplier = computeSpawnScaleMultiplier(spawnY);
        return depthScaleMultiplier * resolveEntityTypeScaleBias(entityTypeId);
    }

    private static double resolveEntityTypeScaleBias(String entityTypeId) {
        if (SQUID_ENTITY_TYPE_ID.equals(entityTypeId)) {
            return SQUID_SCALE_TYPE_BIAS;
        }
        if (GLOW_SQUID_ENTITY_TYPE_ID.equals(entityTypeId)) {
            return GLOW_SQUID_SCALE_TYPE_BIAS;
        }
        return DEFAULT_FISH_SCALE_TYPE_BIAS;
    }

    private static double computeSpawnDamageMultiplier(double spawnY) {
        return lerp(config.depthDamageAtTop, config.depthDamageAtBottom, computeDepthProgress(spawnY));
    }

    private static double computeSpawnHealthMultiplier(double spawnY) {
        double depthHealthMultiplier = lerp(config.depthHealthAtTop, config.depthHealthAtBottom, computeDepthProgress(spawnY));
        double strength = Math.max(MIN_HEALTH_SCALING_STRENGTH, config.entityScale * HEALTH_SCALING_STRENGTH_FROM_ENTITY_SCALE);
        return depthHealthMultiplier * strength;
    }

    private static double resolveEffectiveChargeDamage(Entity leviathan) {
        Optional<UUID> id = LeviathanIdTags.getId(leviathan);
        if (id.isPresent() && virtualStore != null) {
            VirtualLeviathanStore.VirtualLeviathanState state = virtualStore.get(id.get());
            if (state != null) {
                return config.chargeDamage * state.damageMultiplier();
            }
        }
        return config.chargeDamage;
    }

    private static EntityType<?> resolveConfiguredEntityType(String entityTypeId) {
        Identifier id = Identifier.tryParse(entityTypeId);
        if (id == null) {
            throw new IllegalStateException("Configured entityTypeId invalid: " + entityTypeId);
        }
        if (!Registries.ENTITY_TYPE.containsId(id)) {
            throw new IllegalStateException("Configured entityTypeId not found: " + entityTypeId);
        }
        return Registries.ENTITY_TYPE.get(id);
    }

    private static void validateScaleCompatibility(EntityType<?> entityType, String entityTypeId) {
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) entityType;
        DefaultAttributeContainer container = DefaultAttributeRegistry.get(livingType);
        if (container == null) {
            throw new IllegalStateException("entityScale requires LivingEntity type with default attributes, but configured entity is not supported: " + entityTypeId);
        }

        try {
            container.getBaseValue(EntityAttributes.SCALE);
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] entity scale compatibility validated entityTypeId={} scale={}", entityTypeId, config.entityScale);
        } catch (Exception e) {
            throw new IllegalStateException("entityScale requires SCALE attribute support, but entity type lacks SCALE attribute: " + entityTypeId, e);
        }
    }

    private static boolean isAnyPlayerNear(ServerWorld world, Vec3d pos, int radiusBlocks) {
        if (radiusBlocks <= 0) {
            return false;
        }
        double r2 = (double) radiusBlocks * (double) radiusBlocks;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            double dx = player.getX() - pos.x;
            double dy = player.getY() - pos.y;
            double dz = player.getZ() - pos.z;
            if ((dx * dx + dy * dy + dz * dz) <= r2) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWaterValid(ServerWorld world, Vec3d pos) {
        if (!config.requireWaterForSpawn) {
            return true;
        }

        BlockPos at = BlockPos.ofFloored(pos);
        if (!isChunkLoadedForBlock(world, at)) {
            return false;
        }
        if (!world.getFluidState(at).isOf(Fluids.WATER)) {
            return false;
        }

        int requiredDepth = Math.max(1, config.minWaterDepthBlocksForTravel);
        for (int i = 0; i < requiredDepth; i++) {
            BlockPos check = at.down(i);
            if (!isChunkLoadedForBlock(world, check)) {
                return false;
            }
            if (!world.getFluidState(check).isOf(Fluids.WATER)) {
                return false;
            }
        }

        int surfaceY = world.getTopY(Heightmap.Type.WORLD_SURFACE, at.getX(), at.getZ());
        return at.getY() <= surfaceY;
    }

    private static BlockPos resolveWorldSpawn(ServerWorld world) {
        WorldProperties.SpawnPoint spawnPoint = null;
        if (world.getServer() != null) {
            spawnPoint = world.getServer().getSpawnPoint();
        }
        if (spawnPoint == null) {
            spawnPoint = world.getLevelProperties().getSpawnPoint();
        }
        return spawnPoint != null ? spawnPoint.getPos() : BlockPos.ORIGIN;
    }

    private static Vec3d normalizeHeadingStrict(double x, double z, UUID id) {
        if (!Double.isFinite(x) || !Double.isFinite(z)) {
            throw new IllegalStateException("Invalid non-finite heading for leviathan id=" + id);
        }
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Invalid zero heading for leviathan id=" + id);
        }
        return new Vec3d(x / len, 0.0, z / len);
    }

    private static Vec3d normalizeXZ(double x, double z) {
        double len = Math.sqrt(x * x + z * z);
        if (len < 1.0e-6d) {
            throw new IllegalStateException("Heading normalization failed (zero vector)");
        }
        return new Vec3d(x / len, 0.0, z / len);
    }

    private static double wrapRadians(double radians) {
        while (radians > Math.PI) {
            radians -= (Math.PI * 2.0);
        }
        while (radians < -Math.PI) {
            radians += (Math.PI * 2.0);
        }
        return radians;
    }

    private static boolean isChunkLoadedForBlock(ServerWorld world, BlockPos pos) {
        return world.getChunkManager().isChunkLoaded(pos.getX() >> 4, pos.getZ() >> 4);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, 8);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static int getActivationRadiusBlocks() {
        return Math.max(1, effectiveActivationRadiusBlocks);
    }

    private static int getDespawnRadiusBlocks() {
        return Math.max(getActivationRadiusBlocks() + 1, effectiveDespawnRadiusBlocks);
    }

    private static int getMinSpawnDistanceBlocks() {
        return Math.max(0, effectiveMinSpawnDistanceBlocks);
    }

    private static void updateEffectiveDistancesFromServerSettings(MinecraftServer server) {
        if (!config.autoDistancesFromServer || server == null) {
            effectiveActivationRadiusBlocks = config.activationRadiusBlocks;
            effectiveDespawnRadiusBlocks = config.despawnRadiusBlocks;
            effectiveMinSpawnDistanceBlocks = config.minSpawnDistanceBlocks;
            warnedAutoDistanceAdjustment = false;
            return;
        }

        if (initialServerViewDistanceChunks < 0) {
            initialServerViewDistanceChunks = Math.max(2, server.getPlayerManager().getViewDistance());
            AtlantisMod.LOGGER.info("[Atlantis][leviathan] captured initial server view distance={} chunks ({} blocks)",
                initialServerViewDistanceChunks,
                initialServerViewDistanceChunks * 16);
        }

        int viewChunks = initialServerViewDistanceChunks;
        int viewBlocks = viewChunks * 16;

        int activation = Math.max(64, viewBlocks + 16);
        int despawn = Math.max(activation + 64, config.despawnRadiusBlocks);
        int minSpawn = 0;

        warnedAutoDistanceAdjustment = false;

        effectiveActivationRadiusBlocks = activation;
        effectiveDespawnRadiusBlocks = despawn;
        effectiveMinSpawnDistanceBlocks = minSpawn;

    }

    private static boolean isSpawnReady(ServerWorld world, UUID id, ChunkPos centerChunk) {
        if (!config.forceChunkLoadingEnabled || chunkPreloader == null) {
            boolean loaded = world.getChunkManager().isChunkLoaded(centerChunk.x, centerChunk.z);
            if (!loaded) {
                AtlantisMod.LOGGER.debug("[Atlantis][leviathan] spawn readiness false (naturally unloaded) id={} chunk=({}, {})", shortId(id), centerChunk.x, centerChunk.z);
            }
            return loaded;
        }
        boolean loaded = chunkPreloader.isChunkLoaded(world, centerChunk);
        if (!loaded) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] spawn readiness false (preloader pending) id={} chunk=({}, {})", shortId(id), centerChunk.x, centerChunk.z);
        }
        return loaded;
    }

    private static Set<ChunkPos> computeDesiredChunks(VirtualLeviathanStore.VirtualLeviathanState state) {
        Set<ChunkPos> out = new LinkedHashSet<>();
        ChunkPos center = new ChunkPos(BlockPos.ofFloored(state.pos()));

        int radius = Math.max(0, config.preloadRadiusChunks);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                out.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }

        int ahead = Math.max(0, config.preloadAheadChunks);
        for (int i = 1; i <= ahead; i++) {
            int ax = center.x + (int) Math.round((state.headingX() * i));
            int az = center.z + (int) Math.round((state.headingZ() * i));
            out.add(new ChunkPos(ax, az));
        }

        return out;
    }
}
