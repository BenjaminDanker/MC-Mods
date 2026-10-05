package com.silver.aipets.common.domain;

import java.time.Instant;
import java.util.Objects;
import java.time.Duration;

/** Transient mood values, kept separate from the persistent temperament. */
public record PetMood(
        int content,
        int excited,
        int anxious,
        int tired,
        Instant lastDecayAt,
        Instant updatedAt) {
    public PetMood {
        requirePercentage(content, "content");
        requirePercentage(excited, "excited");
        requirePercentage(anxious, "anxious");
        requirePercentage(tired, "tired");
        Objects.requireNonNull(lastDecayAt, "lastDecayAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(lastDecayAt)) {
            throw new IllegalArgumentException("Mood update cannot precede its last decay");
        }
    }

    public static PetMood initial(int content, int excited, int anxious, int tired, Instant createdAt) {
        return new PetMood(content, excited, anxious, tired, createdAt, createdAt);
    }

    public int value(MoodDimension dimension) {
        return switch (Objects.requireNonNull(dimension, "dimension")) {
            case CONTENT -> content;
            case EXCITED -> excited;
            case ANXIOUS -> anxious;
            case TIRED -> tired;
        };
    }

    /** Move temporary mood toward a species/temperament baseline every half hour.
     * Whole intervals advance lastDecayAt, retaining partial elapsed time. */
    public PetMood decay(PetSpecies species, PetTraits traits, Instant now) {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(now, "now");
        if (!now.isAfter(lastDecayAt)) return this;
        long intervals = Math.min(10_000, Duration.between(lastDecayAt, now).toMinutes() / 30);
        if (intervals == 0) return this;
        int contentBase = clamp((species == PetSpecies.DOG ? 55 : 50)
                + (traits.trust() - 50) / 10);
        int excitedBase = clamp((species == PetSpecies.DOG ? 18 : 10)
                + (traits.playfulness() - 50) / 10);
        int anxiousBase = clamp((species == PetSpecies.DOG ? 10 : 14)
                + (50 - traits.security()) / 10);
        int tiredBase = species == PetSpecies.DOG ? 18 : 25;
        return new PetMood(toward(content, contentBase, intervals),
                toward(excited, excitedBase, intervals),
                toward(anxious, anxiousBase, intervals),
                toward(tired, tiredBase, intervals),
                lastDecayAt.plus(Duration.ofMinutes(intervals * 30)), now);
    }

    private static int toward(int value, int target, long intervals) {
        long change = Math.min(Math.abs(value - target), intervals * 2);
        return (int) (value + Long.signum(target - value) * change);
    }

    private static int clamp(int value) { return Math.max(0, Math.min(100, value)); }

    private static void requirePercentage(int value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " must be in 0..100");
        }
    }
}
