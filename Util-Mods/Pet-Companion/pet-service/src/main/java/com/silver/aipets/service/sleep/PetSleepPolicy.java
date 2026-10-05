package com.silver.aipets.service.sleep;

import java.time.Duration;
import java.util.Objects;
import java.util.Map;

/** Deterministic fatigue in points (0-100), with a hard awake deadline. */
public record PetSleepPolicy(Duration logoutDelay, Duration maximumAwake, Duration napDuration,
        Duration longSleepDuration, Duration minimumAwake, int onlineFatiguePerHour,
        int offlineFatiguePerHour, int napThreshold, int longSleepThreshold,
        int napRecovery, int longSleepRecovery) {
    public PetSleepPolicy {
        requirePositive(logoutDelay, "logoutDelay");
        requirePositive(maximumAwake, "maximumAwake");
        requirePositive(napDuration, "napDuration");
        requirePositive(longSleepDuration, "longSleepDuration");
        requirePositive(minimumAwake, "minimumAwake");
        if (minimumAwake.compareTo(maximumAwake) >= 0 || napThreshold < 1
                || longSleepThreshold <= napThreshold || longSleepThreshold > 100
                || onlineFatiguePerHour < 1 || offlineFatiguePerHour < 0
                || napRecovery < 1 || longSleepRecovery < napRecovery) {
            throw new IllegalArgumentException("Invalid fatigue policy");
        }
    }

    public static PetSleepPolicy defaults() {
        return new PetSleepPolicy(Duration.ofMinutes(30), Duration.ofHours(16),
                Duration.ofMinutes(12), Duration.ofMinutes(40), Duration.ofMinutes(75),
                6, 1, 60, 85, 45, 85);
    }

    public static PetSleepPolicy fromEnvironment(Map<String, String> values) {
        PetSleepPolicy d = defaults();
        return new PetSleepPolicy(
                Duration.ofMinutes(number(values, "PET_SLEEP_LOGOUT_DELAY_MINUTES", 30, 1, 1440)),
                Duration.ofMinutes(number(values, "PET_SLEEP_MAX_AWAKE_MINUTES", 960, 120, 1380)),
                Duration.ofMinutes(number(values, "PET_SLEEP_NAP_MINUTES", 12, 5, 120)),
                Duration.ofMinutes(number(values, "PET_SLEEP_LONG_MINUTES", 40, 15, 240)),
                Duration.ofMinutes(number(values, "PET_SLEEP_MIN_AWAKE_MINUTES", 75, 30, 360)),
                number(values, "PET_SLEEP_ONLINE_FATIGUE_PER_HOUR", d.onlineFatiguePerHour(), 1, 30),
                number(values, "PET_SLEEP_OFFLINE_FATIGUE_PER_HOUR", d.offlineFatiguePerHour(), 0, 10),
                number(values, "PET_SLEEP_NAP_THRESHOLD", d.napThreshold(), 20, 90),
                number(values, "PET_SLEEP_LONG_THRESHOLD", d.longSleepThreshold(), 30, 100),
                number(values, "PET_SLEEP_NAP_RECOVERY", d.napRecovery(), 10, 100),
                number(values, "PET_SLEEP_LONG_RECOVERY", d.longSleepRecovery(), 20, 100));
    }

    private static int number(Map<String, String> values, String key, int fallback, int min, int max) {
        int result = Integer.parseInt(values.getOrDefault(key, Integer.toString(fallback)));
        if (result < min || result > max) throw new IllegalArgumentException(key + " out of range");
        return result;
    }

    private static void requirePositive(Duration value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
