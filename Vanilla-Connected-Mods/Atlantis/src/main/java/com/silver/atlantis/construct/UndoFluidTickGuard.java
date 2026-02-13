package com.silver.atlantis.construct;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Global gate for suppressing fluid scheduled ticks during an active undo.
 *
 * Scope is intentionally narrow:
 * - only while undo has activated the guard
 * - only in the specified dimension
 * - only inside the configured inclusive bounds
 */
public final class UndoFluidTickGuard {

    public static final UndoFluidTickGuard INSTANCE = new UndoFluidTickGuard();

    private volatile boolean active;
    private volatile String dimensionId;
    private volatile int minX;
    private volatile int minY;
    private volatile int minZ;
    private volatile int maxX;
    private volatile int maxY;
    private volatile int maxZ;

    private UndoFluidTickGuard() {
    }

    public synchronized void activate(String dimensionId, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (dimensionId == null || dimensionId.isBlank()) {
            return;
        }

        this.dimensionId = dimensionId;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
        this.active = true;
    }

    public synchronized void clear() {
        this.active = false;
        this.dimensionId = null;
    }

    public boolean shouldSuppress(ServerWorld world, BlockPos pos) {
        if (!active || world == null || pos == null) {
            return false;
        }

        String dim = dimensionId;
        if (dim == null || !dim.equals(world.getRegistryKey().getValue().toString())) {
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
