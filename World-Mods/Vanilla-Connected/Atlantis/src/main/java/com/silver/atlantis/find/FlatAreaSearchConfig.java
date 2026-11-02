package com.silver.atlantis.find;

/**
 * Configuration for searching for a large, relatively-flat build area.
 */
public record FlatAreaSearchConfig(
    int windowSizeBlocks,
    int maxAvgAbsDeviation,
    int thresholdIncreaseEveryAttempts,
    int maxRadiusBlocks,
    int minRadiusBlocks,
    int plateauRadiusBlocks,
    double plateauChance,
    int minRadiusIncreaseEveryAttempts,
    int minRadiusIncreaseBlocks,
    int stepBlocks,
    int maxAttempts,
    long tickTimeBudgetNanos
) {
    public static FlatAreaSearchConfig defaults() {
        return new FlatAreaSearchConfig(
            510,
            8, // y axis deviation (tectonic ocean floors are rarely <= 4 over 510x510)
            10,
            50_000,
            5_000,
            10_000,
            0.70,
            10,
            10_000,
            32, // coarser sampling for speed; still 510/32 = ~16x16 grid
            0, // unlimited attempts
            8_000_000L // lower per-tick budget to reduce server-thread spikes
        );
    }

    public FlatAreaSearchConfig {
        if (windowSizeBlocks <= 0) throw new IllegalArgumentException("windowSizeBlocks must be > 0");
        if (windowSizeBlocks % 2 != 0) throw new IllegalArgumentException("windowSizeBlocks must be even (e.g., 510)");
        if (maxAvgAbsDeviation < 0) throw new IllegalArgumentException("maxAvgAbsDeviation must be >= 0");
        if (thresholdIncreaseEveryAttempts < 0) throw new IllegalArgumentException("thresholdIncreaseEveryAttempts must be >= 0");
        if (maxRadiusBlocks <= 0) throw new IllegalArgumentException("maxRadiusBlocks must be > 0");
        if (minRadiusBlocks < 0) throw new IllegalArgumentException("minRadiusBlocks must be >= 0");
        if (minRadiusBlocks > maxRadiusBlocks) throw new IllegalArgumentException("minRadiusBlocks must be <= maxRadiusBlocks");
        if (plateauRadiusBlocks < 0) throw new IllegalArgumentException("plateauRadiusBlocks must be >= 0");
        if (plateauRadiusBlocks > maxRadiusBlocks) throw new IllegalArgumentException("plateauRadiusBlocks must be <= maxRadiusBlocks");
        if (plateauChance < 0 || plateauChance > 1) throw new IllegalArgumentException("plateauChance must be in [0,1]");
        if (minRadiusIncreaseEveryAttempts < 0) throw new IllegalArgumentException("minRadiusIncreaseEveryAttempts must be >= 0");
        if (minRadiusIncreaseBlocks < 0) throw new IllegalArgumentException("minRadiusIncreaseBlocks must be >= 0");
        if (stepBlocks <= 0) throw new IllegalArgumentException("stepBlocks must be > 0");
        if (maxAttempts < 0) throw new IllegalArgumentException("maxAttempts must be >= 0 (0 = unlimited)");
        if (tickTimeBudgetNanos <= 0) throw new IllegalArgumentException("tickTimeBudgetNanos must be > 0");
    }

    public int thresholdForAttempt(int attempt) {
        int base = maxAvgAbsDeviation;
        int every = thresholdIncreaseEveryAttempts;
        if (every <= 0) {
            return base;
        }

        // Attempt 1-10 => +0, 11-20 => +1, etc.
        int increments = Math.max(0, (attempt - 1) / every);
        return base + increments;
    }
}
