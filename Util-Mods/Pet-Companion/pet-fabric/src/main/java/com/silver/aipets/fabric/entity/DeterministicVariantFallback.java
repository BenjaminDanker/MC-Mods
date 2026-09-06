package com.silver.aipets.fabric.entity;

import com.silver.aipets.common.domain.ResourceId;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Stable compatibility fallback used only when a persisted registry ID no longer exists. */
public final class DeterministicVariantFallback {
    private DeterministicVariantFallback() {
    }

    public static ResourceId select(List<ResourceId> available, long stableSeed) {
        Objects.requireNonNull(available, "available");
        List<ResourceId> sorted = available.stream()
                .map(value -> Objects.requireNonNull(value, "available variant"))
                .distinct()
                .sorted(Comparator.comparing(ResourceId::value))
                .toList();
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("No variants are available for compatibility repair");
        }
        return sorted.get(Math.floorMod(Long.hashCode(stableSeed), sorted.size()));
    }
}
