package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.LongTermMemoryStore;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.silver.aipets.service.billing.AiBudgetService;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.function.Function;
import java.util.concurrent.Executor;

/** Defense-in-depth scoped retrieval with authoritative relational hydration and outage fallback. */
public final class MemoryRetrievalService {
    private final LongTermMemoryStore memories;
    private final VectorMemoryRepository vectors;
    private final EmbeddingModelClient model;
    private final Executor recallUpdates;
    private final Clock clock;
    private final PetOperationalMetrics metrics;
    private final EmbeddingUsageRecorder usageRecorder;
    private final AiBudgetService budget;
    private final Function<UUID, Optional<UUID>> ownerResolver;

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock) {
        this(memories, vectors, model, recallUpdates, clock, new PetOperationalMetrics(),
                EmbeddingUsageRecorder.NOOP, AiBudgetService.UNLIMITED, ignored -> Optional.empty());
    }

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock,
            PetOperationalMetrics metrics) {
        this(memories, vectors, model, recallUpdates, clock, metrics,
                EmbeddingUsageRecorder.NOOP, AiBudgetService.UNLIMITED, ignored -> Optional.empty());
    }

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock,
            PetOperationalMetrics metrics,
            EmbeddingUsageRecorder usageRecorder,
            AiBudgetService budget,
            Function<UUID, Optional<UUID>> ownerResolver) {
        this.memories = Objects.requireNonNull(memories, "memories");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.model = Objects.requireNonNull(model, "model");
        this.recallUpdates = Objects.requireNonNull(recallUpdates, "recallUpdates");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.usageRecorder = Objects.requireNonNull(usageRecorder, "usageRecorder");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.ownerResolver = Objects.requireNonNull(ownerResolver, "ownerResolver");
    }

    public MemoryRetrievalResult retrieve(MemorySearchQuery query) {
        Objects.requireNonNull(query, "query");
        final List<VectorMemoryMatch> matches;
        AiBudgetService.Reservation reservation = null;
        try {
            Optional<UUID> owner = ownerResolver.apply(query.petId());
            if (owner.isPresent()) {
                reservation = budget.tryReserve(
                        owner.orElseThrow(), new BigDecimal("0.00016384"), clock.instant()).orElse(null);
                if (reservation == null) return MemoryRetrievalResult.degraded();
            }
            EmbeddingModelResponse response = model.embedWithUsage(query.deterministicSearchText());
            usageRecorder.record(query.petId(), response, clock.instant(), "SUCCEEDED", null);
            EmbeddingVector queryEmbedding = response.embedding();
            matches = vectors.search(query, queryEmbedding);
            if (matches == null) {
                return MemoryRetrievalResult.degraded();
            }
        } catch (RuntimeException failure) {
            return MemoryRetrievalResult.degraded();
        } finally {
            if (reservation != null) {
                // Retrieval usage is persisted by the recorder; reserve only protects concurrent calls.
                budget.settle(reservation, BigDecimal.ZERO);
            }
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
