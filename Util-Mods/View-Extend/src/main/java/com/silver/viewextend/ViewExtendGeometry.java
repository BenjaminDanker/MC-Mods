package com.silver.viewextend;

import java.util.function.LongConsumer;
import net.minecraft.world.level.ChunkPos;

final class ViewExtendGeometry {
    private ViewExtendGeometry() {
    }

    static int ringPerimeter(int radius) {
        return radius <= 0 ? 0 : radius * 8;
    }

    static long ringChunk(int centerX, int centerZ, int radius, int offset) {
        int perimeter = ringPerimeter(radius);
        if (radius <= 0 || offset < 0 || offset >= perimeter) {
            throw new IllegalArgumentException("Invalid ring radius/offset: " + radius + "/" + offset);
        }

        int side = radius * 2 + 1;
        int x;
        int z;
        if (offset < side) {
            x = -radius + offset;
            z = -radius;
        } else if (offset < side * 2) {
            x = -radius + (offset - side);
            z = radius;
        } else {
            int remainder = offset - side * 2;
            int interiorIndex = remainder / 2;
            z = -radius + 1 + interiorIndex;
            x = remainder % 2 == 0 ? -radius : radius;
        }
        return ChunkPos.pack(centerX + x, centerZ + z);
    }

    static void forEachSquareDifference(
            int includedCenterX,
            int includedCenterZ,
            int includedRadius,
            int excludedCenterX,
            int excludedCenterZ,
            int excludedRadius,
            LongConsumer consumer) {
        if (includedRadius < 0) {
            return;
        }
        int includedMinX = includedCenterX - includedRadius;
        int includedMaxX = includedCenterX + includedRadius;
        int includedMinZ = includedCenterZ - includedRadius;
        int includedMaxZ = includedCenterZ + includedRadius;
        int excludedMinX = excludedCenterX - excludedRadius;
        int excludedMaxX = excludedCenterX + excludedRadius;
        int excludedMinZ = excludedCenterZ - excludedRadius;
        int excludedMaxZ = excludedCenterZ + excludedRadius;

        for (int x = includedMinX; x <= includedMaxX; x++) {
            if (excludedRadius < 0 || x < excludedMinX || x > excludedMaxX) {
                emitRange(x, includedMinZ, includedMaxZ, consumer);
                continue;
            }
            emitRange(x, includedMinZ, Math.min(includedMaxZ, excludedMinZ - 1), consumer);
            emitRange(x, Math.max(includedMinZ, excludedMaxZ + 1), includedMaxZ, consumer);
        }
    }

    private static void emitRange(int x, int minZ, int maxZ, LongConsumer consumer) {
        for (int z = minZ; z <= maxZ; z++) {
            consumer.accept(ChunkPos.pack(x, z));
        }
    }
    static boolean withinDistance(int x, int z, int centerX, int centerZ, int radius) {
        return Math.abs(x - centerX) <= radius && Math.abs(z - centerZ) <= radius;
    }
}
