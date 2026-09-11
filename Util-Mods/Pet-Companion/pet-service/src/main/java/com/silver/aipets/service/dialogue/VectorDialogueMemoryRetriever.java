package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.vector.MemoryRetrievalResult;
import com.silver.aipets.service.vector.MemoryRetrievalService;
import com.silver.aipets.service.vector.MemorySearchQuery;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Vector recall adapter with a relational fallback for provider or collection outages. */
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
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(playerMessage, "playerMessage");
        Objects.requireNonNull(backend, "backend");
        Objects.requireNonNull(dimension, "dimension");
        if (limit < 1 || limit > 3) {
            throw new IllegalArgumentException("limit must be between 1 and 3");
        }
        DialogueSafety.SafetyResult safe = localSafety.preprocess(playerMessage, 500);
        if (!safe.allowed()) {
            return fallback.retrieve(petId, "", backend, dimension, limit);
        }
        MemoryRetrievalResult result;
        try {
            result = vectors.retrieve(new MemorySearchQuery(
                    petId,
                    safe.normalizedText(),
                    optional(backend),
                    optional(dimension),
                    Optional.empty(),
                    Set.of(),
                    0.35D,
                    limit));
        } catch (RuntimeException unavailable) {
            return fallback.retrieve(petId, safe.normalizedText(), backend, dimension, limit);
        }
        if (result.vectorDegraded() || result.cards().isEmpty()) {
            return fallback.retrieve(petId, safe.normalizedText(), backend, dimension, limit);
        }
        return result.cards().stream()
                .map(card -> new LongTermMemorySnippet(
                        card, 0.35D, LongTermMemorySnippet.Source.VECTOR))
                .toList();
    }

    private static Optional<String> optional(String value) {
        String normalized = value.strip();
        return normalized.isEmpty() ? Optional.empty() : Optional.of(normalized);
    }
}
