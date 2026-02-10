package com.silver.villagerinterface.config;

import net.minecraft.util.math.Vec3d;

public final class VillagerPosition {
    private final double x;
    private final double y;
    private final double z;

    public VillagerPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public Vec3d toVec3d() {
        return new Vec3d(x, y, z);
    }
}
