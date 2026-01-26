package com.silver.atlantis.construct.undo;

import java.util.List;

public record UndoRunMetadata(
    int version,
    String runId,
    String dimension,
    long createdAtEpochMillis,
    int yOffsetBlocks,
    int rawCenterX,
    int rawCenterY,
    int rawCenterZ,
    List<String> sliceFiles
) {
    public static final int CURRENT_VERSION = 1;
}
