package com.silver.aipets.service.dialogue;

import java.util.Objects;
import java.util.Optional;

/** Compact temporary model interpretation; it is never a durable owner fact. */
public record OwnerInterpretationCandidate(
        Optional<String> intent, Optional<String> emotion, double confidence) {
    public OwnerInterpretationCandidate {
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(emotion, "emotion");
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence outside [0,1]");
        }
    }

    public static OwnerInterpretationCandidate none() {
        return new OwnerInterpretationCandidate(Optional.empty(), Optional.empty(), 0);
    }

    public boolean meaningful() {
        return confidence > 0 && (intent.isPresent() || emotion.isPresent());
    }
}
