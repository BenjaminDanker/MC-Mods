package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.Pet;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DialogueContext(
        Pet pet,
        boolean sleeping,
        boolean aiAccessEnabled,
        List<ShortTermMemorySnippet> shortTermMemories,
        List<LongTermMemorySnippet> longTermMemories,
        List<DialogueTurn> recentTurns,
        DialogueGameContext gameContext,
        Instant now,
        List<RelationshipEvidence> relationshipEvidence,
        OrganicPetContext organicContext) {
    public DialogueContext(Pet pet, boolean sleeping, boolean aiAccessEnabled,
            List<ShortTermMemorySnippet> shortTermMemories,
            List<LongTermMemorySnippet> longTermMemories, List<DialogueTurn> recentTurns,
            DialogueGameContext gameContext, Instant now) {
        this(pet, sleeping, aiAccessEnabled, shortTermMemories, longTermMemories,
                recentTurns, gameContext, now, List.of(), OrganicPetContext.empty(now));
    }
    public DialogueContext(Pet pet, boolean sleeping, boolean aiAccessEnabled,
            List<ShortTermMemorySnippet> shortTermMemories,
            List<LongTermMemorySnippet> longTermMemories, List<DialogueTurn> recentTurns,
            DialogueGameContext gameContext, Instant now, List<RelationshipEvidence> relationshipEvidence) {
        this(pet, sleeping, aiAccessEnabled, shortTermMemories, longTermMemories,
                recentTurns, gameContext, now, relationshipEvidence, OrganicPetContext.empty(now));
    }
    public DialogueContext {
        Objects.requireNonNull(pet, "pet");
        shortTermMemories = List.copyOf(Objects.requireNonNull(shortTermMemories, "shortTermMemories"));
        longTermMemories = List.copyOf(Objects.requireNonNull(longTermMemories, "longTermMemories"));
        recentTurns = List.copyOf(Objects.requireNonNull(recentTurns, "recentTurns"));
        relationshipEvidence = List.copyOf(Objects.requireNonNull(relationshipEvidence, "relationshipEvidence"));
        Objects.requireNonNull(organicContext, "organicContext");
        Objects.requireNonNull(gameContext, "gameContext");
        Objects.requireNonNull(now, "now");
        if (longTermMemories.stream().anyMatch(memory ->
                !memory.card().petId().equals(pet.petId()))) {
            throw new IllegalArgumentException("Long-term memory belongs to another pet");
        }
    }
}
