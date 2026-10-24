package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.MoodDimension;
import com.silver.aipets.common.domain.TraitName;
import com.silver.aipets.common.transport.PetDialogueAction;
import com.silver.aipets.common.transport.PetDialogueAttention;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record DialogueModelOutput(
        String reply,
        DialogueImportance importance,
        Optional<String> memoryCandidate,
        Map<TraitName, Integer> traitDeltas,
        Map<MoodDimension, Integer> moodDeltas,
        PetDialogueAction action,
        PetDialogueAttention attention,
        DialogueAffect affectDelta,
        RelationshipSignal relationshipSignal,
        int affectStrength,
        DialogueSelfModelCandidate selfModelCandidate,
        OwnerInterpretationCandidate ownerInterpretation,
        Optional<OwnerMemoryProposal> ownerMemory) {
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal, int affectStrength,
            DialogueSelfModelCandidate selfModelCandidate,
            OwnerInterpretationCandidate ownerInterpretation) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas, action, attention,
                affectDelta, relationshipSignal, affectStrength, selfModelCandidate,
                ownerInterpretation, Optional.empty());
    }
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal, int affectStrength,
                DialogueSelfModelCandidate selfModelCandidate) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas, action, attention,
                affectDelta, relationshipSignal, affectStrength, 
                selfModelCandidate, OwnerInterpretationCandidate.none());
    }
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas,
                PetDialogueAction.NONE, PetDialogueAttention.QUIET, DialogueAffect.NONE,
                RelationshipSignal.none(), 0, DialogueSelfModelCandidate.none());
    }
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas, PetDialogueAction action,
            PetDialogueAttention attention) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas,
                action, attention, DialogueAffect.NONE, RelationshipSignal.none(), 0, DialogueSelfModelCandidate.none());
    }
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas,
                action, attention, affectDelta, RelationshipSignal.none(), 1, DialogueSelfModelCandidate.none());
    }
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas, action,
                attention, affectDelta, relationshipSignal, 1, DialogueSelfModelCandidate.none());
    }
    public DialogueModelOutput(String reply, DialogueImportance importance,
            Optional<String> memoryCandidate, Map<TraitName, Integer> traitDeltas,
            Map<MoodDimension, Integer> moodDeltas, PetDialogueAction action,
            PetDialogueAttention attention, DialogueAffect affectDelta,
            RelationshipSignal relationshipSignal, int affectStrength) {
        this(reply, importance, memoryCandidate, traitDeltas, moodDeltas, action,
                attention, affectDelta, relationshipSignal, affectStrength, DialogueSelfModelCandidate.none());
    }
    public DialogueModelOutput {
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
