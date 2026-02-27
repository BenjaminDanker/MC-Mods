package com.silver.atlantis.spawn.bounds;

import net.minecraft.util.math.BlockPos;

/**
 * World-space bounds of the most recent construct run.
 * Used to scope /structuremob to only the current cycle schematic build.
 */
public record ActiveConstructBounds(
    String runId,
    String dimensionId,
    int minX,
    int minY,
    int minZ,
    int maxX,
    int maxY,
    int maxZ
) {

    public boolean contains(BlockPos pos) {
        if (pos == null) {
            return false;
        }
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        return x >= minX && x <= maxX
            && y >= minY && y <= maxY
            && z >= minZ && z <= maxZ;
    }
}
