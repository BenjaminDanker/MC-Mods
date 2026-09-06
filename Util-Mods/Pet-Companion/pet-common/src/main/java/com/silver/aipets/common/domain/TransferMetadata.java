package com.silver.aipets.common.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Complete one-shot reservation for a supported cross-backend carry. */
public record TransferMetadata(
        UUID transferId,
        BackendId sourceBackendId,
        UUID sourceEntityUuid,
        BackendId destinationBackendId,
        Instant startedAt,
        Instant expiresAt) {
    public TransferMetadata {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(sourceBackendId, "sourceBackendId");
        Objects.requireNonNull(sourceEntityUuid, "sourceEntityUuid");
        Objects.requireNonNull(destinationBackendId, "destinationBackendId");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("Transfer expiry must be after its start");
        }
    }

    /** Expiry is inclusive: a transfer cannot be completed at or after {@code expiresAt}. */
    public boolean isExpiredAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return !instant.isBefore(expiresAt);
    }
}
