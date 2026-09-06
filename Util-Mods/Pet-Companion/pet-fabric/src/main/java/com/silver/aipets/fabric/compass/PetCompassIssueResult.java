package com.silver.aipets.fabric.compass;

import java.util.Objects;

public record PetCompassIssueResult(
        PetCompassIssueStatus status,
        String presentation,
        int removedInvalidOrDuplicate) {
    public PetCompassIssueResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(presentation, "presentation");
        if (removedInvalidOrDuplicate < 0) {
            throw new IllegalArgumentException("removedInvalidOrDuplicate must not be negative");
        }
    }
}
