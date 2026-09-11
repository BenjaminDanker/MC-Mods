package com.silver.atlantis.construct;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;

/** Computes a destination that is above terrain while remaining in the water column. */
public final class PlayerEjectTarget {

    private PlayerEjectTarget() {
    }

    public static BlockPos aboveGround(ServerWorld world, int x, int z) {
        int y = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);
        return new BlockPos(x, Math.max(world.getBottomY() + 1, y), z);
    }
}
