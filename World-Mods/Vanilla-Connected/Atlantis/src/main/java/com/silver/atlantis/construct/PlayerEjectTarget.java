package com.silver.atlantis.construct;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/** Computes a destination that is above terrain while remaining in the water column. */
public final class PlayerEjectTarget {

    private PlayerEjectTarget() {
    }

    public static BlockPos aboveGround(ServerLevel world, int x, int z) {
        int y = world.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z);
        return new BlockPos(x, Math.max(world.getMinY() + 1, y), z);
    }
}
