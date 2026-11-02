package com.silver.aipets.service.retention;

import java.time.Duration;
import java.util.Objects;

public record RetentionPolicy(
        Duration rawTextRetention,
        int maximumRowsPerRun,
        int maximumAttempts,
        Duration leaseDuration,
        Duration retryDelay,
        Duration scheduleInterval) {
    public RetentionPolicy {
        positive(rawTextRetention, "rawTextRetention");
        positive(leaseDuration, "leaseDuration");
        positive(retryDelay, "retryDelay");
        positive(scheduleInterval, "scheduleInterval");
        if (maximumRowsPerRun < 1 || maximumRowsPerRun > 10_000
                || maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalArgumentException("Retention bounds are outside safe limits");
        }
    }

    public static RetentionPolicy defaults() {
        return new RetentionPolicy(
                Duration.ofDays(7), 1_000, 3,
                Duration.ofMinutes(2), Duration.ofMinutes(5), Duration.ofHours(1));
    }

    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException(name + " must be positive");
    }
}
