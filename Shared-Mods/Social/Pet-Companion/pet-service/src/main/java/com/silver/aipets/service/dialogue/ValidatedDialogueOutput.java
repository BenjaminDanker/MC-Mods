package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetDialogueAttention;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record ValidatedDialogueOutput(
        String reply,
        DialogueImportance importance,
        Optional<String> memoryCandidate,
        Map<TraitName, Integer> proposedTraitDeltas,
        Map<MoodDimension, Integer> proposedMoodDeltas,
        PetDialogueAction action,
        PetDialogueAttention attention,
        DialogueAffect affectDelta,
        RelationshipSignal relationshipSignal,
        int affectStrength,
        DialogueSelfModelCandidate selfModelCandidate,
        OwnerInterpretationCandidate ownerInterpretation,
        Optional<OwnerMemoryProposal> ownerMemory) {
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal, int affectStrength,
            DialogueSelfModelCandidate selfModelCandidate,
            OwnerInterpretationCandidate ownerInterpretation) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas, action, attention,
                affectDelta, relationshipSignal, affectStrength, selfModelCandidate,
                ownerInterpretation, Optional.empty());
    }
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal, int affectStrength,
                DialogueSelfModelCandidate selfModelCandidate) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas,
                action, attention, affectDelta, relationshipSignal, affectStrength,
                selfModelCandidate, OwnerInterpretationCandidate.none());
    }
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas,
                PetDialogueAction.NONE, PetDialogueAttention.QUIET, DialogueAffect.NONE,
                RelationshipSignal.none(), 0, DialogueSelfModelCandidate.none());
    }
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas, PetDialogueAction action,
            PetDialogueAttention attention) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas,
                action, attention, DialogueAffect.NONE, RelationshipSignal.none(), 0, DialogueSelfModelCandidate.none());
    }
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas,
                action, attention, affectDelta, RelationshipSignal.none(), 1, DialogueSelfModelCandidate.none());
    }
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas,
                action, attention, affectDelta, relationshipSignal, 1, DialogueSelfModelCandidate.none());
    }
    public ValidatedDialogueOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> proposedTraitDeltas,
            Map<MoodDimension, Integer> proposedMoodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal, int affectStrength) {
        this(reply, importance, memoryCandidate, proposedTraitDeltas, proposedMoodDeltas,
                action, attention, affectDelta, relationshipSignal, affectStrength,
                DialogueSelfModelCandidate.none());
    }
    public ValidatedDialogueOutput {
        Objects.requireNonNull(reply, "reply");
        Objects.requireNonNull(importance, "importance");
        Objects.requireNonNull(memoryCandidate, "memoryCandidate");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(attention, "attention");
        Objects.requireNonNull(affectDelta, "affectDelta");
        Objects.requireNonNull(relationshipSignal, "relationshipSignal");
        Objects.requireNonNull(selfModelCandidate, "selfModelCandidate");
        Objects.requireNonNull(ownerInterpretation, "ownerInterpretation");
        if (affectStrength < 0 || affectStrength > 2) {
            throw new IllegalArgumentException("affectStrength must be within [0,2]");
        }
        proposedTraitDeltas = Map.copyOf(proposedTraitDeltas);
        proposedMoodDeltas = Map.copyOf(proposedMoodDeltas);
    }
}
