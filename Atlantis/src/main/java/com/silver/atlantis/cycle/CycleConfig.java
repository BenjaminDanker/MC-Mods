package com.silver.atlantis.cycle;

import com.silver.atlantis.construct.ConstructConfig;

/**
 * Hard-coded cycle settings.
 *
 * Intentionally not persisted to disk.
 */
public final class CycleConfig {

    private CycleConfig() {
    }

    /** Wait time between /structuremob and /construct undo. */
    public static final long UNDO_DELAY_MILLIS = 48L * 60L * 60L * 1000L;

    /** Y offset passed to construct when running the cycle. */
    public static final int CONSTRUCT_Y_OFFSET_BLOCKS = ConstructConfig.defaults().yOffsetBlocks();
}
