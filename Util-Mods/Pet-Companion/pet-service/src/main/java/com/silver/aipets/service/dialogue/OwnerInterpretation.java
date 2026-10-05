package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.Optional;

/** Short-lived, explicitly uncertain reading of a message, never a durable owner fact. */
public record OwnerInterpretation(Optional<String> intent, Optional<String> emotion,
        double confidence, Instant updatedAt) {
    public OwnerInterpretation { intent=java.util.Objects.requireNonNull(intent);emotion=java.util.Objects.requireNonNull(emotion);java.util.Objects.requireNonNull(updatedAt);if(confidence<0||confidence>1)throw new IllegalArgumentException("confidence outside [0,1]"); }
    public boolean activeAt(Instant now) { return activeAt(now, 0); }
    public boolean activeAt(Instant now, long laterAcceptedInteractions) {
        return laterAcceptedInteractions < 3
                && now.isBefore(updatedAt.plus(java.time.Duration.ofHours(24)));
    }
}
