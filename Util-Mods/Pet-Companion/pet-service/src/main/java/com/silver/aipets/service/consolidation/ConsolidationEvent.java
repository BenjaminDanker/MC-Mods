package com.silver.aipets.service.consolidation;

import com.silver.aipets.service.dialogue.DialogueImportance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import com.silver.aipets.service.memory.MemoryProvenance;

public record ConsolidationEvent(
        UUID eventId,
        UUID petId,
        DialogueImportance importance,
        String summary,
        String eventType,
        Instant occurredAt,
        MemoryProvenance provenance,
        String sourceServer,
        String sourceDimension,
        java.util.Optional<com.silver.aipets.common.transport.SpatialLocation> location) {
    public ConsolidationEvent(UUID eventId, UUID petId, DialogueImportance importance,
            String summary, String eventType, Instant occurredAt, MemoryProvenance provenance,
            String sourceServer, String sourceDimension) {
        this(eventId, petId, importance, summary, eventType, occurredAt, provenance,
                sourceServer, sourceDimension, java.util.Optional.empty());
    }
    public ConsolidationEvent(UUID eventId, UUID petId, DialogueImportance importance,
            String summary, String eventType, Instant occurredAt,
            MemoryProvenance provenance) {
        this(eventId, petId, importance, summary, eventType, occurredAt,
                provenance, "", "");
    }
    public ConsolidationEvent(UUID eventId, UUID petId, DialogueImportance importance,
            String summary, String eventType, Instant occurredAt) {
        this(eventId, petId, importance, summary, eventType, occurredAt,
                MemoryProvenance.INFERENCE, "", "");
    }
    public ConsolidationEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(importance, "importance");
        summary = bounded(summary, 2_000, "summary");
        eventType = bounded(eventType, 64, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(location, "location");
        sourceServer = optionalLocation(sourceServer, 128);
        sourceDimension = optionalLocation(sourceDimension, 191);
    }

    private static String optionalLocation(String value, int maximum) {
        Objects.requireNonNull(value, "location");
        if (value.length() > maximum || !value.matches("[a-zA-Z0-9_:./-]*")) {
            throw new IllegalArgumentException("Invalid event location");
        }
        return value;
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
