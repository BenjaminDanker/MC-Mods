package com.silver.aipets.fabric.reconciliation;

import com.silver.aipets.common.authority.EntityAuthorityDecision;

import java.util.Objects;
import java.util.Optional;

public record PetEntityReconciliationResult(
        PetEntityReconciliationStatus status,
        Optional<EntityAuthorityDecision> authorityDecision) {
    public PetEntityReconciliationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(authorityDecision, "authorityDecision");
    }

    public static PetEntityReconciliationResult of(PetEntityReconciliationStatus status) {
        return new PetEntityReconciliationResult(status, Optional.empty());
    }

    public static PetEntityReconciliationResult decided(
            PetEntityReconciliationStatus status,
            EntityAuthorityDecision decision) {
        return new PetEntityReconciliationResult(status, Optional.of(decision));
    }
}
