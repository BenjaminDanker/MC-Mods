package com.silver.aipets.service.recall;

import com.silver.aipets.common.authority.PetTransitions;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RecallPersistenceRequest(
        UUID operationId,
        UUID petId,
        String requestFingerprint,
        String periodKey,
        Instant nextAvailableAt,
        PetTransitions.Recall command) {
    public RecallPersistenceRequest {
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(periodKey, "periodKey");
        Objects.requireNonNull(nextAvailableAt, "nextAvailableAt");
        Objects.requireNonNull(command, "command");
        if (!periodKey.matches("[0-9]{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("periodKey must be UTC YYYY-MM");
        }
    }
}
