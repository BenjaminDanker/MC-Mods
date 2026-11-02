package com.silver.atlantis.construct.state;

/**
 * Persistent construct progress so a restart can resume without redoing
 * already completed passes.
 */
public record ConstructRunState(
    int version,
    String runId,
    String dimension,
    long createdAtEpochMillis,
    int rawCenterX,
    int rawCenterY,
    int rawCenterZ,
    int yOffsetBlocks,
    String pass, // SOLIDS | FLUIDS
    int nextPassIndex,
    String stage, // best-effort checkpoint
    Integer anchorToX,
    Integer anchorToY,
    Integer anchorToZ,
    Integer overallMinX,
    Integer overallMinY,
    Integer overallMinZ,
    Integer overallMaxX,
    Integer overallMaxY,
    Integer overallMaxZ,
    boolean completed
) {
    public static final int CURRENT_VERSION = 1;
}
