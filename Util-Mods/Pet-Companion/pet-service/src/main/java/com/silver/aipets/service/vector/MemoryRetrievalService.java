package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.LongTermMemoryStore;
import com.silver.aipets.service.metrics.PetOperationalMetrics;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;

/** Defense-in-depth scoped retrieval with authoritative relational hydration and outage fallback. */
public final class MemoryRetrievalService {
    private final LongTermMemoryStore memories;
    private final VectorMemoryRepository vectors;
    private final EmbeddingModelClient model;
    private final Executor recallUpdates;
    private final Clock clock;
    private final PetOperationalMetrics metrics;

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock) {
        this(memories, vectors, model, recallUpdates, clock, new PetOperationalMetrics());
    }

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock,
            PetOperationalMetrics metrics) {
        this.memories = Objects.requireNonNull(memories, "memories");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.model = Objects.requireNonNull(model, "model");
        this.recallUpdates = Objects.requireNonNull(recallUpdates, "recallUpdates");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    public MemoryRetrievalResult retrieve(MemorySearchQuery query) {
        Objects.requireNonNull(query, "query");
        final List<VectorMemoryMatch> matches;
        try {
            EmbeddingVector queryEmbedding = model.embed(query.deterministicSearchText());
            matches = vectors.search(query, queryEmbedding);
            if (matches == null) {
                return MemoryRetrievalResult.degraded();
            }
        } catch (RuntimeException failure) {
            return MemoryRetrievalResult.degraded();
        }

        List<LongTermMemoryCard> cards = new ArrayList<>(query.maximumResults());
        Set<UUID> seen = new HashSet<>();
        for (VectorMemoryMatch match : matches) {
            if (cards.size() >= query.maximumResults()) {
                break;
            }
            // Reject a broken/malicious provider result before any relational lookup.
            if (match == null
                    || !match.petId().equals(query.petId())
                    || match.similarity() < query.minimumSimilarity()
                    || !seen.add(match.memoryId())) {
                continue;
            }
            LongTermMemoryCard card = memories.findActive(query.petId(), match.memoryId())
                    .filter(candidate -> candidate.version() == match.memoryVersion())
                    .orElse(null);
            if (card == null || !matchesMetadata(card, query)) {
                continue;
            }
            cards.add(card);
        }
        if (!cards.isEmpty()) {
            List<LongTermMemoryCard> recalled = List.copyOf(cards);
            try {
                recallUpdates.execute(() -> recalled.forEach(card -> {
                    try {
                        memories.recordRecalled(card.petId(), card.memoryId(), clock.instant());
                    } catch (RuntimeException ignored) {
                        // Recall counters are best-effort and must never fail dialogue retrieval.
                    }
                }));
            } catch (RuntimeException ignored) {
                // Executor shutdown/rejection must not fail an otherwise valid retrieval.
            }
        }
        metrics.add(PetOperationalMetrics.Counter.MEMORIES_RETRIEVED, cards.size());
        return new MemoryRetrievalResult(cards, false);
    }

    private static boolean matchesMetadata(LongTermMemoryCard card, MemorySearchQuery query) {
        if (!query.namedEntities().isEmpty()
                && java.util.Collections.disjoint(card.entityTags(), query.namedEntities())) {
            return false;
        }
        if (query.dimension().isPresent()
                && !card.locationTags().contains(query.dimension().orElseThrow())) {
            return false;
        }
        return true;
    }
}
