package com.silver.aipets.service.dialogue;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Objects;

public record DialogueLimits(
        int maximumMessageCharacters,
        int maximumReplyCharacters,
        int hardInputTokens,
        int relationshipTokens,
        int shortTermTokens,
        int longTermTokens,
        int recentTurnTokens,
        int maximumRecentTurns,
        Duration cooldown,
        int dailySuccessfulReplyCap,
        int globalConcurrency,
        Duration modelTimeout,
        BigDecimal globalDailyCostCap,
        BigDecimal globalMonthlyCostCap,
        int circuitFailureThreshold,
        Duration circuitOpenDuration) {

    public DialogueLimits {
        if (maximumMessageCharacters < 1 || maximumMessageCharacters > 2_000
                || maximumReplyCharacters < 1 || maximumReplyCharacters > 2_000
                || hardInputTokens < 100 || hardInputTokens > 32_000
                || relationshipTokens < 1 || shortTermTokens < 1 || longTermTokens < 1
                || recentTurnTokens < 1
                || relationshipTokens >= hardInputTokens || shortTermTokens >= hardInputTokens
                || longTermTokens >= hardInputTokens || recentTurnTokens >= hardInputTokens
                || maximumRecentTurns < 1 || maximumRecentTurns > 20
                || dailySuccessfulReplyCap < 1
                || globalConcurrency < 1 || globalConcurrency > 1_000
                || circuitFailureThreshold < 1 || circuitFailureThreshold > 100) {
            throw new IllegalArgumentException("Dialogue limits are outside safe bounds");
        }
        requirePositive(cooldown, "cooldown");
        requirePositive(modelTimeout, "modelTimeout");
        requirePositive(circuitOpenDuration, "circuitOpenDuration");
        requirePositive(globalDailyCostCap, "globalDailyCostCap");
        requirePositive(globalMonthlyCostCap, "globalMonthlyCostCap");
        if (globalMonthlyCostCap.compareTo(globalDailyCostCap) < 0) {
            throw new IllegalArgumentException("Monthly cost cap cannot be below daily cap");
        }
    }

    public static DialogueLimits defaults() {
        return defaults(100);
    }

    public static DialogueLimits defaults(int dailySuccessfulReplyCap) {
        return new DialogueLimits(
                500, 300, 4_000, 250, 1_200, 600, 700, 4,
                Duration.ofSeconds(5), dailySuccessfulReplyCap, 8, Duration.ofSeconds(10),
                new BigDecimal("25.00"), new BigDecimal("500.00"),
                5, Duration.ofMinutes(1));
    }

    /** Production profile: the subscription token budget, rather than reply count, is authoritative. */
    public static DialogueLimits defaultsWithoutReplyCap() {
        return defaults(Integer.MAX_VALUE);
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        Objects.requireNonNull(value, name);
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
