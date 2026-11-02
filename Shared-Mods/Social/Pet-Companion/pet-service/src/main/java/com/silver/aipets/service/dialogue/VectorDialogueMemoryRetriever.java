package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.vector.MemoryRetrievalResult;
import com.silver.aipets.service.vector.MemoryRetrievalService;
import com.silver.aipets.service.vector.MemorySearchQuery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.time.Instant;
import java.util.Comparator;

/** Bounded hybrid recall: authoritative text matches complement vector candidates. */
public final class VectorDialogueMemoryRetriever implements DialogueMemoryRetriever {
    private final MemoryRetrievalService vectors;
    private final DialogueMemoryRetriever fallback;
    private final RuleBasedDialogueSafety localSafety = new RuleBasedDialogueSafety();

    public VectorDialogueMemoryRetriever(
            MemoryRetrievalService vectors, DialogueMemoryRetriever fallback) {
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public List<LongTermMemorySnippet> retrieve(
            UUID petId, String playerMessage, String backend, String dimension, int limit) {
        return retrieve(petId, playerMessage, backend, dimension, "", limit);
    }

    @Override
    public List<LongTermMemorySnippet> retrieve(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit) {
        return retrieve(petId, playerMessage, backend, dimension, biome, limit, false);
    }

    @Override
    public List<LongTermMemorySnippet> retrieveSpeculatively(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit) {
        return retrieve(petId, playerMessage, backend, dimension, biome, limit, true);
    }

    private List<LongTermMemorySnippet> retrieve(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit, boolean speculative) {
        return retrieveSpatial(petId, playerMessage, backend, dimension, biome, limit,
                Optional.empty(), Instant.now(), speculative);
    }

    @Override
    public List<LongTermMemorySnippet> retrieveSpatial(UUID petId, String playerMessage,
            String backend, String dimension, String biome, int limit,
            Optional<com.silver.aipets.common.transport.SpatialLocation> location,
            Instant now, boolean speculative) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(playerMessage, "playerMessage");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(dimension, "dimension");
        if (limit < 1 || limit > 3) {
            throw new IllegalArgumentException("limit must be between 1 and 3");
        }
        DialogueSafety.SafetyResult safe = localSafety.preprocess(playerMessage, 500);
        if (!safe.allowed()) {
            return fallback.retrieveSpatial(petId, "", backend, dimension, biome, limit, location, now, speculative);
        }
        List<LongTermMemorySnippet> cheap = fallback.retrieveSpatial(
                petId, safe.normalizedText(), backend, dimension, biome, limit, location, now, speculative);
        if (safe.normalizedText().split("\\s+").length <= 2
                || (!cheap.isEmpty() && cheap.getFirst().similarity() >= 0.75)) {
            return cheap;
        }
        MemoryRetrievalResult result;
        try {
            MemorySearchQuery query = new MemorySearchQuery(
                    petId,
                    safe.normalizedText(),
                    optional(backend),
                    optional(dimension),
                    Optional.empty(),
                    Set.of(),
                    0.35D,
                    12);
            result = speculative ? vectors.retrieveSpeculatively(query) : vectors.retrieve(query);
        } catch (RuntimeException unavailable) {
            return cheap;
        }
        if (result.vectorDegraded() || result.cards().isEmpty()) {
            return cheap;
        }
        long rankingStart = System.nanoTime();
        var candidates = new java.util.LinkedHashMap<UUID, LongTermMemorySnippet>();
        cheap.forEach(snippet -> candidates.put(snippet.card().memoryId(), snippet));
        result.cards().stream()
                .filter(card -> MemoryRanking.relevant(safe.normalizedText(), card,
                        result.scores().getOrDefault(card.memoryId(), 0.0D)))
                .map(card -> new LongTermMemorySnippet(
                        card, result.scores().getOrDefault(card.memoryId(), 0.0D),
                        LongTermMemorySnippet.Source.VECTOR,
                        MemoryRanking.score(safe.normalizedText(), card,
                                result.scores().getOrDefault(card.memoryId(), 0.0D),
                                backend, dimension, biome, now, location)))
                // Prefer the freshly hydrated vector record for duplicate IDs; never
                // pretend a text-only candidate has a measured semantic similarity.
                .forEach(snippet -> candidates.put(snippet.card().memoryId(), snippet));
        List<LongTermMemorySnippet> ranked = candidates.values().stream()
                .sorted(Comparator.comparingDouble(LongTermMemorySnippet::rankingScore).reversed()
                        .thenComparing(snippet -> snippet.card().memoryId().toString()))
                .limit(limit)
                .toList();
        DialogueDiagnostics.elapsed("spatial_memory_reranking", rankingStart);
        return ranked;
    }

    private static Optional<String> optional(String value) {
        String normalized = value.strip();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }
}
