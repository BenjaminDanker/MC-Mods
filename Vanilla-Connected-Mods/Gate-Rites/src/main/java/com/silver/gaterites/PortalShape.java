package com.silver.gaterites;

/** Where a floating portal can be walked into. Pure geometry: the portal is an oval standing at {@code cx, cy, cz}. */
record PortalShape(double cx, double cy, double cz) {
    /** How far from the portal's axis a body may be and still be inside it. */
    static final double REACH = 0.85;
    /** How far above or below the portal's centre a body's middle may be. */
    static final double HALF_HEIGHT = 1.2;
    /** A stranger this close is turned away. */
    static final double WARDING = 1.5;

    /** {@code feetY} is the traveller's feet; their body's middle is taken as 0.9 above. */
    boolean contains(double x, double feetY, double z) {
        return Math.hypot(x - cx, z - cz) <= REACH && Math.abs(feetY + 0.9 - cy) <= HALF_HEIGHT;
    }

    boolean near(double x, double feetY, double z) {
        return Math.hypot(x - cx, z - cz) <= WARDING && Math.abs(feetY + 0.9 - cy) <= HALF_HEIGHT + 0.5;
    }
}
