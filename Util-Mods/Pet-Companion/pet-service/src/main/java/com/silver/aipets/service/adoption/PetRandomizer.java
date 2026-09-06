package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.AppearanceRules;
import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.ResourceId;
import com.silver.aipets.common.domain.ScaleRange;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.random.RandomGenerator;

/** Adoption-only appearance and temperament generation with injected randomness. */
public final class PetRandomizer {
    public static final int TEMPERAMENT_MINIMUM = 35;
    public static final int TEMPERAMENT_MAXIMUM = 65;

    private final AppearanceRules appearanceRules;
    private final AppearanceCatalog appearanceCatalog;
    private final InitialMood initialMood;
    private final RandomGenerator random;

    public PetRandomizer(
            AppearanceRules appearanceRules,
            AppearanceCatalog appearanceCatalog,
            InitialMood initialMood,
            RandomGenerator random) {
        this.appearanceRules = Objects.requireNonNull(appearanceRules, "appearanceRules");
        this.appearanceCatalog = Objects.requireNonNull(appearanceCatalog, "appearanceCatalog");
        this.initialMood = Objects.requireNonNull(initialMood, "initialMood");
        this.random = Objects.requireNonNull(random, "random");
    }

    public AdoptionProfile generate(PetSpecies species, Instant adoptedAt) {
        Objects.requireNonNull(species, "species");
        Objects.requireNonNull(adoptedAt, "adoptedAt");

        List<ResourceId> variants = appearanceCatalog.variantsFor(species);
        ResourceId variant = variants.get(random.nextInt(variants.size()));
        ScaleRange range = appearanceRules.scaleRange(species);
        double centeredUnitValue = (random.nextDouble() + random.nextDouble()) / 2.0;
        double scale = range.minimum() + centeredUnitValue * (range.maximum() - range.minimum());

        PetAppearance appearance = PetAppearance.create(
                species,
                variant,
                scale,
                OptionalLong.of(random.nextLong()),
                appearanceRules);
        PetTraits traits = PetTraits.initial(
                centeredTemperament(),
                centeredTemperament(),
                centeredTemperament(),
                centeredTemperament(),
                centeredTemperament(),
                adoptedAt);
        return new AdoptionProfile(appearance, traits, initialMood.at(adoptedAt));
    }

    private int centeredTemperament() {
        int width = TEMPERAMENT_MAXIMUM - TEMPERAMENT_MINIMUM + 1;
        int first = random.nextInt(width);
        int second = random.nextInt(width);
        return TEMPERAMENT_MINIMUM + (first + second) / 2;
    }
}
