package com.silver.structurereset;

import java.util.ArrayList;
import java.util.List;

/** An inclusive block box, with the chunk-column slices a snapshot is stored in and where to put people who must leave. */
public record Zone(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    /** The part of a zone inside one chunk column. */
    public record Slice(int chunkX, int chunkZ, int x, int z, int sizeX, int sizeZ) {
    }

    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public List<Slice> slices() {
        List<Slice> slices = new ArrayList<>();
        for (int cx = Math.floorDiv(minX, 16); cx <= Math.floorDiv(maxX, 16); cx++) {
            for (int cz = Math.floorDiv(minZ, 16); cz <= Math.floorDiv(maxZ, 16); cz++) {
                int x0 = Math.max(minX, cx * 16);
                int x1 = Math.min(maxX, cx * 16 + 15);
                int z0 = Math.max(minZ, cz * 16);
                int z1 = Math.min(maxZ, cz * 16 + 15);
                slices.add(new Slice(cx, cz, x0, z0, x1 - x0 + 1, z1 - z0 + 1));
            }
        }
        return slices;
    }

    /** The x and z just outside the nearest side of the zone, for someone standing at (x, z) inside it. */
    public int[] ejectPoint(int x, int z, int padding) {
        int west = x - minX;
        int east = maxX - x;
        int north = z - minZ;
        int south = maxZ - z;
        int nearest = Math.min(Math.min(west, east), Math.min(north, south));
        int clampedX = Math.max(minX, Math.min(maxX, x));
        int clampedZ = Math.max(minZ, Math.min(maxZ, z));
        if (nearest == west) {
            return new int[] {minX - padding, clampedZ};
        }
        if (nearest == east) {
            return new int[] {maxX + padding, clampedZ};
        }
        if (nearest == north) {
            return new int[] {clampedX, minZ - padding};
        }
        return new int[] {clampedX, maxZ + padding};
    }
}
