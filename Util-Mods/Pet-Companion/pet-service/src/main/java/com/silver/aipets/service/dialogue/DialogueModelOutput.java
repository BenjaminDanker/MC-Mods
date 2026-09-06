package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record DialogueModelOutput(
        String reply,
        DialogueImportance importance,
        Optional<String> memoryCandidate,
        Map<TraitName, Integer> traitDeltas,
        Map<MoodDimension, Integer> moodDeltas) {
    public DialogueModelOutput {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(importance, "importance");
        Objects.requireNonNull(memoryCandidate, "memoryCandidate");
        traitDeltas = Map.copyOf(Objects.requireNonNull(traitDeltas, "traitDeltas"));
        moodDeltas = Map.copyOf(Objects.requireNonNull(moodDeltas, "moodDeltas"));
        if (!traitDeltas.keySet().equals(java.util.EnumSet.allOf(TraitName.class))
                || !moodDeltas.keySet().equals(java.util.EnumSet.allOf(MoodDimension.class))) {
            throw new IllegalArgumentException("Every structured delta field is required exactly once");
        }
        if (traitDeltas.values().stream().anyMatch(delta -> delta < -100 || delta > 100)
                || moodDeltas.values().stream().anyMatch(delta -> delta < -100 || delta > 100)) {
            throw new IllegalArgumentException("Proposed deltas must be within [-100,100]");
        }
    }
}
