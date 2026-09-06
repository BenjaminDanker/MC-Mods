package com.silver.aipets.service.vector;

import java.util.Objects;
import java.util.UUID;

public record VectorMemoryMatch(UUID petId, UUID memoryId, long memoryVersion, double similarity) {
    public VectorMemoryMatch {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        if (memoryVersion < 1 || !Double.isFinite(similarity)
                || similarity < -1.0 || similarity > 1.0) {
            throw new IllegalArgumentException("Invalid vector match");
        }
    }
}
