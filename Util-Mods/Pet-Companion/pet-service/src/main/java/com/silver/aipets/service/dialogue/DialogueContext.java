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
        Instant now) {
    public DialogueContext {
        Objects.requireNonNull(pet, "pet");
        shortTermMemories = List.copyOf(Objects.requireNonNull(shortTermMemories, "shortTermMemories"));
        longTermMemories = List.copyOf(Objects.requireNonNull(longTermMemories, "longTermMemories"));
        recentTurns = List.copyOf(Objects.requireNonNull(recentTurns, "recentTurns"));
        Objects.requireNonNull(gameContext, "gameContext");
        Objects.requireNonNull(now, "now");
        if (longTermMemories.stream().anyMatch(memory ->
                !memory.card().petId().equals(pet.petId()))) {
            throw new IllegalArgumentException("Long-term memory belongs to another pet");
        }
    }
}
