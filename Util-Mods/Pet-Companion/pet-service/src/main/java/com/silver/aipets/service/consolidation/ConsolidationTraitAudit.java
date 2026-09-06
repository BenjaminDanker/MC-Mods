package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.TraitName;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ConsolidationTraitAudit(
        UUID changeId,
        UUID petId,
        UUID jobId,
        TraitName trait,
        int oldValue,
        int proposedDelta,
        int appliedDelta,
        int newValue,
        Instant createdAt) {
    public ConsolidationTraitAudit {
        Objects.requireNonNull(changeId, "changeId");
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(jobId, "jobId");
        Objects.requireNonNull(trait, "trait");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
