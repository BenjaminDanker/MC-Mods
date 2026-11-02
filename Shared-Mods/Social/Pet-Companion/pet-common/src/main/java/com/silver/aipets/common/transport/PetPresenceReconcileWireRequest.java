package com.silver.aipets.common.transport;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Snapshot of the players currently connected to the trusted Velocity proxy. */
public record PetPresenceReconcileWireRequest(Set<UUID> onlineOwnerUuids, Instant occurredAt) {
    public PetPresenceReconcileWireRequest {
        Objects.requireNonNull(onlineOwnerUuids, "onlineOwnerUuids");
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (onlineOwnerUuids.size() > 10_000 || onlineOwnerUuids.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("online owner snapshot is outside safe bounds");
        }
        onlineOwnerUuids = Set.copyOf(onlineOwnerUuids);
    }
}
