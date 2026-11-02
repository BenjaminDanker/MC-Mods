package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.LongTermMemoryStore;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.Instant;
import java.util.Comparator;

/** Deterministic fallback that keeps dialogue available when vector search is unavailable. */
public final class RelationalDialogueMemoryRetriever implements DialogueMemoryRetriever {
    private final LongTermMemoryStore memories;

    public RelationalDialogueMemoryRetriever(LongTermMemoryStore memories) {
        this.memories = Objects.requireNonNull(memories, "memories");
    }

    @Override
    public List<LongTermMemorySnippet> retrieve(
            UUID petId, String playerMessage, String backend, String dimension, int limit) {
        return retrieve(petId, playerMessage, backend, dimension, "", limit);
    }

    @Override
    public List<LongTermMemorySnippet> retrieve(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit) {
        return retrieveSpatial(petId, playerMessage, backend, dimension, biome, limit,
                java.util.Optional.empty(), Instant.now(), false);
    }

    @Override
    public List<LongTermMemorySnippet> retrieveSpatial(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit,
            java.util.Optional<com.silver.aipets.common.transport.SpatialLocation> location,
            Instant now, boolean speculative) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(playerMessage, "playerMessage");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(dimension, "dimension");
        if (limit < 1 || limit > 3) {
            throw new IllegalArgumentException("limit must be between 1 and 3");
        }
        return memories.listActiveForPet(petId, 100).cards().stream()
                .filter(card -> MemoryRanking.relevant(playerMessage, card, 0))
                .map(card -> new LongTermMemorySnippet(card,
                        MemoryRanking.lexical(playerMessage, card), LongTermMemorySnippet.Source.RELATIONAL,
                        MemoryRanking.score(playerMessage, card, 0, backend, dimension, biome, now, location)))
                .sorted(Comparator.comparingDouble((LongTermMemorySnippet snippet) ->
                        snippet.rankingScore()).reversed())
                .limit(limit)
                .toList();
    }
}
