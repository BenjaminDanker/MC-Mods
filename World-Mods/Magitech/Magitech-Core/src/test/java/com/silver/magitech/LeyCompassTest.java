package com.silver.magitech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LeyCompassTest {
    @Test
    void northIsMinusZAndEastIsPlusX() {
        assertEquals("away, north", LeyCompass.compass(0, -100));
        assertEquals("away, east", LeyCompass.compass(100, 0));
        assertEquals("away, south", LeyCompass.compass(0, 100));
        assertEquals("away, west", LeyCompass.compass(-100, 0));
    }

    @Test
    void diagonalsAreNamedByBothAxes() {
        assertEquals("away, north-east", LeyCompass.compass(100, -100));
        assertEquals("away, south-east", LeyCompass.compass(100, 100));
        assertEquals("away, south-west", LeyCompass.compass(-100, 100));
        assertEquals("away, north-west", LeyCompass.compass(-100, -100));
    }

    @Test
    void nothingToPointAtIsRightHere() {
        assertEquals("right here", LeyCompass.compass(0, 0));
        assertEquals("right here", LeyCompass.compass(0.0005, -0.0005));
    }

    @Test
    void sectorsSwitchNearTheirBoundary() {
        // 22.5 degrees off south (towards east) is the boundary: tan(22.5) is about 0.4142.
        assertEquals("away, south", LeyCompass.compass(0.40, 1));
        assertEquals("away, south-east", LeyCompass.compass(0.43, 1));
        assertEquals("away, south", LeyCompass.compass(-0.40, 1));
        assertEquals("away, south-west", LeyCompass.compass(-0.43, 1));
        assertEquals("away, north", LeyCompass.compass(0.0, -1), "the +-180 degree seam");
        assertEquals("away, north", LeyCompass.compass(-0.05, -1));
        assertEquals("away, north", LeyCompass.compass(0.05, -1));
    }
}
