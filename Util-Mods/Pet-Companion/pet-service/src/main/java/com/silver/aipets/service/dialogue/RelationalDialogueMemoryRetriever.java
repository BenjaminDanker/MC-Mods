package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.LongTermMemoryStore;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Deterministic fallback that keeps dialogue available when vector search is unavailable. */
public final class RelationalDialogueMemoryRetriever implements DialogueMemoryRetriever {
    private final LongTermMemoryStore memories;

    public RelationalDialogueMemoryRetriever(LongTermMemoryStore memories) {
        this.memories = Objects.requireNonNull(memories, "memories");
    }

    @Override
    public List<LongTermMemorySnippet> retrieve(
            UUID petId, String playerMessage, String backend, String dimension, int limit) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(playerMessage, "playerMessage");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(dimension, "dimension");
        if (limit < 1 || limit > 3) {
            throw new IllegalArgumentException("limit must be between 1 and 3");
        }
        return memories.listActiveForPet(petId, limit).cards().stream()
                .map(card -> new LongTermMemorySnippet(card, 0.0D))
                .toList();
    }
}
