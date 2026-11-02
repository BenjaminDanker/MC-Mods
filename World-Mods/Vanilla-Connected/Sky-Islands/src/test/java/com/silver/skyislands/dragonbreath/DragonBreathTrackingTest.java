package com.silver.skyislands.dragonbreath;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DragonBreathTrackingTest {
    @Test
    void northIsMinusZAndEastIsPlusX() {
        assertEquals("north", DragonBreathTracking.compass(0, -100));
        assertEquals("east", DragonBreathTracking.compass(100, 0));
        assertEquals("south", DragonBreathTracking.compass(0, 100));
        assertEquals("west", DragonBreathTracking.compass(-100, 0));
    }

    @Test
    void diagonals() {
        assertEquals("north-east", DragonBreathTracking.compass(100, -100));
        assertEquals("south-east", DragonBreathTracking.compass(100, 100));
        assertEquals("south-west", DragonBreathTracking.compass(-100, 100));
        assertEquals("north-west", DragonBreathTracking.compass(-100, -100));
    }

    @Test
    void zeroIsRightHere() {
        assertEquals("right here", DragonBreathTracking.compass(0, 0));
    }

    @Test
    void sectorsSwitchNearTheirBoundary() {
        assertEquals("south", DragonBreathTracking.compass(0.40, 1));
        assertEquals("south-east", DragonBreathTracking.compass(0.43, 1));
        assertEquals("south", DragonBreathTracking.compass(-0.40, 1));
        assertEquals("south-west", DragonBreathTracking.compass(-0.43, 1));
        assertEquals("north", DragonBreathTracking.compass(0.0, -1));
    }
}
