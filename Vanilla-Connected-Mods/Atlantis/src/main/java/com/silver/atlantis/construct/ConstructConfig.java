package com.silver.atlantis.construct;

/**
 * Simple pacing knobs to avoid watchdog freezes when pasting large schematics.
 */
public record ConstructConfig(
    int delayBetweenStagesTicks,
    int delayBetweenSlicesTicks,
    int maxChunksToLoadPerTick,
    int yOffsetBlocks,
    int maxEntitiesToProcessPerTick,
    int playerEjectMarginBlocks,
    int playerEjectTeleportOffsetBlocks,
    int pasteFlushEveryBlocks,
    long tickTimeBudgetNanos,
    long undoTickTimeBudgetNanos,
    int undoFlushEveryBlocks,
    int maxFluidNeighborUpdatesPerTick,
    long expectedTickNanos,
    double adaptiveScaleSmoothing,
    double adaptiveScaleMin,
    double adaptiveScaleMax
) {

    public static ConstructConfig defaults() {
        return new ConstructConfig(
            20,         // 1s between heavy stages
            80,         // 4s between slices
            4,           // load up to 4 chunks per tick (still time-budgeted)
            /* yOffsetBlocks */ -62,                // positive raises build; negative sinks build
            250,        // max entities processed per tick during cleanup
            50,        // expands the protected no-entry footprint around the build
            10,        // teleport target offset outside the protected footprint
            8192,      // flush/commit this often during paste so clients see incremental progress
            20_000_000L,      // 20ms per tick
            10_000_000L,       // 10ms per tick for undo (prevents GPU-melting client update floods)
            1024,              // smaller batches reduce client spikes and end-of-undo hitches
            50,              // cap fluid neighbor update pulses per tick (prevents multi-second spikes)
            50_000_000L,
            0.20d,
            0.50d,
            1.50d
        );
    }
}
