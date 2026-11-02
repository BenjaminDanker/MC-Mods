package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.PetMood;

import java.time.Instant;

/** Configurable deterministic initial mood; it never affects temperament values. */
public record InitialMood(int content, int excited, int anxious, int tired) {
    public InitialMood {
        requirePercentage(content, "content");
        requirePercentage(excited, "excited");
        requirePercentage(anxious, "anxious");
        requirePercentage(tired, "tired");
    }

    public static InitialMood defaults() {
        return new InitialMood(50, 20, 10, 10);
    }

    public PetMood at(Instant instant) {
        return PetMood.initial(content, excited, anxious, tired, instant);
    }

    private static void requirePercentage(int value, String name) {
        if (value < 0 || value > 100) {
            throw new IllegalArgumentException(name + " must be in 0..100");
        }
    }
}
