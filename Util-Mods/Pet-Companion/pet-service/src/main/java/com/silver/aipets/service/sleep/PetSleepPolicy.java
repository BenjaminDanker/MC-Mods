package com.silver.aipets.service.sleep;

import java.time.Duration;
import java.util.Objects;

/** Fixed real-time sleep rules from the product specification. */
public record PetSleepPolicy(Duration logoutDelay, Duration maximumAwake, Duration sleepDuration) {
    public PetSleepPolicy {
        requirePositive(logoutDelay, "logoutDelay");
        requirePositive(maximumAwake, "maximumAwake");
        requirePositive(sleepDuration, "sleepDuration");
    }

    public static PetSleepPolicy defaults() {
        return new PetSleepPolicy(Duration.ofMinutes(30), Duration.ofHours(23), Duration.ofHours(1));
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
