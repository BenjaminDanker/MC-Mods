package com.silver.atlantis.leviathan;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
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
    private static final Map<UUID, LeviathanCombatRuntime> combatRuntimeById = new HashMap<>();
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
        LeviathanManagedEntityIndex.reset();

        if (!initialized) {
            LeviathanManagedEntityIndex.registerHooks();
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
        LeviathanManagedEntityIndex.reset();
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
                    LeviathanCombatRuntime runtime = combatRuntimeById.get(state.id());
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
        List<ServerPlayerEntity> activePlayers = overworld.getPlayers(player -> player != null && !player.isSpectator() && player.isAlive());

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
                    && isAnyPlayerNear(activePlayers, state.pos(), getActivationRadiusBlocks())) {
                    retireAndReplaceVirtualLeviathan(overworld, state, "loaded instance disappeared near players (likely killed)");
                    continue;
                }

                VirtualLeviathanStore.VirtualLeviathanState advanced = advanceVirtualState(overworld, state);
                virtualStore.upsert(advanced);

                if (config.forceChunkLoadingEnabled && chunkPreloader != null) {
                    if (isAnyPlayerNear(activePlayers, advanced.pos(), getActivationRadiusBlocks())) {
                        Set<ChunkPos> desired = computeDesiredChunks(advanced);
                        chunkBudget -= chunkPreloader.request(overworld, advanced.id(), desired, serverTicks, chunkBudget);
                        inactiveChunkReleaseDone.remove(advanced.id());
                    } else if (inactiveChunkReleaseDone.add(advanced.id())) {
                        chunkPreloader.release(overworld, advanced.id());
                    }
                }

                tryMaterialize(overworld, activePlayers, advanced, loadedById);
                continue;
            }

            inactiveChunkReleaseDone.remove(state.id());

            LeviathanCombatRuntime runtime = combatRuntimeById.computeIfAbsent(state.id(), id -> {
                LeviathanCombatRuntime created = new LeviathanCombatRuntime();
                created.previousPos = posOf(loaded);
                return created;
            });

            boolean engaged = updateCombat(overworld, loaded, runtime);
            if (!engaged) {
                applyLoadedPassiveMovement(overworld, loaded, state);
            }

            VirtualLeviathanStore.VirtualLeviathanState sync = syncStateFromLoaded(loaded, state.id());

            if (!isAnyPlayerNear(activePlayers, sync.pos(), getDespawnRadiusBlocks())) {
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
                                       List<ServerPlayerEntity> activePlayers,
                                       VirtualLeviathanStore.VirtualLeviathanState state,
                                       Map<UUID, Entity> loadedById) {
        if (loadedById.containsKey(state.id())) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped already loaded id={}", shortId(state.id()));
            return;
        }
        if (!isAnyPlayerNear(activePlayers, state.pos(), getActivationRadiusBlocks())) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] materialize skipped no nearby player id={} activationRadius={}", shortId(state.id()), getActivationRadiusBlocks());
            return;
        }
        if (isAnyPlayerNear(activePlayers, state.pos(), getMinSpawnDistanceBlocks())) {
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

    private static boolean updateCombat(ServerWorld world, Entity entity, LeviathanCombatRuntime runtime) {
        return LeviathanRuntimeService.updateCombat(world, entity, runtime, config, serverTicks, virtualStore);
    }

    private static void applyLoadedPassiveMovement(ServerWorld world, Entity entity, VirtualLeviathanStore.VirtualLeviathanState state) {
        LeviathanRuntimeService.applyLoadedPassiveMovement(world, entity, state, config);
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

        LeviathanCombatRuntime runtime = combatRuntimeById.get(id);
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
        Map<UUID, Entity> loaded = LeviathanManagedEntityIndex.snapshot(world);
        if (AtlantisMod.LOGGER.isDebugEnabled()) {
            AtlantisMod.LOGGER.debug("[Atlantis][leviathan] loaded managed scan complete count={}", loaded.size());
        }
        return loaded;
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
        return isAnyPlayerNear(world.getPlayers(player -> player != null && !player.isSpectator() && player.isAlive()), pos, radiusBlocks);
    }

    private static boolean isAnyPlayerNear(List<ServerPlayerEntity> players, Vec3d pos, int radiusBlocks) {
        if (radiusBlocks <= 0 || players == null || players.isEmpty()) {
            return false;
        }

        double r2 = (double) radiusBlocks * (double) radiusBlocks;
        for (ServerPlayerEntity player : players) {
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
