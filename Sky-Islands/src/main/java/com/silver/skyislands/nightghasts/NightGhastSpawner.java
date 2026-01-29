package com.silver.skyislands.nightghasts;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.GhastEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class NightGhastSpawner {

    private static final Logger LOGGER = LoggerFactory.getLogger(NightGhastSpawner.class);

    static final String TAG = "sky_islands_night_ghast";

    private static final Map<UUID, Long> nextSpawnTickByPlayer = new HashMap<>();
    private static final long CREATIVE_SKIP_LOG_INTERVAL_TICKS = 20L * 60L;
    private static final Map<UUID, Long> nextCreativeSkipLogTickByPlayer = new HashMap<>();

    private static boolean initialized;
    private static boolean lastNight;

    private NightGhastSpawner() {
    }

    static void tick(MinecraftServer server, long ticks, NightGhastsConfig config) {
        ServerWorld overworld = server.getOverworld();
        if (overworld == null) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] tick(): no overworld");
            }
            return;
        }

        boolean night = isNight(overworld);

        if (!initialized) {
            initialized = true;
            lastNight = night;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] startup: night={} timeOfDay={}", night, (overworld.getTimeOfDay() % 24000L));
            }
            if (!night) {
                int removed = cleanupDaytimeGhasts(overworld);
                if (removed > 0) {
                    LOGGER.info("[Sky-Islands] Daytime startup cleanup: removed {} tagged ghasts", removed);
                }
                nextSpawnTickByPlayer.clear();
                return;
            }
        }

        if (lastNight && !night) {
            // Daybreak: make our spawned ghasts disappear (no drops).
            int removed = cleanupDaytimeGhasts(overworld);
            LOGGER.info("[Sky-Islands] Daybreak cleanup: removed {} tagged ghasts", removed);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] daybreak: cleared schedules (players={})", overworld.getPlayers().size());
            }
            nextSpawnTickByPlayer.clear();
            nextCreativeSkipLogTickByPlayer.clear();
            lastNight = false;
            return;
        }

        if (!lastNight && night) {
            // Night just started: clear per-player cooldowns so spawns can happen immediately.
            nextSpawnTickByPlayer.clear();
            nextCreativeSkipLogTickByPlayer.clear();
            LOGGER.info("[Sky-Islands] Night started: cleared ghast spawn cooldowns");
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] night-start: players={} scanRadius={}", overworld.getPlayers().size(), config.playerScanRadiusBlocks);
            }
        }

        lastNight = night;

        if (!night) {
            return;
        }

        for (ServerPlayerEntity player : overworld.getPlayers()) {
            UUID playerId = player.getUuid();

            long next = nextSpawnTickByPlayer.getOrDefault(playerId, 0L);
            if (ticks < next) {
                if (LOGGER.isDebugEnabled() && (ticks % 200L) == 0) {
                    LOGGER.debug("[Sky-Islands][nightghasts] cooldown player={} now={} next={}",
                            player.getNameForScoreboard(), ticks, next);
                }
                continue;
            }

            if (player.isSpectator() || player.getAbilities().creativeMode) {
                // Still set a schedule so we don't reevaluate this player every tick.
                long scheduled = ticks + config.spawnIntervalTicks;
                nextSpawnTickByPlayer.put(playerId, scheduled);
                if (LOGGER.isDebugEnabled()) {
                    long nextLog = nextCreativeSkipLogTickByPlayer.getOrDefault(playerId, 0L);
                    if (ticks >= nextLog) {
                        nextCreativeSkipLogTickByPlayer.put(playerId, ticks + CREATIVE_SKIP_LOG_INTERVAL_TICKS);
                        LOGGER.debug("[Sky-Islands][nightghasts] skip player={} creativeOrSpec=true nextTick={} (throttled)",
                                player.getNameForScoreboard(), scheduled);
                    }
                }
                continue;
            }

            Vec3d playerPos = new Vec3d(player.getX(), player.getY(), player.getZ());
            int nearby = countTaggedGhastsNear(overworld, playerPos, config.playerScanRadiusBlocks);
            int missing = Math.max(0, config.targetGhastsPerPlayer - nearby);
            int toSpawn = Math.min(missing, config.maxSpawnPerPlayerPerInterval);

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] plan player={} nearby={} missing={} toSpawn={}",
                        player.getNameForScoreboard(), nearby, missing, toSpawn);
            }

            if (toSpawn <= 0) {
                nextSpawnTickByPlayer.put(playerId, ticks + config.spawnIntervalTicks);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][nightghasts] scheduled player={} nextTick={} (no spawn needed)",
                            player.getNameForScoreboard(), (ticks + config.spawnIntervalTicks));
                }
                continue;
            }

            int spawned = 0;
            for (int i = 0; i < toSpawn; i++) {
                SpawnAttemptStats stats = new SpawnAttemptStats();
                if (trySpawnOne(overworld, player, config, stats)) {
                    spawned++;
                    LOGGER.info("[Sky-Islands] Spawned night ghast near {} at ({}, {}, {}) (nearby now ~{})",
                            player.getNameForScoreboard(),
                            round1(stats.spawnX), round1(stats.spawnY), round1(stats.spawnZ),
                            (nearby + spawned));
                } else {
                    // Only log a miss once per interval per player, to avoid spam.
                    if (spawned == 0 && i == 0) {
                        LOGGER.info("[Sky-Islands] No ghast spawned near {} (need {}): unloadedChunks={} noSpace={} spawnFailed={}",
                                player.getNameForScoreboard(),
                                toSpawn,
                                stats.unloadedChunk, stats.noSpace, stats.spawnFailed);
                    }
                }
            }

            nextSpawnTickByPlayer.put(playerId, ticks + config.spawnIntervalTicks);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("[Sky-Islands][nightghasts] scheduled player={} nextTick={} spawned={}",
                        player.getNameForScoreboard(), (ticks + config.spawnIntervalTicks), spawned);
            }
        }
    }

    private static boolean trySpawnOne(ServerWorld world, ServerPlayerEntity player, NightGhastsConfig config, SpawnAttemptStats stats) {
        Vec3d p = new Vec3d(player.getX(), player.getY(), player.getZ());

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][nightghasts] trySpawnOne player={} attempts={}",
                    player.getNameForScoreboard(), config.spawnAttemptsPerInterval);
        }

        for (int attempt = 0; attempt < config.spawnAttemptsPerInterval; attempt++) {
            double angle = world.getRandom().nextDouble() * (Math.PI * 2.0);
            double dist = config.minSpawnDistanceBlocks + world.getRandom().nextDouble() * (config.maxSpawnDistanceBlocks - config.minSpawnDistanceBlocks);

            double x = p.x + Math.cos(angle) * dist;
            double z = p.z + Math.sin(angle) * dist;

            int yOffset = world.getRandom().nextBetween(config.spawnYOffsetMin, config.spawnYOffsetMax);
            double y = p.y + yOffset;

            BlockPos pos = BlockPos.ofFloored(x, y, z);
            if (!world.isChunkLoaded(pos)) {
                stats.unloadedChunk++;
                continue;
            }

            GhastEntity ghast = EntityType.GHAST.create(world, SpawnReason.EVENT);
            if (ghast == null) {
                stats.spawnFailed++;
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][nightghasts] create GHAST failed (player={})", player.getNameForScoreboard());
                }
                return false;
            }

            ghast.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    world.getRandom().nextFloat() * 360.0f, 0.0f);
            ghast.addCommandTag(TAG);

            if (!world.isSpaceEmpty(ghast)) {
                stats.noSpace++;
                ghast.discard();
                continue;
            }

            if (world.spawnEntity(ghast)) {
                stats.spawnX = ghast.getX();
                stats.spawnY = ghast.getY();
                stats.spawnZ = ghast.getZ();
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("[Sky-Islands][nightghasts] spawned tag={} at ({}, {}, {})", TAG,
                            round1(stats.spawnX), round1(stats.spawnY), round1(stats.spawnZ));
                }
                return true;
            }

            stats.spawnFailed++;
            ghast.discard();
        }

        return false;
    }

    private static int countTaggedGhastsNear(ServerWorld world, Vec3d center, int radiusBlocks) {
        Box box = new Box(
                center.x - radiusBlocks, center.y - radiusBlocks, center.z - radiusBlocks,
                center.x + radiusBlocks, center.y + radiusBlocks, center.z + radiusBlocks
        );

        int count = 0;
        for (Entity e : world.getEntitiesByType(EntityType.GHAST, box, entity -> entity.getCommandTags().contains(TAG))) {
            count++;
        }
        return count;
    }

    private static int cleanupDaytimeGhasts(ServerWorld world) {
        int removed = 0;
        for (Entity e : world.iterateEntities()) {
            if (e.getType() == EntityType.GHAST && e.getCommandTags().contains(TAG)) {
                e.discard();
                removed++;
            }
        }

        if (LOGGER.isDebugEnabled() && removed > 0) {
            LOGGER.debug("[Sky-Islands][nightghasts] cleanupDaytimeGhasts removed={}", removed);
        }

        return removed;
    }

    private static boolean isNight(ServerWorld world) {
        long t = world.getTimeOfDay() % 24000L;
        return t >= 13000L && t <= 23000L;
    }

    private static final class SpawnAttemptStats {
        int unloadedChunk;
        int noSpace;
        int spawnFailed;
        double spawnX;
        double spawnY;
        double spawnZ;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }
}
