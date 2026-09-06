package com.silver.aipets.service.consolidation;

import java.time.Duration;
import java.util.Objects;

public record ConsolidationConfig(
        int maximumEvents,
        int maximumInputTokens,
        int maximumCards,
        int maximumCardTokens,
        int maximumRelationshipTokens,
        int maximumAttempts,
        Duration leaseDuration,
        Duration initialRetryDelay,
        Duration modelTimeout) {
    public ConsolidationConfig {
        if (maximumEvents < 1 || maximumEvents > 100
                || maximumInputTokens < 100 || maximumInputTokens > 32_000
                || maximumCards < 1 || maximumCards > 5
                || maximumCardTokens < 1 || maximumCardTokens >= maximumInputTokens
                || maximumRelationshipTokens < 1 || maximumRelationshipTokens >= maximumInputTokens
                || maximumAttempts < 1 || maximumAttempts > 20) {
            throw new IllegalArgumentException("Consolidation limits are outside safe bounds");
        }
        positive(leaseDuration, "leaseDuration");
        positive(initialRetryDelay, "initialRetryDelay");
        positive(modelTimeout, "modelTimeout");
    }

    public static ConsolidationConfig defaults() {
        return new ConsolidationConfig(
                30, 6_000, 5, 100, 250, 3,
                Duration.ofMinutes(2), Duration.ofMinutes(1), Duration.ofSeconds(20));
    }

    private static void positive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
