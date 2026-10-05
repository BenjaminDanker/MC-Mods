package com.silver.aipets.service.dialogue;

import java.util.Locale;
import java.util.Objects;

/** Tentative preference evidence. It cannot directly modify a stable self-model cue. */
public record DialogueSelfModelCandidate(Kind kind, String value, double confidence,
        java.util.Set<java.util.UUID> supportingEventIds) {
    public enum Kind { NONE, PREFERENCE, INTEREST, AVOIDANCE, GOAL }
    public DialogueSelfModelCandidate(Kind kind, String value, double confidence) {
        this(kind, value, confidence, java.util.Set.of());
    }
    public DialogueSelfModelCandidate {
        Objects.requireNonNull(kind);
        supportingEventIds = java.util.Set.copyOf(Objects.requireNonNull(supportingEventIds));
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1)
            throw new IllegalArgumentException("confidence must be in [0,1]");
        value = value == null ? "" : value.strip();
        if (value.length() > 100) throw new IllegalArgumentException("candidate value exceeds 100 characters");
        if (kind == Kind.NONE && !value.isEmpty()) throw new IllegalArgumentException("NONE candidate must be empty");
        if (kind != Kind.NONE && value.isEmpty()) throw new IllegalArgumentException("candidate value is required");
    }
    public static DialogueSelfModelCandidate none() { return new DialogueSelfModelCandidate(Kind.NONE, "", 0, java.util.Set.of()); }
    public boolean meaningful() { return kind != Kind.NONE && confidence >= .4; }
    public String category() { return switch (kind) {
        case PREFERENCE -> "likes"; case INTEREST -> "interests";
        case AVOIDANCE -> "avoidances"; case GOAL -> "goals"; case NONE -> "";
    }; }
    public static Kind parseKind(String text) { return Kind.valueOf(text.toUpperCase(Locale.ROOT)); }
}
