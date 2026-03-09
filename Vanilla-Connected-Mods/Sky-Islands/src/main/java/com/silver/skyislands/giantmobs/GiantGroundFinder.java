package com.silver.skyislands.giantmobs;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class GiantGroundFinder {
    private final Map<Long, CachedGround> cache = new HashMap<>();
    private final Map<Long, Long> nextAllowedScanTick = new HashMap<>();

    public Optional<BlockPos> findSpawnPosAtColumn(ServerWorld world,
                                                   int centerX,
                                                   int centerZ,
                                                   GiantMobsConfig config,
                                                   Random random,
                                                   long serverTick) {
        Optional<BlockPos> ground = findGroundColumn(world, centerX, centerZ, config, serverTick);
        if (ground.isPresent()) {
            return ground.map(pos -> pos.up(config.spawnHeightAboveGround));
        }

        int radius = config.groundSearchHorizontalRadiusBlocks;
        if (radius <= 0) {
            return Optional.empty();
        }

        for (int attempt = 0; attempt < config.spawnSearchAttempts; attempt++) {
            int offsetX = random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = random.nextInt(radius * 2 + 1) - radius;
            ground = findGroundColumn(world, centerX + offsetX, centerZ + offsetZ, config, serverTick);
            if (ground.isPresent()) {
                return ground.map(pos -> pos.up(config.spawnHeightAboveGround));
            }
        }

        return Optional.empty();
    }

    public Optional<BlockPos> findSpawnPosNear(ServerWorld world,
                                               Vec3d center,
                                               GiantMobsConfig config,
                                               Random random,
                                               long serverTick) {
        int centerX = (int) Math.floor(center.x);
        int centerZ = (int) Math.floor(center.z);

        for (int attempt = 0; attempt < config.spawnSearchAttempts; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            int distance = config.minSpawnDistanceBlocks;
            if (config.maxSpawnDistanceBlocks > config.minSpawnDistanceBlocks) {
                distance += random.nextInt(config.maxSpawnDistanceBlocks - config.minSpawnDistanceBlocks + 1);
            }

            int sampleX = centerX + (int) Math.round(Math.cos(angle) * distance);
            int sampleZ = centerZ + (int) Math.round(Math.sin(angle) * distance);

            Optional<BlockPos> ground = findGroundColumn(world, sampleX, sampleZ, config, serverTick);
            if (ground.isPresent()) {
                return ground.map(pos -> pos.up(config.spawnHeightAboveGround));
            }

            int radius = config.groundSearchHorizontalRadiusBlocks;
            if (radius <= 0) {
                continue;
            }

            int offsetX = random.nextInt(radius * 2 + 1) - radius;
            int offsetZ = random.nextInt(radius * 2 + 1) - radius;
            ground = findGroundColumn(world, sampleX + offsetX, sampleZ + offsetZ, config, serverTick);
            if (ground.isPresent()) {
                return ground.map(pos -> pos.up(config.spawnHeightAboveGround));
            }
        }

        return Optional.empty();
    }

    private Optional<BlockPos> findGroundColumn(ServerWorld world,
                                                int x,
                                                int z,
                                                GiantMobsConfig config,
                                                long serverTick) {
        long key = pack(x, z);
        CachedGround cached = cache.get(key);
        if (cached != null && cached.expiresAtTick >= serverTick) {
            return Optional.of(cached.pos);
        }

        long nextAllowed = nextAllowedScanTick.getOrDefault(key, 0L);
        if (serverTick < nextAllowed) {
            return Optional.empty();
        }

        nextAllowedScanTick.put(key, serverTick + config.groundSearchCooldownTicks);

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, x, z);
        int minY = Math.max(world.getBottomY(), topY - config.groundSearchVerticalRangeBlocks);
        for (int y = topY; y >= minY; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            if (world.getBlockState(pos).isAir()) {
                continue;
            }
            if (!world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP)) {
                continue;
            }

            cache.put(key, new CachedGround(pos, serverTick + config.groundCacheTtlTicks));
            return Optional.of(pos);
        }

        return Optional.empty();
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private record CachedGround(BlockPos pos, long expiresAtTick) {
    }
}