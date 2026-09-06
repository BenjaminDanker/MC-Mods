package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.PetAppearance;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetTraits;

import java.util.Objects;

/** Values generated exactly once and committed with a new pet. */
public record AdoptionProfile(PetAppearance appearance, PetTraits traits, PetMood mood) {
    public AdoptionProfile {
        Objects.requireNonNull(appearance, "appearance");
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(mood, "mood");
    }
}
