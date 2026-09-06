package com.silver.aipets.fabric.config;

/** Validated defaults; loading these values from deployment configuration is a later slice. */
public record PetPhysicalConfig(
        double followStartDistance,
        double followStopDistance,
        int pathRefreshTicks,
        double nearSpeed,
        double mediumSpeed,
        double farSpeed,
        double mediumDistance,
        double farDistance,
        int stuckTimeoutTicks,
        double progressThreshold) {
    public PetPhysicalConfig {
        requirePositiveFinite(followStartDistance, "followStartDistance");
        requireNonNegativeFinite(followStopDistance, "followStopDistance");
        if (followStopDistance >= followStartDistance) {
            throw new IllegalArgumentException("followStopDistance must be less than followStartDistance");
        }
        if (pathRefreshTicks < 1) {
            throw new IllegalArgumentException("pathRefreshTicks must be positive");
        }
        requirePositiveFinite(nearSpeed, "nearSpeed");
        requirePositiveFinite(mediumSpeed, "mediumSpeed");
        requirePositiveFinite(farSpeed, "farSpeed");
        if (nearSpeed > mediumSpeed || mediumSpeed > farSpeed) {
            throw new IllegalArgumentException("Follow speeds must be nondecreasing");
        }
        requirePositiveFinite(mediumDistance, "mediumDistance");
        requirePositiveFinite(farDistance, "farDistance");
        if (mediumDistance < followStartDistance || farDistance <= mediumDistance) {
            throw new IllegalArgumentException("Follow distance bands are invalid");
        }
        if (stuckTimeoutTicks < pathRefreshTicks) {
            throw new IllegalArgumentException("stuckTimeoutTicks must cover at least one refresh interval");
        }
        requirePositiveFinite(progressThreshold, "progressThreshold");
    }

    public static PetPhysicalConfig defaults() {
        return new PetPhysicalConfig(5.0, 2.0, 10, 1.0, 1.25, 1.5, 9.0, 16.0, 60, 0.10);
    }

    public double speedForSquaredDistance(double squaredDistance) {
        if (!Double.isFinite(squaredDistance) || squaredDistance < 0.0) {
            throw new IllegalArgumentException("squaredDistance must be finite and non-negative");
        }
        if (squaredDistance >= farDistance * farDistance) {
            return farSpeed;
        }
        if (squaredDistance >= mediumDistance * mediumDistance) {
            return mediumSpeed;
        }
        return nearSpeed;
    }

    private static void requirePositiveFinite(double value, String name) {
        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegativeFinite(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
