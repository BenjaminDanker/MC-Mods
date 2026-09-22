package com.silver.aipets.common.transport;

import java.util.Objects;
import java.util.UUID;

/** Minimal durable player notification for an automatically completed adoption. */
public record PendingAdoptionNoticeWire(UUID intentId, String petName) {
    public PendingAdoptionNoticeWire {
        Objects.requireNonNull(intentId, "intentId");
        Objects.requireNonNull(petName, "petName");
        if (petName.isBlank() || petName.length() > 64) {
            throw new IllegalArgumentException("petName is invalid");
        }
    }
}
