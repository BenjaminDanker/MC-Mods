package com.silver.aipets.service.dialogue;

import java.util.Objects;

/** A small, event-specific relationship cue. It is evidence, never a cumulative score. */
public record RelationshipSignal(Type type, int strength) {
    public RelationshipSignal {
        Objects.requireNonNull(type, "type");
        if ((type == Type.NONE && strength != 0)
                || (type != Type.NONE && (strength < 1 || strength > 2))) {
            throw new IllegalArgumentException("Relationship signal strength does not match its type");
        }
    }

    public static RelationshipSignal none() { return new RelationshipSignal(Type.NONE, 0); }
    public boolean meaningful() { return type != Type.NONE; }

    public enum Type {
        NONE, WARMTH, TRUST, BOUNDARY, REPAIR, IRRITATION, FEAR, DISTANCE, CURIOSITY
    }
}
