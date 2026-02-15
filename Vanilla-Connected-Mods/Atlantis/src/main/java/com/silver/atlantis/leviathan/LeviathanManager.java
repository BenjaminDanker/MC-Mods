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

    private static volatile boolean initialized;
    private static LeviathansConfig config;
    private static VirtualLeviathanStore virtualStore;
    private static EntityType<?> configuredEntityType;
    private static LeviathanChunkPreloader chunkPreloader;
    private static final Map<UUID, CombatRuntime> combatRuntimeById = new HashMap<>();
    private static final Set<UUID> inactiveChunkReleaseDone = new LinkedHashSet<>();

    private static long serverTicks;
    private static long lastEnsureTick;
    private static long lastRecoveryTick;
    private static int effectiveActivationRadiusBlocks;
    private static int effectiveDespawnRadiusBlocks;
    private static int effectiveMinSpawnDistanceBlocks;

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
        private long missingTargetUntilTick = -1L;
        private final Map<UUID, Long> lastHitTickByPlayer = new HashMap<>();
        private final Map<UUID, Long> lastHitPassByPlayer = new HashMap<>();

        private void resetToPassive() {
            substate = CombatSubstate.ACQUIRE;
            targetUuid = null;
            anchorPos = null;
            cooldownUntilTick = 0L;
            invalidLineTicks = 0L;
            missingTargetUntilTick = -1L;
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
        configuredEntityType = resolveConfiguredEntityType(newConfig.entityTypeId);
        validateScaleCompatibility(configuredEntityType, newConfig.entityTypeId);
        chunkPreloader = new LeviathanChunkPreloader(newConfig.preloadTicketLevel);
        effectiveActivationRadiusBlocks = newConfig.activationRadiusBlocks;
        effectiveDespawnRadiusBlocks = newConfig.despawnRadiusBlocks;
        effectiveMinSpawnDistanceBlocks = newConfig.minSpawnDistanceBlocks;

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
        configuredEntityType = resolveConfiguredEntityType(newConfig.entityTypeId);
        validateScaleCompatibility(configuredEntityType, newConfig.entityTypeId);
        chunkPreloader = new LeviathanChunkPreloader(newConfig.preloadTicketLevel);
        inactiveChunkReleaseDone.clear();
        effectiveActivationRadiusBlocks = newConfig.activationRadiusBlocks;
        effectiveDespawnRadiusBlocks = newConfig.despawnRadiusBlocks;
        effectiveMinSpawnDistanceBlocks = newConfig.minSpawnDistanceBlocks;
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
                " - id=%s pos=(%.1f, %.1f, %.1f) heading=(%.2f, %.2f)",
                shortId(state.id()),
                state.pos().x,
                state.pos().y,
                state.pos().z,
                state.headingX(),
                state.headingZ());
            source.sendFeedback(() -> Text.literal(line), false);

            if (includeLoaded) {
                Entity entity = loaded.get(state.id());
                if (entity != null) {
                    CombatRuntime runtime = combatRuntimeById.get(state.id());
                    String engagementState = runtime == null || runtime.targetUuid == null ? "PASSIVE" : "ENGAGED/" + runtime.substate;
                    String target = runtime == null || runtime.targetUuid == null ? "<none>" : shortId(runtime.targetUuid);
                    String loadedLine = String.format(Locale.ROOT,
                        "   loadedPos=(%.1f, %.1f, %.1f) engagement=%s target=%s",
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
        if (config == null || virtualStore == null || configuredEntityType == null) {
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
        combatRuntimeById.keySet().retainAll(loadedById.keySet());
        int chunkBudget = config.forceChunkLoadingEnabled ? config.maxChunkLoadsPerTick : 0;

        if (serverTicks - lastRecoveryTick >= RECOVERY_INTERVAL_TICKS) {
            reconcileLoadedToVirtual(loadedById);
            lastRecoveryTick = serverTicks;
        }

        List<VirtualLeviathanStore.VirtualLeviathanState> snapshot = virtualStore.snapshot();
        for (VirtualLeviathanStore.VirtualLeviathanState state : snapshot) {
            Entity loaded = loadedById.get(state.id());
            if (loaded == null) {
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
    }

    private static void ensureMinimumPopulation(ServerWorld world) {
        int deficit = config.minimumLeviathans - virtualStore.size();
        if (deficit <= 0) {
            return;
        }

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

        Vec3d chosenPos = null;
        for (int attempt = 0; attempt < 64; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = config.roamMinDistanceBlocks
                + random.nextDouble() * Math.max(1.0, (double) (config.roamMaxDistanceBlocks - config.roamMinDistanceBlocks));

            double x = spawn.getX() + Math.cos(angle) * distance;
            double z = spawn.getZ() + Math.sin(angle) * distance;
            int yJitter = config.spawnYRandomRange <= 0 ? 0 : random.nextInt(config.spawnYRandomRange * 2 + 1) - config.spawnYRandomRange;
            double y = config.spawnY + yJitter;

            Vec3d candidate = new Vec3d(
                clamp(x, border.getBoundWest() + 8.0, border.getBoundEast() - 8.0),
                y,
                clamp(z, border.getBoundNorth() + 8.0, border.getBoundSouth() - 8.0));

            if (isWaterValid(world, candidate)) {
                chosenPos = candidate;
                break;
            }
        }

        if (chosenPos == null) {
            throw new IllegalStateException("Unable to sample valid virtual leviathan spawn position after 64 attempts");
        }

        double angle = random.nextDouble() * Math.PI * 2.0;
        return new VirtualLeviathanStore.VirtualLeviathanState(
            UUID.randomUUID(),
            chosenPos,
            Math.cos(angle),
            Math.sin(angle),
            serverTicks
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

        Vec3d submergedSteered = chooseSubmergedDirection(world, moved, new Vec3d(steered.x, 0.0, steered.z));
        Vec3d corrected = new Vec3d(moved.x, moved.y, moved.z);
        return new VirtualLeviathanStore.VirtualLeviathanState(state.id(), corrected, submergedSteered.x, submergedSteered.z, serverTicks);
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
            return;
        }
        if (!isAnyPlayerNear(world, state.pos(), getActivationRadiusBlocks())) {
            return;
        }
        if (isAnyPlayerNear(world, state.pos(), getMinSpawnDistanceBlocks())) {
            return;
        }

        Entity alreadyLoaded = loadedById.get(state.id());
        if (alreadyLoaded != null && alreadyLoaded.isAlive()) {
            return;
        }

        ChunkPos center = new ChunkPos(BlockPos.ofFloored(state.pos()));
        if (!isSpawnReady(world, state.id(), center)) {
            return;
        }
        if (!isWaterValid(world, state.pos())) {
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
        Entity entity = configuredEntityType.create(world, SpawnReason.EVENT);
        if (entity == null) {
            throw new IllegalStateException("Failed to create leviathan entity for configured entityTypeId=" + config.entityTypeId);
        }

        entity.refreshPositionAndAngles(state.pos().x, state.pos().y, state.pos().z, 0.0f, 0.0f);
        entity.addCommandTag(MANAGED_TAG);
        entity.addCommandTag(LeviathanIdTags.toTag(state.id()));

        if (!(entity instanceof LivingEntity living)) {
            throw new IllegalStateException("Configured leviathan entity type is not LivingEntity. entityTypeId=" + config.entityTypeId);
        }

        EntityAttributeInstance scale = living.getAttributeInstance(EntityAttributes.SCALE);
        if (scale == null) {
            throw new IllegalStateException("entityScale requires SCALE attribute support but entity has no SCALE attribute. entityTypeId=" + config.entityTypeId);
        }
        scale.setBaseValue(config.entityScale);

        if (entity instanceof MobEntity mob) {
            mob.setTarget(null);
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
            ServerPlayerEntity initialTarget = findNearestTarget(world, posOf(entity), config.engageRadiusBlocks);
            if (initialTarget == null) {
                runtime.previousPos = posOf(entity);
                return false;
            }
            runtime.targetUuid = initialTarget.getUuid();
            runtime.anchorPos = posOf(initialTarget);
            runtime.phaseStartTick = serverTicks;
            runtime.substate = CombatSubstate.ACQUIRE;
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

        if (entityPos.distanceTo(posOf(target)) > config.disengageRadiusBlocks) {
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
                detectChargeHit(world, entity, target, runtime, previousPos, entityPos);
                Vec3d steered = chooseSubmergedDirection(world, entityPos, runtime.chargeDirection);
                entity.setVelocity(steered.multiply(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if ((serverTicks - runtime.phaseStartTick) >= config.chargeDurationTicks) {
                    runtime.substate = CombatSubstate.PASS_THROUGH;
                    runtime.phaseStartTick = serverTicks;
                    runtime.cooldownUntilTick = serverTicks + config.chargeCooldownTicks;
                }
            }
            case PASS_THROUGH -> {
                Vec3d steered = chooseSubmergedDirection(world, entityPos, runtime.chargeDirection);
                entity.setVelocity(steered.multiply(config.chargeSpeedBlocksPerTick));
                faceAlong(entity, steered);

                if (entityPos.distanceTo(posOf(target)) >= config.passOvershootBlocks) {
                    runtime.substate = CombatSubstate.TURN_BACK;
                    runtime.phaseStartTick = serverTicks;
                }
            }
            case TURN_BACK -> {
                circleAroundAnchor(world, entity, posOf(target), Math.max(8.0d, config.passOvershootBlocks), config.virtualSpeedBlocksPerTick);
                if ((serverTicks - runtime.phaseStartTick) >= config.turnaroundTicks) {
                    runtime.substate = CombatSubstate.REACQUIRE;
                    runtime.phaseStartTick = serverTicks;
                }
            }
            case REACQUIRE -> {
                if (serverTicks >= runtime.cooldownUntilTick) {
                    runtime.substate = CombatSubstate.ACQUIRE;
                    runtime.phaseStartTick = serverTicks;
                } else {
                    circleAroundAnchor(world, entity, posOf(target), Math.max(8.0d, config.passOvershootBlocks), config.virtualSpeedBlocksPerTick);
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
        runtime.passId++;
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
        if (expanded.raycast(segmentStart, segmentEnd).isEmpty()) {
            return;
        }

        Vec3d toTarget = posOf(target).subtract(posOf(leviathan));
        double toTargetLen = toTarget.length();
        if (toTargetLen < 1.0e-6d) {
            return;
        }

        Vec3d toTargetNorm = toTarget.multiply(1.0d / toTargetLen);
        Vec3d forward = runtime.chargeDirection.length() < 1.0e-6d ? new Vec3d(1.0d, 0.0d, 0.0d) : runtime.chargeDirection.normalize();
        double dot = forward.dotProduct(toTargetNorm);
        if (dot < config.chargeDirectionDotThreshold) {
            return;
        }

        double speed = leviathan.getVelocity().length();
        if (speed < config.chargeMinHitSpeed) {
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
        if (leviathan instanceof LivingEntity living) {
            damaged = target.damage(world, world.getDamageSources().mobAttack(living), (float) config.chargeDamage);
        } else {
            damaged = target.damage(world, world.getDamageSources().generic(), (float) config.chargeDamage);
        }

        if (!damaged) {
            return;
        }

        if (config.chargeKnockbackStrength > 0.0d) {
            Vec3d knock = forward.multiply(config.chargeKnockbackStrength);
            target.addVelocity(knock.x, Math.max(0.15d, knock.y + 0.15d), knock.z);
            target.velocityModified = true;
        }

        runtime.lastHitTickByPlayer.put(target.getUuid(), serverTicks);
        runtime.lastHitPassByPlayer.put(target.getUuid(), runtime.passId);
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

    private static ServerPlayerEntity findNearestTarget(ServerWorld world, Vec3d pos, int radius) {
        double bestSq = Double.POSITIVE_INFINITY;
        ServerPlayerEntity best = null;
        double maxSq = (double) radius * (double) radius;

        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player == null || player.isSpectator() || !player.isAlive()) {
                continue;
            }
            Vec3d playerPos = posOf(player);
            double d2 = playerPos.squaredDistanceTo(pos);
            if (d2 <= maxSq && d2 < bestSq) {
                bestSq = d2;
                best = player;
            }
        }

        return best;
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
        Vec3d normalized = normalizeVectorStrict(preferred);
        if (pathIsSubmergedClear(world, start, normalized, config.solidAvoidanceProbeDistanceBlocks)) {
            return normalized;
        }

        double baseYaw = Math.atan2(normalized.z, normalized.x);
        int[] yawOffsets = new int[] {20, -20, 40, -40, 60, -60, 80, -80, 110, -110, 150, -150};
        double[] verticalOffsets = new double[] {0.0, 0.2, -0.2, 0.35, -0.35, 0.5, -0.5};

        for (int yawOffset : yawOffsets) {
            double yaw = baseYaw + Math.toRadians(yawOffset * config.solidAvoidanceTurnStrength);
            for (double vertical : verticalOffsets) {
                Vec3d candidate = normalizeVectorStrict(new Vec3d(Math.cos(yaw), vertical, Math.sin(yaw)));
                if (pathIsSubmergedClear(world, start, candidate, config.solidAvoidanceProbeDistanceBlocks)) {
                    return candidate;
                }
            }
        }

        return normalized;
    }

    private static boolean pathIsSubmergedClear(ServerWorld world, Vec3d start, Vec3d direction, int probeDistance) {
        int maxProbe = Math.max(1, probeDistance);
        int verticalClearance = Math.max(0, config.solidAvoidanceVerticalClearanceBlocks);

        for (int i = 1; i <= maxProbe; i++) {
            Vec3d sample = start.add(direction.multiply(i));
            BlockPos base = BlockPos.ofFloored(sample);

            if (config.requireWaterForSpawn && !world.getFluidState(base).isOf(Fluids.WATER)) {
                return false;
            }

            BlockState at = world.getBlockState(base);
            if (at.isSolidBlock(world, base) && !world.getFluidState(base).isOf(Fluids.WATER)) {
                return false;
            }

            for (int up = 1; up <= verticalClearance; up++) {
                BlockPos check = base.up(up);
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
        float yaw = (float) Math.toDegrees(Math.atan2(direction.z, direction.x));
        entity.setYaw(yaw);
        entity.setBodyYaw(yaw);
        entity.setHeadYaw(yaw);
    }

    private static VirtualLeviathanStore.VirtualLeviathanState syncStateFromLoaded(Entity entity, UUID id) {
        Vec3d velocity = entity.getVelocity();
        Vec3d heading = normalizeHeadingStrict(velocity.x, velocity.z, id);
        return new VirtualLeviathanStore.VirtualLeviathanState(id, posOf(entity), heading.x, heading.z, serverTicks);
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
        return loaded;
    }

    public static boolean isManaged(Entity entity) {
        return entity.getCommandTags().contains(MANAGED_TAG);
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
        if (!LivingEntity.class.isAssignableFrom(entityType.getBaseClass())) {
            throw new IllegalStateException("entityScale requires LivingEntity type, but configured entity is not LivingEntity: " + entityTypeId);
        }

        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) entityType;
        DefaultAttributeContainer container = DefaultAttributeRegistry.get(livingType);
        if (container == null) {
            throw new IllegalStateException("No default attribute container found for configured entity type: " + entityTypeId);
        }

        try {
            container.getBaseValue(EntityAttributes.SCALE);
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
        if (!world.getFluidState(at).isOf(Fluids.WATER)) {
            return false;
        }

        int requiredDepth = Math.max(1, config.minWaterDepthBlocksForTravel);
        for (int i = 0; i < requiredDepth; i++) {
            BlockPos check = at.down(i);
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
            return;
        }

        int simChunks = Math.max(2, server.getPlayerManager().getSimulationDistance());
        int viewChunks = Math.max(simChunks, server.getPlayerManager().getViewDistance());

        int activation = Math.max(64, simChunks * 16);
        int despawn = Math.max(activation + 64, (simChunks + 4) * 16);
        int minSpawn = Math.max(0, viewChunks * 16);

        effectiveActivationRadiusBlocks = activation;
        effectiveDespawnRadiusBlocks = despawn;
        effectiveMinSpawnDistanceBlocks = minSpawn;
    }

    private static boolean isSpawnReady(ServerWorld world, UUID id, ChunkPos centerChunk) {
        if (!config.forceChunkLoadingEnabled || chunkPreloader == null) {
            return world.getChunkManager().isChunkLoaded(centerChunk.x, centerChunk.z);
        }
        return chunkPreloader.isChunkLoaded(world, centerChunk);
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
