package com.silver.atlantis.find;

import net.minecraft.core.BlockPos;

public record FlatAreaSearchResult(
    BlockPos center,
    int sampleCount,
    double meanHeight,
    double avgAbsDeviation,
    int minHeight,
    int maxHeight,
    int attempts
) {}
