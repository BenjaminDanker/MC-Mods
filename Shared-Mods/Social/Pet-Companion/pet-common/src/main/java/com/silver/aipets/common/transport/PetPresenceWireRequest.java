package com.silver.aipets.common.transport;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Whole-network presence event emitted only by the trusted Velocity proxy. */
public record PetPresenceWireRequest(
        UUID ownerUuid,
        boolean online,
        Optional<UUID> absenceSessionId,
        Instant occurredAt) {
    public PetPresenceWireRequest {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(absenceSessionId, "absenceSessionId");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (online == absenceSessionId.isPresent()) {
            throw new IllegalArgumentException(
                    "online events omit absenceSessionId and offline events require it");
        }
    }
}
