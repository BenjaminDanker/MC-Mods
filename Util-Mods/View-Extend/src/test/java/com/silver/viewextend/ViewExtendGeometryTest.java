package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

class ViewExtendGeometryTest {
    @Test
    void ringVisitsEveryPerimeterChunkExactlyOnce() {
        int centerX = 17;
        int centerZ = -23;
        int radius = 4;
        Set<Long> visited = new HashSet<>();

        for (int offset = 0; offset < ViewExtendGeometry.ringPerimeter(radius); offset++) {
            long packed = ViewExtendGeometry.ringChunk(centerX, centerZ, radius, offset);
            visited.add(packed);
            int distance = Math.max(
                    Math.abs(ChunkPos.getX(packed) - centerX),
                    Math.abs(ChunkPos.getZ(packed) - centerZ));
            assertEquals(radius, distance);
        }

        assertEquals(radius * 8, visited.size());
    }

    @Test
    void oneChunkMoveOnlyProducesEnteringStrip() {
        Set<Long> difference = new HashSet<>();
        ViewExtendGeometry.forEachSquareDifference(1, 0, 2, 0, 0, 2, difference::add);

        assertEquals(5, difference.size());
        for (long packed : difference) {
            assertEquals(3, ChunkPos.getX(packed));
            assertTrue(ChunkPos.getZ(packed) >= -2 && ChunkPos.getZ(packed) <= 2);
        }
    }

    @Test
    void diagonalMoveHasNoDuplicateCorner() {
        Set<Long> difference = new HashSet<>();
        ViewExtendGeometry.forEachSquareDifference(1, 1, 2, 0, 0, 2, difference::add);
        assertEquals(9, difference.size());
    }

    @Test
    void fullyContainedSquareProducesNoDifference() {
        Set<Long> difference = new HashSet<>();
        ViewExtendGeometry.forEachSquareDifference(0, 0, 1, 0, 0, 3, difference::add);
        assertTrue(difference.isEmpty());
    }
}
