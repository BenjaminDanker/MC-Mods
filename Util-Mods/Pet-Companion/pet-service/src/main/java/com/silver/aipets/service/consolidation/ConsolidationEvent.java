package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueImportance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConsolidationEvent(
        UUID eventId,
        UUID petId,
        DialogueImportance importance,
        String summary,
        String eventType,
        Instant occurredAt) {
    public ConsolidationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(importance, "importance");
        summary = bounded(summary, 2_000, "summary");
        eventType = bounded(eventType, 64, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static String bounded(String value, int maximum, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.strip().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return normalized;
    }
}
