package com.silver.aipets.common.domain;

/** Immutable, finite three-dimensional world coordinates. */
public record WorldPosition(double x, double y, double z) {
    public WorldPosition {
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
    }

    public double distanceTo(WorldPosition other) {
        java.util.Objects.requireNonNull(other, "other");
        return Math.hypot(Math.hypot(x - other.x, y - other.y), z - other.z);
    }

    public boolean isWithin(WorldPosition other, double maximumDistance) {
        if (!Double.isFinite(maximumDistance) || maximumDistance < 0.0) {
            throw new IllegalArgumentException("maximumDistance must be finite and non-negative");
        }
        return distanceTo(other) <= maximumDistance;
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
