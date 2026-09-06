package com.silver.aipets.common.domain;

import java.time.Instant;
import java.util.Objects;

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

    private static void requirePercentage(int value, String field) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(field + " must be in 0..100");
        }
    }
}
