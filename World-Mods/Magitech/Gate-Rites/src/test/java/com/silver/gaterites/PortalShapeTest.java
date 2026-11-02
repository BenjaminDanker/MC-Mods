package com.silver.gaterites;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PortalShapeTest {
    private final PortalShape portal = new PortalShape(10.0, 65.35, 20.0);

    @Test
    void walkingIntoThePortalCounts() {
        assertTrue(portal.contains(10.0, 64.45, 20.0), "standing in the middle");
        assertTrue(portal.contains(10.6, 64.45, 20.0), "brushing the edge");
    }

    @Test
    void standingBesideItDoesNot() {
        assertFalse(portal.contains(11.5, 64.45, 20.0));
        assertFalse(portal.contains(10.0, 62.0, 20.0), "far below");
        assertFalse(portal.contains(10.0, 68.0, 20.0), "far above");
    }

    @Test
    void aStrangerIsTurnedAwayBeforeReachingIt() {
        assertTrue(portal.near(11.4, 64.45, 20.0));
        assertFalse(portal.near(12.0, 64.45, 20.0));
        assertFalse(portal.contains(11.4, 64.45, 20.0));
    }
}
