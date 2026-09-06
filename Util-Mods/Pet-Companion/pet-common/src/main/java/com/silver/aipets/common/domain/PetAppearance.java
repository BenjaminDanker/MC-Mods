package com.silver.aipets.common.domain;

import java.util.Objects;
import java.util.OptionalLong;

/** Exact adoption-time appearance. Transitions retain this value unchanged. */
public final class PetAppearance {
    private final PetSpecies species;
    private final ResourceId variantId;
    private final double scale;
    private final OptionalLong appearanceSeed;

    private PetAppearance(
            PetSpecies species,
            ResourceId variantId,
            double scale,
            OptionalLong appearanceSeed,
            AppearanceRules rules) {
        this.species = Objects.requireNonNull(species, "species");
        this.variantId = Objects.requireNonNull(variantId, "variantId");
        this.appearanceSeed = Objects.requireNonNull(appearanceSeed, "appearanceSeed");
        Objects.requireNonNull(rules, "rules").validate(species, scale);
        this.scale = scale;
    }

    public static PetAppearance create(
            PetSpecies species,
            ResourceId variantId,
            double scale,
            OptionalLong appearanceSeed,
            AppearanceRules rules) {
        return new PetAppearance(species, variantId, scale, appearanceSeed, rules);
    }

    public static PetAppearance create(
            PetSpecies species,
            String variantId,
            double scale,
            Long appearanceSeed,
            AppearanceRules rules) {
        OptionalLong seed = appearanceSeed == null
                ? OptionalLong.empty()
                : OptionalLong.of(appearanceSeed);
        return create(species, ResourceId.parse(variantId), scale, seed, rules);
    }

    /** Creates a deterministic compatibility repair without changing type, scale, or seed. */
    public PetAppearance withVariant(ResourceId replacementVariantId, AppearanceRules rules) {
        return create(species, replacementVariantId, scale, appearanceSeed, rules);
    }

    public PetSpecies species() {
        return species;
    }

    public ResourceId entityTypeId() {
        return species.entityTypeId();
    }

    public ResourceId variantId() {
        return variantId;
    }

    public double scale() {
        return scale;
    }

    public OptionalLong appearanceSeed() {
        return appearanceSeed;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PetAppearance that)) {
            return false;
        }
        return species == that.species
                && variantId.equals(that.variantId)
                && Double.compare(scale, that.scale) == 0
                && appearanceSeed.equals(that.appearanceSeed);
    }

    @Override
    public int hashCode() {
        return Objects.hash(species, variantId, scale, appearanceSeed);
    }

    @Override
    public String toString() {
        return "PetAppearance[species=" + species
                + ", variantId=" + variantId
                + ", scale=" + scale
                + ", appearanceSeed=" + appearanceSeed + ']';
    }
}
