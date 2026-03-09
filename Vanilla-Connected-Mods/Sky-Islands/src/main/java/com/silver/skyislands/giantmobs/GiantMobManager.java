package com.silver.skyislands.giantmobs;

import com.silver.skyislands.giantmobs.mixins.FallingBlockEntityInvoker;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.GiantEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.WorldProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class GiantMobManager {
    public static final String MANAGED_TAG = "sky_islands_managed_giant";
    public static final String PROJECTILE_TAG = "sky_islands_managed_giant_projectile";

    private static final double PROJECTILE_DRAG = 0.98D;
    private static final double PROJECTILE_GRAVITY = 0.04D;
    private static final int MIN_SOLVER_FLIGHT_TICKS = 8;
    private static final int EXTRA_PROJECTILE_LIFE_TICKS = 12;

    private static final Logger LOGGER = LoggerFactory.getLogger(GiantMobManager.class);

    private static GiantMobsConfig config;
    private static VirtualGiantStore virtualStore;
    private static GiantGroundFinder groundFinder;
    private static GiantChunkPreloader chunkPreloader;

    private static long serverTicks;
    private static boolean initialised;

    private static final Set<UUID> debugLoggedUnmanagedGiants = new HashSet<>();
    private static final Set<UUID> inactiveChunkReleaseDone = new HashSet<>();
    private static final Map<UUID, Long> nextAttackTick = new HashMap<>();
    private static final Map<UUID, ProjectileState> projectileStates = new HashMap<>();

    private record ProjectileState(UUID ownerId, Vec3d launchPos, long expiresTick) {
    }

    private record ProjectileLaunch(Vec3d velocity, int flightTicks) {
    }

    private GiantMobManager() {
    }

    public static void init() {
        if (initialised) {
            return;
        }
        initialised = true;

        config = GiantMobsConfig.loadOrCreate();
        virtualStore = new VirtualGiantStore();
        groundFinder = new GiantGroundFinder();
        chunkPreloader = new GiantChunkPreloader(config.preloadTicketLevel);

        ServerTickEvents.END_SERVER_TICK.register(GiantMobManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (virtualStore != null) {
                virtualStore.flush();
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof GiantEntity giant && isManaged(giant)) {
                onManagedGiantDeath(giant);
            }
        });

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][giants][manager] init enabled={} minimum={} activation={} despawn={} forceChunkLoading={}",
                    config.enabled,
                    config.minimumGiants,
                    config.activationRadiusBlocks,
                    config.despawnRadiusBlocks,
                    config.forceChunkLoadingEnabled);
        }
    }

    public static boolean isManaged(GiantEntity giant) {
        return giant.getCommandTags().contains(MANAGED_TAG);
    }

    public static boolean isManagedProjectile(Entity entity) {
        return entity != null && entity.getCommandTags().contains(PROJECTILE_TAG);
    }

    public static boolean shouldForceTrack(Entity entity) {
        if (entity instanceof GiantEntity giant) {
            return isManaged(giant);
        }
        return isManagedProjectile(entity);
    }

    public static boolean shouldPreventUnload(Entity entity) {
        return shouldForceTrack(entity);
    }

    public static int dumpGiants(ServerCommandSource source, boolean includeVirtual, boolean includeLoaded, boolean includeProjectiles) {
        if (virtualStore == null || config == null) {
            source.sendFeedback(() -> Text.literal("Sky-Islands giants system not initialised yet."), false);
            return 0;
        }

        ServerWorld overworld = source.getServer().getOverworld();
        if (overworld == null) {
            source.sendFeedback(() -> Text.literal("Sky-Islands: no overworld available."), false);
            return 0;
        }

        List<VirtualGiantStore.VirtualGiantState> snapshot = virtualStore.snapshot();
        Map<UUID, GiantEntity> loaded = new HashMap<>();
        List<FallingBlockEntity> projectiles = new ArrayList<>();

        for (Entity entity : overworld.iterateEntities()) {
            if (entity instanceof GiantEntity giant && isManaged(giant)) {
                GiantIdTags.getId(giant).ifPresent(id -> loaded.put(id, giant));
            } else if (entity instanceof FallingBlockEntity fallingBlock && isManagedProjectile(fallingBlock)) {
                projectiles.add(fallingBlock);
            }
        }

        int virtualCount = includeVirtual ? snapshot.size() : 0;
        int loadedCount = includeLoaded ? loaded.size() : 0;
        int projectileCount = includeProjectiles ? projectiles.size() : 0;
        source.sendFeedback(() -> Text.literal("Sky-Islands giants: virtual=" + virtualCount + " loaded=" + loadedCount + " projectiles=" + projectileCount +
                " (activationRadius=" + config.activationRadiusBlocks + " despawnRadius=" + config.despawnRadiusBlocks + ")"), false);

        int shown = 0;
        int maxShow = 25;

        if (includeVirtual) {
            for (VirtualGiantStore.VirtualGiantState state : snapshot) {
                if (shown >= maxShow) {
                    break;
                }

                String line = " - id=" + shortId(state.id()) +
                        " virtualPos=(" + round1(state.pos().x) + ", " + round1(state.pos().y) + ", " + round1(state.pos().z) + ")" +
                        " yaw=" + round1(state.yawDegrees());
                source.sendFeedback(() -> Text.literal(line), false);
                shown++;
            }
        }

        if (includeLoaded) {
            for (Map.Entry<UUID, GiantEntity> entry : loaded.entrySet()) {
                if (shown >= maxShow) {
                    break;
                }

                GiantEntity giant = entry.getValue();
                String line = " - loaded id=" + shortId(entry.getKey()) +
                        " entityPos=(" + round1(giant.getX()) + ", " + round1(giant.getY()) + ", " + round1(giant.getZ()) + ")" +
                        " yaw=" + round1(giant.getYaw());
                source.sendFeedback(() -> Text.literal(line), false);
                shown++;
            }
        }

        if (includeProjectiles) {
            for (FallingBlockEntity projectile : projectiles) {
                if (shown >= maxShow) {
                    break;
                }

                String line = " - projectile uuid=" + projectile.getUuidAsString() +
                        " pos=(" + round1(projectile.getX()) + ", " + round1(projectile.getY()) + ", " + round1(projectile.getZ()) + ")" +
                        " block=" + projectile.getBlockState().getBlock();
                source.sendFeedback(() -> Text.literal(line), false);
                shown++;
            }
        }

        if ((includeVirtual && snapshot.size() > maxShow) || (includeLoaded && loaded.size() > maxShow) || (includeProjectiles && projectiles.size() > maxShow)) {
            source.sendFeedback(() -> Text.literal("(output truncated; showing first " + maxShow + ")"), false);
        }

        return 1;
    }

    private static void tick(MinecraftServer server) {
        serverTicks++;

        if (config == null || virtualStore == null || groundFinder == null || chunkPreloader == null) {
            return;
        }

        if (config.virtualStateFlushIntervalMinutes > 0) {
            long flushIntervalTicks = (long) config.virtualStateFlushIntervalMinutes * 60L * 20L;
            if (flushIntervalTicks > 0L && (serverTicks % flushIntervalTicks) == 0L) {
                virtualStore.flush();
            }
        }

        ServerWorld world = server.getOverworld();
        if (world == null) {
            return;
        }

        if (!config.enabled) {
            if (config.forceChunkLoadingEnabled) {
                chunkPreloader.releaseUnused(world, serverTicks, config.releaseTicketsAfterTicks);
            }
            return;
        }

        if ((serverTicks % 100L) == 0L) {
            debugLogUnmanagedGiants(world);
            recoverMissingLoadedGiants(world);
            ensureMinimumVirtualGiants(world);
        }

        tickProjectiles(world);
        tickVirtualGiants(world);
    }

    private static void debugLogUnmanagedGiants(ServerWorld world) {
        int found = 0;
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof GiantEntity giant)) {
                continue;
            }
            if (isManaged(giant)) {
                continue;
            }
            found++;
            UUID id = giant.getUuid();
            if (!debugLoggedUnmanagedGiants.add(id)) {
                continue;
            }

            LOGGER.warn("[Sky-Islands][giants][debug] unmanaged GiantEntity present uuid={} pos=({}, {}, {}) tags={}",
                    giant.getUuidAsString(),
                    round1(giant.getX()), round1(giant.getY()), round1(giant.getZ()),
                    giant.getCommandTags().size());
        }

        if (found == 0 && !debugLoggedUnmanagedGiants.isEmpty()) {
            debugLoggedUnmanagedGiants.clear();
        }
    }

    private static void recoverMissingLoadedGiants(ServerWorld world) {
        Set<UUID> known = new HashSet<>();
        for (VirtualGiantStore.VirtualGiantState state : virtualStore.snapshot()) {
            known.add(state.id());
        }

        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof GiantEntity giant)) {
                continue;
            }
            if (!isManaged(giant) || giant.isRemoved() || !giant.isAlive()) {
                continue;
            }

            GiantIdTags.getId(giant).ifPresent(id -> {
                if (known.contains(id)) {
                    return;
                }

                virtualStore.upsert(new VirtualGiantStore.VirtualGiantState(id, giant.getEntityPos(), giant.getYaw(), serverTicks));
            });
        }
    }

    private static void ensureMinimumVirtualGiants(ServerWorld world) {
        int failures = 0;
        while (virtualStore.size() < config.minimumGiants && failures < (config.minimumGiants * 3)) {
            Optional<Vec3d> spawnPos = pickPersistentSpawnPos(world);
            if (spawnPos.isEmpty()) {
                failures++;
                continue;
            }

            UUID id = UUID.randomUUID();
            float yaw = world.getRandom().nextFloat() * 360.0F;
            Vec3d pos = spawnPos.get();
            virtualStore.upsert(new VirtualGiantStore.VirtualGiantState(id, pos, yaw, serverTicks));

            LOGGER.info("[Sky-Islands] Created virtual giant id={} pos=({}, {}, {}) yaw={}",
                    shortId(id),
                    round1(pos.x), round1(pos.y), round1(pos.z),
                    round1(yaw));
        }
    }

    private static void tickVirtualGiants(ServerWorld world) {
        List<VirtualGiantStore.VirtualGiantState> snapshot = virtualStore.snapshot();
        if (snapshot.isEmpty()) {
            if (config.forceChunkLoadingEnabled) {
                chunkPreloader.releaseUnused(world, serverTicks, config.releaseTicketsAfterTicks);
            }
            return;
        }

        Map<UUID, GiantEntity> loaded = new HashMap<>();
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof GiantEntity giant)) {
                continue;
            }
            if (!isManaged(giant) || giant.isRemoved() || !giant.isAlive()) {
                continue;
            }
            GiantIdTags.getId(giant).ifPresent(id -> loaded.put(id, giant));
        }

        int chunkBudget = config.forceChunkLoadingEnabled ? config.maxChunkLoadsPerTick : 0;

        for (VirtualGiantStore.VirtualGiantState state : snapshot) {
            GiantEntity giant = loaded.get(state.id());
            if (giant == null) {
                boolean playerNearVirtual = isAnyPlayerNear(world, state.pos(), config.activationRadiusBlocks);
                if (!playerNearVirtual) {
                    releaseInactiveChunks(world, state.id());
                    continue;
                }

                inactiveChunkReleaseDone.remove(state.id());
                ServerPlayerEntity preloadTarget = findNearestPlayer(world, state.pos(), config.activationRadiusBlocks);
                Set<ChunkPos> desired = computeDesiredChunks(state.pos(), preloadTarget);
                if (config.forceChunkLoadingEnabled) {
                    chunkBudget -= chunkPreloader.request(world, state.id(), desired, serverTicks, chunkBudget);
                }

                if (isSpawnReady(world, desired)) {
                    GiantEntity spawned = spawnGiantFromVirtual(world, state);
                    if (spawned != null) {
                        loaded.put(state.id(), spawned);
                    }
                }
                continue;
            }

            inactiveChunkReleaseDone.remove(state.id());
            giant.setAiDisabled(true);

            ServerPlayerEntity target = findNearestPlayer(world, giant.getEntityPos(), config.attackRangeBlocks);
            boolean playerNearLoaded = isAnyPlayerNear(world, giant.getEntityPos(), config.activationRadiusBlocks);
            Set<ChunkPos> desired = computeDesiredChunks(giant.getEntityPos(), target);
            if (config.forceChunkLoadingEnabled && playerNearLoaded) {
                chunkBudget -= chunkPreloader.request(world, state.id(), desired, serverTicks, chunkBudget);
            }

            tickLoadedGiant(world, state, giant, target);

            if (!isAnyPlayerNear(world, giant.getEntityPos(), config.despawnRadiusBlocks)) {
                giant.discard();
                nextAttackTick.remove(state.id());
                loaded.remove(state.id());
                releaseInactiveChunks(world, state.id());
                continue;
            }

            virtualStore.upsert(new VirtualGiantStore.VirtualGiantState(state.id(), giant.getEntityPos(), giant.getYaw(), serverTicks));
        }

        if (config.forceChunkLoadingEnabled) {
            chunkPreloader.releaseUnused(world, serverTicks, config.releaseTicketsAfterTicks);
        }
    }

    private static void tickLoadedGiant(ServerWorld world, VirtualGiantStore.VirtualGiantState state, GiantEntity giant, ServerPlayerEntity target) {
        if (target == null) {
            return;
        }

        faceTarget(giant, target.getEntityPos());

        if (serverTicks < nextAttackTick.getOrDefault(state.id(), 0L)) {
            return;
        }

        if (spawnThrownBlockCluster(world, state.id(), giant, target)) {
            nextAttackTick.put(state.id(), serverTicks + getNextAttackDelayTicks(world));
        } else {
            nextAttackTick.put(state.id(), serverTicks + 10L);
        }
    }

    private static boolean spawnThrownBlockCluster(ServerWorld world, UUID giantId, GiantEntity giant, ServerPlayerEntity target) {
        double yawRadians = Math.toRadians(giant.getYaw());
        Vec3d forward = new Vec3d(-Math.sin(yawRadians), 0.0, Math.cos(yawRadians));
        Vec3d startCenter = new Vec3d(giant.getX(), giant.getEyeY() - 0.5, giant.getZ()).add(forward.multiply(2.8));
        Vec3d targetPos = new Vec3d(target.getX(), target.getEyeY() - 0.2, target.getZ());
        Vec3d delta = targetPos.subtract(startCenter);
        Vec3d horizontal = new Vec3d(delta.x, 0.0, delta.z);
        double horizontalLength = horizontal.length();
        if (horizontalLength < 1.0e-6 || horizontalLength > config.attackRangeBlocks) {
            return false;
        }

        Vec3d horizontalDir = horizontal.multiply(1.0 / horizontalLength);
        Vec3d lateral = new Vec3d(-horizontalDir.z, 0.0, horizontalDir.x);
        int spawned = 0;

        for (int i = 0; i < config.projectileCount; i++) {
            double spreadScale = config.projectileSpreadRadiusBlocks;
            double ring = i == 0 ? 0.0 : (0.35 + (0.65 * ((i + 2) / (double) config.projectileCount)));
            double angle = i == 0 ? 0.0 : (2.0 * Math.PI * i / Math.max(1, config.projectileCount - 1));
            double sideOffset = Math.cos(angle) * spreadScale * ring;
            double verticalOffset = Math.sin(angle) * (spreadScale * 0.35) * ring;

            Vec3d start = startCenter.add(lateral.multiply(sideOffset)).add(0.0, verticalOffset, 0.0);
            Vec3d spreadTarget = targetPos
                    .add(lateral.multiply(sideOffset * 1.8))
                    .add(0.0, verticalOffset * 0.75, 0.0);

            if (spawnSingleProjectile(world, giantId, giant, start, spreadTarget)) {
                spawned++;
            }
        }

        return spawned > 0;
    }

    private static boolean spawnSingleProjectile(ServerWorld world, UUID giantId, GiantEntity giant, Vec3d start, Vec3d targetPos) {
        BlockState projectileState = chooseProjectileBlockState(world, giant);
        ProjectileLaunch launch = solveProjectileLaunch(start, targetPos, config.attackRangeBlocks);
        if (launch == null) {
            return false;
        }

        FallingBlockEntity projectile = FallingBlockEntityInvoker.skyIslands$create(world, start.x, start.y, start.z, projectileState);
        projectile.setVelocity(launch.velocity());
        projectile.setHurtEntities(config.projectileImpactDamage, 40);
        projectile.setDestroyedOnLanding();
        projectile.dropItem = false;
        projectile.timeFalling = 1;
        projectile.addCommandTag(PROJECTILE_TAG);
        projectile.setFallingBlockPos(giant.getBlockPos());

        if (!world.spawnEntity(projectile)) {
            return false;
        }

        projectileStates.put(projectile.getUuid(), new ProjectileState(giantId, start, serverTicks + launch.flightTicks() + EXTRA_PROJECTILE_LIFE_TICKS));
        return true;
    }

    private static BlockState chooseProjectileBlockState(ServerWorld world, GiantEntity giant) {
        BlockPos below = giant.getBlockPos().down();
        BlockState state = world.getBlockState(below);
        if (state.isAir() || !state.blocksMovement() || !state.isSideSolidFullSquare(world, below, Direction.UP)) {
            return Blocks.COBBLESTONE.getDefaultState();
        }
        return state;
    }

    private static GiantEntity spawnGiantFromVirtual(ServerWorld world, VirtualGiantStore.VirtualGiantState state) {
        GiantEntity giant = EntityType.GIANT.create(world, SpawnReason.EVENT);
        if (giant == null) {
            return null;
        }

        Vec3d pos = state.pos();
        giant.refreshPositionAndAngles(pos.x, pos.y, pos.z, state.yawDegrees(), 0.0F);
        giant.setAiDisabled(true);
        giant.setPersistent();

        if (!world.isSpaceEmpty(giant)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][giants][manager] spawn blocked id={} virtualPos=({}, {}, {})",
                        shortId(state.id()),
                        round1(state.pos().x), round1(state.pos().y), round1(state.pos().z));
            }
            return null;
        }

        giant.addCommandTag(MANAGED_TAG);
        giant.addCommandTag(GiantIdTags.toTag(state.id()));

        if (!world.spawnEntity(giant)) {
            return null;
        }

        nextAttackTick.put(state.id(), serverTicks + config.attackCooldownTicks);
        return giant;
    }

    private static boolean isSpawnReady(ServerWorld world, Iterable<ChunkPos> desiredChunks) {
        if (!config.forceChunkLoadingEnabled) {
            return true;
        }

        for (ChunkPos pos : desiredChunks) {
            if (!chunkPreloader.isChunkLoaded(world, pos)) {
                return false;
            }
        }
        return true;
    }

    private static Set<ChunkPos> computeDesiredChunks(Vec3d giantPos, ServerPlayerEntity target) {
        Set<ChunkPos> desired = new LinkedHashSet<>();

        int centerChunkX = MathHelper.floor(giantPos.x) >> 4;
        int centerChunkZ = MathHelper.floor(giantPos.z) >> 4;
        int radius = config.preloadRadiusChunks;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                desired.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
            }
        }

        if (target != null) {
            Vec3d targetPos = target.getEntityPos();
            double dx = targetPos.x - giantPos.x;
            double dz = targetPos.z - giantPos.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(distance / 16.0));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double sampleX = giantPos.x + (dx * t);
                double sampleZ = giantPos.z + (dz * t);
                desired.add(new ChunkPos(MathHelper.floor(sampleX) >> 4, MathHelper.floor(sampleZ) >> 4));
            }
        }

        return desired;
    }

    private static void releaseInactiveChunks(ServerWorld world, UUID id) {
        if (!config.forceChunkLoadingEnabled) {
            return;
        }
        if (inactiveChunkReleaseDone.add(id)) {
            chunkPreloader.release(world, id);
        }
    }

    private static void onManagedGiantDeath(GiantEntity giant) {
        GiantIdTags.getId(giant).ifPresent(id -> {
            virtualStore.remove(id);
            nextAttackTick.remove(id);
            inactiveChunkReleaseDone.remove(id);
            LOGGER.info("[Sky-Islands] Managed giant died id={} uuid={}", shortId(id), giant.getUuidAsString());
        });
    }

    private static void tickProjectiles(ServerWorld world) {
        if (projectileStates.isEmpty()) {
            return;
        }

        List<UUID> toRemove = new ArrayList<>();
        for (Entity entity : world.iterateEntities()) {
            if (!(entity instanceof FallingBlockEntity projectile) || !isManagedProjectile(projectile)) {
                continue;
            }

            ProjectileState projectileState = projectileStates.get(projectile.getUuid());
            if (projectileState == null) {
                projectileStates.put(projectile.getUuid(), new ProjectileState(null, projectile.getEntityPos(), serverTicks + getMaxSolverFlightTicks() + EXTRA_PROJECTILE_LIFE_TICKS));
                projectileState = projectileStates.get(projectile.getUuid());
            }

            if (projectileState.expiresTick() <= serverTicks || projectile.squaredDistanceTo(projectileState.launchPos()) > (double) config.attackRangeBlocks * (double) config.attackRangeBlocks || projectile.isOnGround()) {
                impactProjectile(world, projectile, projectileState);
                toRemove.add(projectile.getUuid());
                continue;
            }

            Box impactBox = projectile.getBoundingBox().expand(0.6);
            boolean hitEntity = false;
            for (Entity hit : world.getOtherEntities(projectile, impactBox, candidate -> candidate instanceof LivingEntity living && !(candidate instanceof GiantEntity) && candidate.isAlive())) {
                if (!(hit instanceof LivingEntity living)) {
                    continue;
                }

                if (!living.damage(world, world.getDamageSources().fallingBlock(projectile), config.projectileImpactDamage)) {
                    continue;
                }

                living.takeKnockback(config.projectileKnockbackStrength, projectile.getX() - living.getX(), projectile.getZ() - living.getZ());
                hitEntity = true;
            }

            if (hitEntity) {
                impactProjectile(world, projectile, projectileState);
                toRemove.add(projectile.getUuid());
            }
        }

        projectileStates.keySet().removeIf(id -> toRemove.contains(id));
    }

    private static void impactProjectile(ServerWorld world, FallingBlockEntity projectile, ProjectileState projectileState) {
        if (projectile.isRemoved()) {
            return;
        }

        Box splashBox = projectile.getBoundingBox().expand(1.25);
        for (Entity hit : world.getOtherEntities(projectile, splashBox, candidate -> candidate instanceof LivingEntity living && !(candidate instanceof GiantEntity) && candidate.isAlive())) {
            if (!(hit instanceof LivingEntity living)) {
                continue;
            }

            living.damage(world, world.getDamageSources().fallingBlock(projectile), Math.max(1.0F, config.projectileImpactDamage * 0.5F));
            living.takeKnockback(config.projectileKnockbackStrength * 0.65, projectile.getX() - living.getX(), projectile.getZ() - living.getZ());
        }

        projectile.discard();
    }

    private static Optional<Vec3d> pickPersistentSpawnPos(ServerWorld world) {
        int attempts = Math.max(16, config.spawnSearchAttempts * 2);
        for (int attempt = 0; attempt < attempts; attempt++) {
            Vec3d anchor = pickPersistentSpawnAnchor(world);
            Optional<BlockPos> spawnPos = groundFinder.findSpawnPosAtColumn(
                    world,
                    MathHelper.floor(anchor.x),
                    MathHelper.floor(anchor.z),
                    config,
                    world.getRandom(),
                    serverTicks
            );
            if (spawnPos.isEmpty()) {
                continue;
            }

            Vec3d pos = Vec3d.ofBottomCenter(spawnPos.get());
            if (canSpawnGiantAt(world, pos)) {
                return Optional.of(pos);
            }
        }

        return Optional.empty();
    }

    private static Vec3d pickPersistentSpawnAnchor(ServerWorld world) {
        WorldProperties.SpawnPoint spawnPoint = world.getLevelProperties().getSpawnPoint();
        BlockPos spawn = spawnPoint != null ? spawnPoint.getPos() : BlockPos.ORIGIN;

        int inner = Math.max(0, config.minSpawnDistanceBlocks);
        int outer = Math.max(inner + 1, config.maxSpawnDistanceBlocks);

        double minX = world.getWorldBorder().getBoundWest() + 64.0;
        double maxX = world.getWorldBorder().getBoundEast() - 64.0;
        double minZ = world.getWorldBorder().getBoundNorth() + 64.0;
        double maxZ = world.getWorldBorder().getBoundSouth() - 64.0;

        double theta = world.getRandom().nextDouble() * (Math.PI * 2.0);
        double radius = Math.sqrt(world.getRandom().nextDouble() * ((double) outer * (double) outer - (double) inner * (double) inner) + (double) inner * (double) inner);
        double x = clamp(spawn.getX() + (Math.cos(theta) * radius), minX, maxX);
        double z = clamp(spawn.getZ() + (Math.sin(theta) * radius), minZ, maxZ);
        return new Vec3d(x, world.getTopYInclusive(), z);
    }

    private static boolean canSpawnGiantAt(ServerWorld world, Vec3d pos) {
        GiantEntity giant = EntityType.GIANT.create(world, SpawnReason.EVENT);
        if (giant == null) {
            return false;
        }

        giant.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        giant.setAiDisabled(true);
        return world.isSpaceEmpty(giant);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static ProjectileLaunch solveProjectileLaunch(Vec3d start, Vec3d target, int maxHorizontalRange) {
        Vec3d delta = target.subtract(start);
        double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        if (horizontalDistance < 1.0e-6 || horizontalDistance > maxHorizontalRange) {
            return null;
        }

        double bestScore = Double.POSITIVE_INFINITY;
        ProjectileLaunch best = null;

        double sumCoeff = 0.0;
        double coeff = 1.0;
        double gravityVelocity = 0.0;
        double gravityDisplacement = 0.0;

        int maxFlightTicks = getMaxSolverFlightTicks();
        double preferredMaxSpeed = Math.max(3.0, maxHorizontalRange / 24.0);

        for (int ticks = 1; ticks <= maxFlightTicks; ticks++) {
            sumCoeff += coeff;
            gravityVelocity -= PROJECTILE_GRAVITY;
            gravityDisplacement += gravityVelocity;
            gravityVelocity *= PROJECTILE_DRAG;
            coeff *= PROJECTILE_DRAG;

            if (ticks < MIN_SOLVER_FLIGHT_TICKS) {
                continue;
            }

            double velocityX = delta.x / sumCoeff;
            double velocityZ = delta.z / sumCoeff;
            double velocityY = (delta.y - gravityDisplacement) / sumCoeff;

            double horizontalSpeed = Math.sqrt(velocityX * velocityX + velocityZ * velocityZ);
            double launchSpeed = Math.sqrt(horizontalSpeed * horizontalSpeed + velocityY * velocityY);
            double pitch = Math.abs(Math.atan2(velocityY, Math.max(horizontalSpeed, 1.0e-6)));

            double score = pitch * 4.0 + (launchSpeed * 0.08) + (ticks * 0.002);
            if (launchSpeed > preferredMaxSpeed) {
                score += (launchSpeed - preferredMaxSpeed) * 0.75;
            }

            if (score < bestScore) {
                bestScore = score;
                best = new ProjectileLaunch(new Vec3d(velocityX, velocityY, velocityZ), ticks);
            }
        }

        return best;
    }

    private static int getMaxSolverFlightTicks() {
        return Math.max(40, Math.min(200, config.attackRangeBlocks + 20));
    }

    private static long getNextAttackDelayTicks(ServerWorld world) {
        int jitter = world.getRandom().nextBetween(-20, 20);
        return Math.max(10L, (long) config.attackCooldownTicks + jitter);
    }

    private static boolean isAnyPlayerNear(ServerWorld world, Vec3d pos, int radiusBlocks) {
        double maxDistanceSq = (double) radiusBlocks * (double) radiusBlocks;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            if (player.getEntityPos().squaredDistanceTo(pos) <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    private static ServerPlayerEntity findNearestPlayer(ServerWorld world, Vec3d pos, int radiusBlocks) {
        double bestSq = (double) radiusBlocks * (double) radiusBlocks;
        ServerPlayerEntity best = null;
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.isSpectator()) {
                continue;
            }
            double distanceSq = player.getEntityPos().squaredDistanceTo(pos);
            if (distanceSq > bestSq) {
                continue;
            }
            bestSq = distanceSq;
            best = player;
        }
        return best;
    }

    private static void faceTarget(GiantEntity giant, Vec3d targetPos) {
        Vec3d from = giant.getEyePos();
        Vec3d delta = targetPos.subtract(from);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);

        float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
        float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, Math.max(horizontal, 1.0e-6)));

        giant.setYaw(yaw);
        giant.setHeadYaw(yaw);
        giant.setBodyYaw(yaw);
        giant.setPitch(MathHelper.clamp(pitch, -30.0F, 45.0F));
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, 8);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}