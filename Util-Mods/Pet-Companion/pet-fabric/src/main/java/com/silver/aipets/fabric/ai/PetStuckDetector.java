package com.silver.aipets.fabric.ai;

import net.minecraft.util.math.Vec3d;

import java.util.Objects;

/** Counts only sampled intervals with negligible movement while follow navigation is expected. */
final class PetStuckDetector {
    private final int timeoutTicks;
    private final double progressThresholdSquared;

    private Vec3d lastPosition;
    private int ticksWithoutProgress;

    PetStuckDetector(int timeoutTicks, double progressThreshold) {
        if (timeoutTicks < 1) {
            throw new IllegalArgumentException("timeoutTicks must be positive");
        }
        if (!Double.isFinite(progressThreshold) || progressThreshold <= 0.0) {
            throw new IllegalArgumentException("progressThreshold must be finite and positive");
        }
        this.timeoutTicks = timeoutTicks;
        this.progressThresholdSquared = progressThreshold * progressThreshold;
    }

    void reset(Vec3d currentPosition) {
        lastPosition = Objects.requireNonNull(currentPosition, "currentPosition");
        ticksWithoutProgress = 0;
    }

    boolean sample(Vec3d currentPosition, int elapsedTicks) {
        Objects.requireNonNull(currentPosition, "currentPosition");
        if (elapsedTicks < 1) {
            throw new IllegalArgumentException("elapsedTicks must be positive");
        }
        if (lastPosition == null) {
            reset(currentPosition);
            return false;
        }

        if (currentPosition.squaredDistanceTo(lastPosition) < progressThresholdSquared) {
            ticksWithoutProgress += elapsedTicks;
        } else {
            ticksWithoutProgress = 0;
        }
        lastPosition = currentPosition;
        if (ticksWithoutProgress < timeoutTicks) {
            return false;
        }
        ticksWithoutProgress = 0;
        return true;
    }
}
