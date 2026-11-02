package com.silver.aipets.service.recall;

import com.silver.aipets.common.authority.PetTransitions;
import java.util.Objects;
import java.util.UUID;

public record RecallCompensationRequest(
        UUID recallOperationId,
        UUID petId,
        String requestFingerprint,
        PetTransitions.CompensateRecallFailure command) {
    public RecallCompensationRequest {
        Objects.requireNonNull(recallOperationId, "recallOperationId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(command, "command");
    }
}
