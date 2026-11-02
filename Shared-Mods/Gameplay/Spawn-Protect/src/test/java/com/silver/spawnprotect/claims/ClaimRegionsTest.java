package com.silver.spawnprotect.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClaimRegionsTest {
    private static Set<Long> chunks(int[][] positions) {
        Set<Long> set = new HashSet<>();
        for (int[] p : positions) {
            set.add(ClaimRegions.key(p[0], p[1]));
        }
        return set;
    }

    @Test
    void attachedChunksFormOneRegardlessOfShape() {
        List<ClaimRegions.Region> regions = ClaimRegions.connected(chunks(new int[][] {{0, 0}, {1, 0}, {2, 0}, {2, 1}, {2, 2}}));
        assertEquals(1, regions.size());
        assertEquals(5, regions.get(0).chunks());
        assertEquals(0, regions.get(0).minX());
        assertEquals(2, regions.get(0).maxZ());
    }

    @Test
    void separatedClaimsStayApart() {
        List<ClaimRegions.Region> regions = ClaimRegions.connected(chunks(new int[][] {{0, 0}, {1, 0}, {10, 10}}));
        assertEquals(2, regions.size());
        assertEquals(2, regions.get(0).chunks());
        assertEquals(1, regions.get(1).chunks());
    }

    @Test
    void cornerTouchingIsNotAttached() {
        assertEquals(2, ClaimRegions.connected(chunks(new int[][] {{0, 0}, {1, 1}})).size());
    }

    @Test
    void negativeCoordinatesAndTheCenterBelongToTheRegion() {
        Set<Long> area = chunks(new int[][] {{-3, -3}, {-2, -3}, {-1, -3}, {-1, -2}, {-1, -1}});
        ClaimRegions.Region region = ClaimRegions.connected(area).get(0);
        assertEquals(5, region.chunks());
        assertTrue(area.contains(ClaimRegions.key(region.centerX(), region.centerZ())));
    }

    @Test
    void centreIsTheMiddleOfTheClaimNotTheMiddleOfAThinTail() {
        // A 5x5 block with a long one-chunk tail: the centre must stay in the block, not drift along the tail.
        Set<Long> area = new HashSet<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                area.add(ClaimRegions.key(x, z));
            }
        }
        for (int x = 5; x < 12; x++) {
            area.add(ClaimRegions.key(x, 2));
        }
        ClaimRegions.Region region = ClaimRegions.connected(area).get(0);
        assertEquals(5, region.centerX());
        assertEquals(2, region.centerZ());
    }

    @Test
    void largestRegionComesFirst() {
        List<ClaimRegions.Region> regions = ClaimRegions.connected(chunks(new int[][] {{0, 0}, {50, 50}, {51, 50}, {52, 50}}));
        assertEquals(3, regions.get(0).chunks());
    }
}
