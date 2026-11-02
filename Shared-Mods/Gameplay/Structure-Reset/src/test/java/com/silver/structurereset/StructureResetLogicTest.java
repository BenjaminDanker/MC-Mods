package com.silver.structurereset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class StructureResetLogicTest {
    @Test
    void slicesCoverTheZoneOnceAcrossChunkBorders() {
        Zone zone = new Zone(-5, 60, 10, 20, 90, 40);
        List<Zone.Slice> slices = zone.slices();
        assertEquals(3 * 3, slices.size());
        long covered = 0;
        for (Zone.Slice s : slices) {
            covered += (long) s.sizeX() * s.sizeZ();
        }
        assertEquals((long) 26 * 31, covered);
        assertEquals(new Zone.Slice(-1, 0, -5, 10, 5, 6), slices.get(0));
    }

    @Test
    void zoneContainsItsBordersOnly() {
        Zone zone = new Zone(0, 0, 0, 9, 9, 9);
        assertTrue(zone.contains(0, 0, 9));
        assertFalse(zone.contains(10, 5, 5));
        assertFalse(zone.contains(5, -1, 5));
        assertEquals(1000, zone.volume());
    }

    @Test
    void ejectPointIsOutsideTheNearestSide() {
        Zone zone = new Zone(0, 0, 0, 100, 50, 60);
        assertArrayEquals(new int[] {-3, 20}, zone.ejectPoint(2, 20, 3));
        assertArrayEquals(new int[] {104, 20}, zone.ejectPoint(98, 20, 4));
        assertArrayEquals(new int[] {50, -3}, zone.ejectPoint(50, 1, 3));
        assertArrayEquals(new int[] {50, 63}, zone.ejectPoint(50, 59, 3));
    }

    @Test
    void filterMatchesIdsAndNamespaces() {
        StructureFilter filter = new StructureFilter(List.of("minecraft:desert_pyramid", "mss:*"));
        assertTrue(filter.matches("minecraft:desert_pyramid"));
        assertTrue(filter.matches("mss:castle_tower"));
        assertFalse(filter.matches("minecraft:village_plains"));
        assertFalse(filter.matches("mssx:castle"));
    }

    @Test
    void beatenInstanceResetsOnlyAfterTheInterval() {
        Instance instance = new Instance();
        instance.state = Instance.State.READY;
        assertFalse(instance.dueForReset(1_000_000, 1000));
        instance.markBeaten(1_000);
        assertFalse(instance.dueForReset(1_500, 1000));
        assertTrue(instance.dueForReset(2_000, 1000));
        instance.markRestored();
        assertFalse(instance.dueForReset(10_000, 1000));
        assertEquals(1, instance.resets);
    }

    @Test
    void openingEveryContainerBeatsTheStructure() {
        Instance instance = new Instance();
        instance.lootPositions = List.of(1L, 2L, 3L);
        instance.lootPositions = new java.util.ArrayList<>(instance.lootPositions);
        assertFalse(instance.markOpened(1L));
        assertFalse(instance.markOpened(1L));
        assertFalse(instance.markOpened(99L));
        assertFalse(instance.markOpened(2L));
        assertTrue(instance.markOpened(3L));
    }
}
