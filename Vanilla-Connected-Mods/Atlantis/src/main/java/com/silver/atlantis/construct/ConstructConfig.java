package com.silver.atlantis.construct;

/**
 * Simple pacing knobs to avoid watchdog freezes when pasting large schematics.
 */
public record ConstructConfig(
    int delayBetweenSlicesTicks,
    int yOffsetBlocks,
    int playerEjectMarginBlocks,
    int playerEjectTeleportOffsetBlocks,
    long tickTimeBudgetNanos,
    long undoTickTimeBudgetNanos
) {

    public ConstructConfig withYOffsetBlocks(int yOffsetBlocks) {
        return new ConstructConfig(
            delayBetweenSlicesTicks,
            yOffsetBlocks,
            playerEjectMarginBlocks,
            playerEjectTeleportOffsetBlocks,
            tickTimeBudgetNanos,
            undoTickTimeBudgetNanos
        );
    }

    public static ConstructConfig defaults() {
        return new ConstructConfig(
            80,         // 4s between slices
            /* yOffsetBlocks */ -62,                // positive raises build; negative sinks build
            50,        // expands the protected no-entry footprint around the build
            10,        // teleport target offset outside the protected footprint
            20_000_000L,      // 20ms per tick
            10_000_000L       // 10ms per tick for undo (prevents client update floods)
        );
    }
}
