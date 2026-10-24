package com.silver.skyislands.giantmobs;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.server.MinecraftServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.LevelData;

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
    private static final EntityType<?> GIANT_TYPE = BuiltInRegistries.ENTITY_TYPE
            .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "giant"));
    public static final String MANAGED_TAG = "sky_islands_managed_giant";

    private static final Logger LOGGER = LoggerFactory.getLogger(GiantMobManager.class);

    private static GiantMobsConfig config;
    private static VirtualGiantStore virtualStore;
    private static GiantGroundFinder groundFinder;
    private static GiantChunkPreloader chunkPreloader;
    private static GiantProjectiles projectiles;
    private static GiantCombat combat;

    private static long serverTicks;
    private static boolean initialised;

    private static final Set<UUID> debugLoggedUnmanagedGiants = new HashSet<>();
    private static final Set<UUID> inactiveChunkReleaseDone = new HashSet<>();
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
        projectiles = new GiantProjectiles(config);
        combat = new GiantCombat(config, projectiles);

        ServerTickEvents.END_SERVER_TICK.register(GiantMobManager::tick);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (virtualStore != null) {
                virtualStore.flush();
            }
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, damageSource) -> {
            if (entity instanceof Giant giant && isManaged(giant)) {
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

    public static boolean isManaged(Giant giant) {
        return giant.entityTags().contains(MANAGED_TAG);
    }

    public static boolean isManagedProjectile(Entity entity) {
        return GiantProjectiles.isProjectile(entity);
    }

    public static boolean shouldForceTrack(Entity entity) {
        if (entity instanceof Giant giant) {
            return isManaged(giant);
        }
        return isManagedProjectile(entity);
    }

    public static boolean shouldPreventUnload(Entity entity) {
        return shouldForceTrack(entity);
    }

    public static int dumpGiants(CommandSourceStack source, boolean includeVirtual, boolean includeLoaded, boolean includeProjectiles) {
        if (virtualStore == null || config == null) {
            source.sendSuccess(() -> Component.literal("Sky-Islands giants system not initialised yet."), false);
            return 0;
        }

        ServerLevel overworld = source.getServer().overworld();
        if (overworld == null) {
            source.sendSuccess(() -> Component.literal("Sky-Islands: no overworld available."), false);
            return 0;
        }

        List<VirtualGiantStore.VirtualGiantState> snapshot = virtualStore.snapshot();
        Map<UUID, Giant> loaded = new HashMap<>();
        List<FallingBlockEntity> thrown = new ArrayList<>();

        for (Entity entity : overworld.getAllEntities()) {
            if (entity instanceof Giant giant && isManaged(giant)) {
                GiantIdTags.getId(giant).ifPresent(id -> loaded.put(id, giant));
            } else if (entity instanceof FallingBlockEntity fallingBlock && isManagedProjectile(fallingBlock)) {
                thrown.add(fallingBlock);
            }
        }

        int virtualCount = includeVirtual ? snapshot.size() : 0;
        int loadedCount = includeLoaded ? loaded.size() : 0;
        int projectileCount = includeProjectiles ? thrown.size() : 0;
        source.sendSuccess(() -> Component.literal("Sky-Islands giants: virtual=" + virtualCount + " loaded=" + loadedCount + " projectiles=" + projectileCount +
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
                source.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }

        if (includeLoaded) {
            for (Map.Entry<UUID, Giant> entry : loaded.entrySet()) {
                if (shown >= maxShow) {
                    break;
                }

                Giant giant = entry.getValue();
                String line = " - loaded id=" + shortId(entry.getKey()) +
                        " entityPos=(" + round1(giant.getX()) + ", " + round1(giant.getY()) + ", " + round1(giant.getZ()) + ")" +
                        " yaw=" + round1(giant.getYRot()) +
                        " hp=" + round1(giant.getHealth()) + "/" + round1(giant.getMaxHealth());
                source.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }

        if (includeProjectiles) {
            for (FallingBlockEntity projectile : thrown) {
                if (shown >= maxShow) {
                    break;
                }

                String line = " - projectile uuid=" + projectile.getStringUUID() +
                        " pos=(" + round1(projectile.getX()) + ", " + round1(projectile.getY()) + ", " + round1(projectile.getZ()) + ")" +
                        " block=" + projectile.getBlockState().getBlock();
                source.sendSuccess(() -> Component.literal(line), false);
                shown++;
            }
        }

        if ((includeVirtual && snapshot.size() > maxShow) || (includeLoaded && loaded.size() > maxShow) || (includeProjectiles && thrown.size() > maxShow)) {
            source.sendSuccess(() -> Component.literal("(output truncated; showing first " + maxShow + ")"), false);
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

        ServerLevel world = server.overworld();
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

        combat.trackPlayers(world, serverTicks);
        projectiles.tick(world, serverTicks);
        tickVirtualGiants(world);
    }

    private static void debugLogUnmanagedGiants(ServerLevel world) {
        int found = 0;
        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof Giant giant)) {
                continue;
            }
            if (isManaged(giant)) {
                continue;
            }
            found++;
            UUID id = giant.getUUID();
            if (!debugLoggedUnmanagedGiants.add(id)) {
                continue;
            }

            LOGGER.warn("[Sky-Islands][giants][debug] unmanaged Giant present uuid={} pos=({}, {}, {}) tags={}",
                    giant.getStringUUID(),
                    round1(giant.getX()), round1(giant.getY()), round1(giant.getZ()),
                    giant.entityTags().size());
        }

        if (found == 0 && !debugLoggedUnmanagedGiants.isEmpty()) {
            debugLoggedUnmanagedGiants.clear();
        }
    }

    private static void recoverMissingLoadedGiants(ServerLevel world) {
        Set<UUID> known = new HashSet<>();
        for (VirtualGiantStore.VirtualGiantState state : virtualStore.snapshot()) {
            known.add(state.id());
        }

        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof Giant giant)) {
                continue;
            }
            if (!isManaged(giant) || giant.isRemoved() || !giant.isAlive()) {
                continue;
            }

            GiantIdTags.getId(giant).ifPresent(id -> {
                if (known.contains(id)) {
                    return;
                }

                virtualStore.upsert(new VirtualGiantStore.VirtualGiantState(id, giant.position(), giant.getYRot(), serverTicks));
            });
        }
    }

    private static void ensureMinimumVirtualGiants(ServerLevel world) {
        int failures = 0;
        while (virtualStore.size() < config.minimumGiants && failures < (config.minimumGiants * 3)) {
            Optional<Vec3> spawnPos = pickPersistentSpawnPos(world);
            if (spawnPos.isEmpty()) {
                failures++;
                continue;
            }

            UUID id = UUID.randomUUID();
            float yaw = world.getRandom().nextFloat() * 360.0F;
            Vec3 pos = spawnPos.get();
            virtualStore.upsert(new VirtualGiantStore.VirtualGiantState(id, pos, yaw, serverTicks));

            LOGGER.info("[Sky-Islands] Created virtual giant id={} pos=({}, {}, {}) yaw={}",
                    shortId(id),
                    round1(pos.x), round1(pos.y), round1(pos.z),
                    round1(yaw));
        }
    }

    private static void tickVirtualGiants(ServerLevel world) {
        List<VirtualGiantStore.VirtualGiantState> snapshot = virtualStore.snapshot();
        if (snapshot.isEmpty()) {
            if (config.forceChunkLoadingEnabled) {
                chunkPreloader.releaseUnused(world, serverTicks, config.releaseTicketsAfterTicks);
            }
            return;
        }

        Map<UUID, Giant> loaded = new HashMap<>();
        for (Entity entity : world.getAllEntities()) {
            if (!(entity instanceof Giant giant)) {
                continue;
            }
            if (!isManaged(giant) || giant.isRemoved() || !giant.isAlive()) {
                continue;
            }
            GiantIdTags.getId(giant).ifPresent(id -> loaded.put(id, giant));
        }

        int chunkBudget = config.forceChunkLoadingEnabled ? config.maxChunkLoadsPerTick : 0;

        for (VirtualGiantStore.VirtualGiantState state : snapshot) {
            Giant giant = loaded.get(state.id());
            if (giant == null) {
                boolean playerNearVirtual = isAnyPlayerNear(world, state.pos(), config.activationRadiusBlocks);
                if (!playerNearVirtual) {
                    releaseInactiveChunks(world, state.id());
                    continue;
                }

                inactiveChunkReleaseDone.remove(state.id());
                ServerPlayer preloadTarget = findNearestPlayer(world, state.pos(), config.activationRadiusBlocks);
                Set<ChunkPos> desired = computeDesiredChunks(state.pos(), preloadTarget);
                if (config.forceChunkLoadingEnabled) {
                    chunkBudget -= chunkPreloader.request(world, state.id(), desired, serverTicks, chunkBudget);
                }

                if (isSpawnReady(world, desired)) {
                    Giant spawned = spawnGiantFromVirtual(world, state);
                    if (spawned != null) {
                        loaded.put(state.id(), spawned);
                    }
                }
                continue;
            }

            inactiveChunkReleaseDone.remove(state.id());
            giant.setNoAi(true);

            ServerPlayer target = findNearestPlayer(world, giant.position(), config.attackRangeBlocks);
            boolean playerNearLoaded = isAnyPlayerNear(world, giant.position(), config.activationRadiusBlocks);
            Set<ChunkPos> desired = computeDesiredChunks(giant.position(), target);
            if (config.forceChunkLoadingEnabled && playerNearLoaded) {
                chunkBudget -= chunkPreloader.request(world, state.id(), desired, serverTicks, chunkBudget);
            }

            combat.tick(world, giant, state.id(), playersWithin(world, giant.position(), config.attackRangeBlocks), serverTicks);

            if (!isAnyPlayerNear(world, giant.position(), config.despawnRadiusBlocks)) {
                giant.discard();
                combat.forget(state.id());
                loaded.remove(state.id());
                releaseInactiveChunks(world, state.id());
                continue;
            }

            virtualStore.upsert(new VirtualGiantStore.VirtualGiantState(state.id(), giant.position(), giant.getYRot(), serverTicks));
        }

        if (config.forceChunkLoadingEnabled) {
            chunkPreloader.releaseUnused(world, serverTicks, config.releaseTicketsAfterTicks);
        }
    }

    private static Giant spawnGiantFromVirtual(ServerLevel world, VirtualGiantStore.VirtualGiantState state) {
        Giant giant = (Giant) BuiltInRegistries.ENTITY_TYPE
                .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath("minecraft", "giant"))
                .create(world, EntitySpawnReason.EVENT);
        if (giant == null) {
            return null;
        }

        Vec3 pos = state.pos();
        giant.setPos(pos.x, pos.y, pos.z);
        giant.setYRot(state.yawDegrees());
        giant.setXRot(0.0F);
        giant.setNoAi(true);
        giant.setPersistenceRequired();
        giant.getAttribute(Attributes.MAX_HEALTH).setBaseValue(config.maxHealth);
        giant.setHealth((float) config.maxHealth);

        if (!world.noCollision(giant)) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][giants][manager] spawn blocked id={} virtualPos=({}, {}, {})",
                        shortId(state.id()),
                        round1(state.pos().x), round1(state.pos().y), round1(state.pos().z));
            }
            return null;
        }

        giant.addTag(MANAGED_TAG);
        giant.addTag(GiantIdTags.toTag(state.id()));

        if (!world.addFreshEntity(giant)) {
            return null;
        }

        return giant;
    }

    private static boolean isSpawnReady(ServerLevel world, Iterable<ChunkPos> desiredChunks) {
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

    private static Set<ChunkPos> computeDesiredChunks(Vec3 giantPos, ServerPlayer target) {
        Set<ChunkPos> desired = new LinkedHashSet<>();

        int centerChunkX = Mth.floor(giantPos.x) >> 4;
        int centerChunkZ = Mth.floor(giantPos.z) >> 4;
        int radius = config.preloadRadiusChunks;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                desired.add(new ChunkPos(centerChunkX + dx, centerChunkZ + dz));
            }
        }

        if (target != null) {
            Vec3 targetPos = target.position();
            double dx = targetPos.x - giantPos.x;
            double dz = targetPos.z - giantPos.z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            int steps = Math.max(1, (int) Math.ceil(distance / 16.0));
            for (int i = 0; i <= steps; i++) {
                double t = i / (double) steps;
                double sampleX = giantPos.x + (dx * t);
                double sampleZ = giantPos.z + (dz * t);
                desired.add(new ChunkPos(Mth.floor(sampleX) >> 4, Mth.floor(sampleZ) >> 4));
            }
        }

        return desired;
    }

    private static void releaseInactiveChunks(ServerLevel world, UUID id) {
        if (!config.forceChunkLoadingEnabled) {
            return;
        }
        if (inactiveChunkReleaseDone.add(id)) {
            chunkPreloader.release(world, id);
        }
    }

    private static void onManagedGiantDeath(Giant giant) {
        GiantIdTags.getId(giant).ifPresent(id -> {
            virtualStore.remove(id);
            combat.forget(id);
            inactiveChunkReleaseDone.remove(id);
            LOGGER.info("[Sky-Islands] Managed giant died id={} uuid={}", shortId(id), giant.getStringUUID());
        });
    }

    private static Optional<Vec3> pickPersistentSpawnPos(ServerLevel world) {
        int attempts = Math.max(16, config.spawnSearchAttempts * 2);
        for (int attempt = 0; attempt < attempts; attempt++) {
            Vec3 anchor = pickPersistentSpawnAnchor(world);
            Optional<BlockPos> spawnPos = groundFinder.findSpawnPosAtColumn(
                    world,
                    Mth.floor(anchor.x),
                    Mth.floor(anchor.z),
                    config,
                    world.getRandom(),
                    serverTicks
            );
            if (spawnPos.isEmpty()) {
                continue;
            }

            Vec3 pos = Vec3.atBottomCenterOf(spawnPos.get());
            if (canSpawnGiantAt(world, pos)) {
                return Optional.of(pos);
            }
        }

        return Optional.empty();
    }

    private static Vec3 pickPersistentSpawnAnchor(ServerLevel world) {
        BlockPos spawn = world.getRespawnData() != null ? world.getRespawnData().pos() : BlockPos.ZERO;

        int inner = Math.max(0, config.minSpawnDistanceBlocks);
        int outer = Math.max(inner + 1, config.maxSpawnDistanceBlocks);

        double minX = world.getWorldBorder().getMinX() + 64.0;
        double maxX = world.getWorldBorder().getMaxX() - 64.0;
        double minZ = world.getWorldBorder().getMinZ() + 64.0;
        double maxZ = world.getWorldBorder().getMaxZ() - 64.0;

        double theta = world.getRandom().nextDouble() * (Math.PI * 2.0);
        double radius = Math.sqrt(world.getRandom().nextDouble() * ((double) outer * (double) outer - (double) inner * (double) inner) + (double) inner * (double) inner);
        double x = clamp(spawn.getX() + (Math.cos(theta) * radius), minX, maxX);
        double z = clamp(spawn.getZ() + (Math.sin(theta) * radius), minZ, maxZ);
        return new Vec3(x, world.getMinY() + world.getHeight() - 1, z);
    }

    private static boolean canSpawnGiantAt(ServerLevel world, Vec3 pos) {
        Giant giant = (Giant) GIANT_TYPE.create(world, EntitySpawnReason.EVENT);
        if (giant == null) {
            return false;
        }

        giant.setPos(pos.x, pos.y, pos.z);
        giant.setYRot(0.0F);
        giant.setXRot(0.0F);
        giant.setNoAi(true);
        return world.noCollision(giant);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static List<ServerPlayer> playersWithin(ServerLevel world, Vec3 pos, int radiusBlocks) {
        double maxDistanceSq = (double) radiusBlocks * (double) radiusBlocks;
        List<ServerPlayer> result = new ArrayList<>();
        for (ServerPlayer player : world.players()) {
            if (!player.isSpectator() && player.isAlive() && player.position().distanceToSqr(pos) <= maxDistanceSq) {
                result.add(player);
            }
        }
        return result;
    }

    private static boolean isAnyPlayerNear(ServerLevel world, Vec3 pos, int radiusBlocks) {
        double maxDistanceSq = (double) radiusBlocks * (double) radiusBlocks;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            if (player.position().distanceToSqr(pos) <= maxDistanceSq) {
                return true;
            }
        }
        return false;
    }

    private static ServerPlayer findNearestPlayer(ServerLevel world, Vec3 pos, int radiusBlocks) {
        double bestSq = (double) radiusBlocks * (double) radiusBlocks;
        ServerPlayer best = null;
        for (ServerPlayer player : world.players()) {
            if (player.isSpectator()) {
                continue;
            }
            double distanceSq = player.position().distanceToSqr(pos);
            if (distanceSq > bestSq) {
                continue;
            }
            bestSq = distanceSq;
            best = player;
        }
        return best;
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.substring(0, 8);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
