package com.silver.atlantis.find;

import net.minecraft.util.math.BlockPos;

import java.util.Random;

/**
 * Picks candidate centers around spawn with a higher probability near spawn,
 * capped so it won't always choose right on top of spawn.
 */
public final class DistanceBiasedCandidateSampler {

    private DistanceBiasedCandidateSampler() {}

    public static BlockPos sampleCenter(BlockPos spawn, Random random, FlatAreaSearchConfig config) {
        return sampleCenter(spawn, random, config, 1);
    }

    public static BlockPos sampleCenter(BlockPos spawn, Random random, FlatAreaSearchConfig config, int attempt) {
        int radius = sampleRadius(random, config, attempt);
        double angle = random.nextDouble() * (Math.PI * 2.0);

        int dx = (int) Math.round(Math.cos(angle) * radius);
        int dz = (int) Math.round(Math.sin(angle) * radius);

        int x = spawn.getX() + dx;
        int z = spawn.getZ() + dz;

        // Keep centers roughly aligned so repeated searches don't oscillate between near-identical windows.
        // (This still remains random because radius/angle are random.)
        int half = config.windowSizeBlocks() / 2;
        x = alignToWindowCenter(x, half);
        z = alignToWindowCenter(z, half);

        return new BlockPos(x, spawn.getY(), z);
    }

    private static int alignToWindowCenter(int coord, int halfWindow) {
        // Align such that window bounds land on a stable grid.
        int window = halfWindow * 2;
        int base = Math.floorDiv(coord - halfWindow, window) * window;
        return base + halfWindow;
    }

    private static int sampleRadius(Random random, FlatAreaSearchConfig config, int attempt) {
        int max = config.maxRadiusBlocks();
        int min = config.minRadiusBlocks();
        int plateau = config.plateauRadiusBlocks();

        int a = Math.max(1, attempt);
        int every = config.minRadiusIncreaseEveryAttempts();
        int step = config.minRadiusIncreaseBlocks();

        int floor = min;
        if (every > 0 && step > 0) {
            floor = Math.max(floor, ((a - 1) / every) * step);
        }
        floor = Math.max(0, Math.min(max, floor));

        if (floor >= max) {
            return max;
        }

        if (plateau <= 0) {
            // Bias towards the minimum radius (floor) using U^k.
            double u = random.nextDouble();
            double k = 2.25;
            int remaining = Math.max(1, max - floor);
            int tail = (int) Math.round(remaining * Math.pow(u, k));
            return floor + tail;
        }

        // If our minimum radius has already moved past the plateau, the plateau is irrelevant.
        if (floor < plateau) {
            if (random.nextDouble() < config.plateauChance()) {
                // Sample uniformly in [floor..plateau]
                return floor + random.nextInt((plateau - floor) + 1);
            }
        }

        // Outside plateau: still biased inward within the remaining search band.
        int start = Math.max(floor, plateau);
        int remaining = Math.max(1, max - start);
        double u = random.nextDouble();
        double k = 1.75;
        int tail = (int) Math.round(remaining * Math.pow(u, k));
        return start + tail;
    }
}
