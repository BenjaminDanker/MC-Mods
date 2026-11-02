package com.silver.aipets.common.transport;

import java.util.Objects;

/** Server-sampled place, never coordinates extracted from owner text. Unknown is Optional.empty(). */
public record SpatialLocation(String server, String world, String dimension,
        double x, double y, double z) {
    public SpatialLocation {
        server = identifier(server, 128);
        world = identifier(world, 191);
        dimension = identifier(dimension, 191);
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > 30_000_000 || Math.abs(z) > 30_000_000
                || Math.abs(y) > 30_000_000) throw new IllegalArgumentException("Invalid coordinates");
    }

    public boolean sameSpace(SpatialLocation other) {
        return server.equals(other.server) && world.equals(other.world)
                && dimension.equals(other.dimension);
    }

    public double distanceTo(SpatialLocation other) {
        return Math.hypot(Math.hypot(x - other.x, z - other.z), y - other.y);
    }

    private static String identifier(String value, int maximum) {
        Objects.requireNonNull(value, "location identifier");
        if (value.isBlank() || value.equalsIgnoreCase("unknown") || value.length() > maximum
                || value.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Invalid location identifier");
        }
        return value;
    }
}
