package com.silver.aipets.service.dialogue;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable evidence tied to the interaction and source dialogue event that produced it. */
public record RelationshipEvidence(
        UUID interactionId,
        UUID eventId,
        RelationshipSignal signal,
        Instant occurredAt) {
    public RelationshipEvidence {
        Objects.requireNonNull(interactionId, "interactionId");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(signal, "signal");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (!signal.meaningful()) throw new IllegalArgumentException("NONE is not stored as evidence");
    }
}
