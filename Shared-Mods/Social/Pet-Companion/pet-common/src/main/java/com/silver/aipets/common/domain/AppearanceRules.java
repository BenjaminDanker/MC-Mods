package com.silver.aipets.common.domain;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Injected species-specific appearance bounds; deployments may replace the defaults. */
public record AppearanceRules(Map<PetSpecies, ScaleRange> scaleRanges) {
    public AppearanceRules {
        Objects.requireNonNull(scaleRanges, "scaleRanges");
        EnumMap<PetSpecies, ScaleRange> copy = new EnumMap<>(PetSpecies.class);
        copy.putAll(scaleRanges);
        for (PetSpecies species : PetSpecies.values()) {
            if (!copy.containsKey(species) || copy.get(species) == null) {
                throw new IllegalArgumentException("Missing scale range for " + species);
            }
        }
        if (copy.size() != PetSpecies.values().length) {
            throw new IllegalArgumentException("Scale rules contain an unknown species");
        }
        scaleRanges = Collections.unmodifiableMap(copy);
    }

    public static AppearanceRules defaults() {
        return new AppearanceRules(Map.of(
                PetSpecies.CAT, new ScaleRange(0.55, 0.80),
                PetSpecies.DOG, new ScaleRange(0.50, 0.78)));
    }

    public ScaleRange scaleRange(PetSpecies species) {
        return scaleRanges.get(Objects.requireNonNull(species, "species"));
    }

    public void validate(PetSpecies species, double scale) {
        ScaleRange range = scaleRange(species);
        if (!range.contains(scale)) {
            throw new IllegalArgumentException(
                    "Scale " + scale + " is outside " + species + " bounds "
                            + range.minimum() + ".." + range.maximum());
        }
    }
}
