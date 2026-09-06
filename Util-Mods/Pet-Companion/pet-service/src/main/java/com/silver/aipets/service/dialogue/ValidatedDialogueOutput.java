package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ValidatedDialogueOutput(
        String reply,
        DialogueImportance importance,
        Optional<String> memoryCandidate,
        Map<TraitName, Integer> proposedTraitDeltas,
        Map<MoodDimension, Integer> proposedMoodDeltas) {
    public ValidatedDialogueOutput {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(importance, "importance");
        Objects.requireNonNull(memoryCandidate, "memoryCandidate");
        proposedTraitDeltas = Map.copyOf(proposedTraitDeltas);
        proposedMoodDeltas = Map.copyOf(proposedMoodDeltas);
    }
}
