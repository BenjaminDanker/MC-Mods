package com.silver.atlantis.cycle;

public record CycleState(
    int version,
    boolean enabled,
    long nextRunAtEpochMillis,
    String stage,
    Integer lastCenterX,
    Integer lastCenterY,
    Integer lastCenterZ,
    String lastConstructRunId
) {
    public static final int CURRENT_VERSION = 1;

    public static CycleState disabled() {
        return new CycleState(CURRENT_VERSION, false, 0L, Stage.IDLE.name(), null, null, null, null);
    }

    public enum Stage {
        IDLE,
        WAIT_FIND_FLAT,
        START_CONSTRUCT,
        WAIT_CONSTRUCT,
        RUN_STRUCTUREMOB,
        WAIT_STRUCTUREMOB,
        WAIT_BEFORE_UNDO,
        START_UNDO,
        WAIT_UNDO
    }
}
