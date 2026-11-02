package com.silver.aipets.service.retention;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Test/development representation; summary and source identity survive raw-text redaction. */
public record RetentionEvent(
        UUID eventId, UUID petId, Instant occurredAt, Optional<Instant> shortTermExpiresAt,
        boolean promptEligible, String summary,
        Optional<String> rawPlayerText, Optional<String> rawPetReply) {
    public RetentionEvent {
        Objects.requireNonNull(eventId, "eventId"); Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(shortTermExpiresAt, "shortTermExpiresAt");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(rawPlayerText, "rawPlayerText");
        Objects.requireNonNull(rawPetReply, "rawPetReply");
        if (summary.isBlank()) throw new IllegalArgumentException("summary cannot be blank");
    }
}
