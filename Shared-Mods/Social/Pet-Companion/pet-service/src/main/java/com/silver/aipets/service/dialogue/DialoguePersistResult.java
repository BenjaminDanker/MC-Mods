package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.PetMood;
import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.common.domain.TraitName;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record DialoguePersistResult(
        PetTraits traits,
        PetMood mood,
        Map<TraitName, Integer> appliedTraitDeltas,
        Map<MoodDimension, Integer> appliedMoodDeltas,
        Optional<Instant> shortTermExpiresAt) {
    public DialoguePersistResult {
        Objects.requireNonNull(traits, "traits");
        Objects.requireNonNull(mood, "mood");
        appliedTraitDeltas = Map.copyOf(appliedTraitDeltas);
        appliedMoodDeltas = Map.copyOf(appliedMoodDeltas);
        Objects.requireNonNull(shortTermExpiresAt, "shortTermExpiresAt");
    }
}
