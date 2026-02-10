package com.silver.atlantis.find;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldProperties;

import java.util.Random;

public final class FlatAreaFinder {

    private FlatAreaFinder() {}

    public static FlatAreaSearchResult find(ServerWorld world, Random random, FlatAreaSearchConfig config) {
        SurfaceHeightSampler heightSampler = WorldgenSurfaceHeightSampler.forWorld(world);
        WorldProperties.SpawnPoint spawnPoint = null;
        if (world.getServer() != null) {
            spawnPoint = world.getServer().getSpawnPoint();
        }
        if (spawnPoint == null) {
            spawnPoint = world.getLevelProperties().getSpawnPoint();
        }
        BlockPos spawn = spawnPoint != null ? spawnPoint.getPos() : BlockPos.ORIGIN;

        FlatAreaSearchResult bestNearMiss = null;

        // NOTE: This method is synchronous and will block the server thread if called directly.
        // If maxAttempts is unlimited (<=0), apply a safety cap here.
        int maxAttempts = config.maxAttempts() > 0 ? config.maxAttempts() : 10_000;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            BlockPos center = DistanceBiasedCandidateSampler.sampleCenter(spawn, random, config, attempt);

            FlatAreaSearchResult eval = evaluateWindow(world, heightSampler, center, config.windowSizeBlocks(), config.stepBlocks(), attempt);
            if (eval.avgAbsDeviation() <= config.maxAvgAbsDeviation()) {
                return eval;
            }

            if (bestNearMiss == null || eval.avgAbsDeviation() < bestNearMiss.avgAbsDeviation()) {
                bestNearMiss = eval;
            }
        }

        // No perfect hit; return the best candidate found.
        return bestNearMiss;
    }

    private static FlatAreaSearchResult evaluateWindow(
        ServerWorld world,
        SurfaceHeightSampler sampler,
        BlockPos center,
        int windowSize,
        int step,
        int attempt
    ) {
        int half = windowSize / 2;
        int startX = center.getX() - half;
        int startZ = center.getZ() - half;
        int endXExclusive = startX + windowSize;
        int endZExclusive = startZ + windowSize;

        int samplesX = (int) Math.ceil(windowSize / (double) step);
        int samplesZ = (int) Math.ceil(windowSize / (double) step);
        int sampleCount = samplesX * samplesZ;
        int[] heights = new int[sampleCount];

        long sum = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        int idx = 0;
        for (int x = startX; x < endXExclusive; x += step) {
            for (int z = startZ; z < endZExclusive; z += step) {
                int y = sampler.sampleSurfaceY(x, z);
                heights[idx++] = y;
                sum += y;
                if (y < min) min = y;
                if (y > max) max = y;
            }
        }

        int actualSamples = idx;
        double mean = sum / (double) actualSamples;

        double devSum = 0.0;
        for (int i = 0; i < actualSamples; i++) {
            devSum += Math.abs(heights[i] - mean);
        }
        double avgAbsDev = devSum / actualSamples;

        // Put Y at the rounded mean (useful for build placement), but keep X/Z as the real center.
        BlockPos yAdjustedCenter = new BlockPos(center.getX(), (int) Math.round(mean), center.getZ());

        return new FlatAreaSearchResult(
            yAdjustedCenter,
            actualSamples,
            mean,
            avgAbsDev,
            min,
            max,
            attempt
        );
    }
}
