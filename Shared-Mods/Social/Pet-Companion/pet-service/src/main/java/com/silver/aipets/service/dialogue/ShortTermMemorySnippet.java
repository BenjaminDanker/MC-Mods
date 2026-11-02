package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ShortTermMemorySnippet(
        UUID eventId,
        DialogueImportance importance,
        String summary,
        Instant occurredAt,
        Optional<Instant> expiresAt,
        double relevance,
        String provenance,
        Optional<com.silver.aipets.common.transport.SpatialLocation> location) {
    public ShortTermMemorySnippet(UUID eventId, DialogueImportance importance, String summary,
            Instant occurredAt, Optional<Instant> expiresAt, double relevance, String provenance) {
        this(eventId, importance, summary, occurredAt, expiresAt, relevance, provenance, Optional.empty());
    }
    public ShortTermMemorySnippet(UUID eventId, DialogueImportance importance, String summary,
            Instant occurredAt, Optional<Instant> expiresAt, double relevance) {
        this(eventId, importance, summary, occurredAt, expiresAt, relevance, "VERIFIED_OBSERVATION");
    }

    public ShortTermMemorySnippet {
        Objects.requireNonNull(location, "location");
        if (!"VERIFIED_OBSERVATION".equals(provenance)) location = Optional.empty();
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(importance, "importance");
        summary = bounded(summary, 2_000, "summary");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        provenance = provenance == null || provenance.isBlank() ? "INFERENCE" : provenance.strip();
        if (!java.util.Set.of("VERIFIED_OBSERVATION", "OWNER_STATEMENT", "INFERENCE", "CONSOLIDATED")
                .contains(provenance)) {
            provenance = "INFERENCE";
        }
        if (!Double.isFinite(relevance) || relevance < 0 || relevance > 1) {
            throw new IllegalArgumentException("relevance must be within [0,1]");
        }
    }

    public boolean eligibleAt(Instant now) {
        return expiresAt.map(expiry -> expiry.isAfter(now)).orElse(true);
    }

    static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return normalized;
    }
}
