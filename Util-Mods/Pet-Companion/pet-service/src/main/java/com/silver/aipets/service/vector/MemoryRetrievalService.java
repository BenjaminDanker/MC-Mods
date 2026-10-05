package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.LongTermMemoryStore;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.silver.aipets.service.billing.AiBudgetService;
import com.silver.aipets.service.billing.AiPricing;
import com.silver.aipets.service.dialogue.DialogueDiagnostics;

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
import java.time.Duration;

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
    private final AiPricing pricing;
    private final Function<UUID, Optional<UUID>> ownerResolver;
    private final java.util.Map<String, CachedEmbedding> queryCache =
            java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>());

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock) {
        this(memories, vectors, model, recallUpdates, clock, new PetOperationalMetrics(),
                EmbeddingUsageRecorder.NOOP, AiBudgetService.UNLIMITED, ignored -> Optional.empty(),
                AiPricing.defaults());
    }

    public MemoryRetrievalService(
            LongTermMemoryStore memories,
            VectorMemoryRepository vectors,
            EmbeddingModelClient model,
            Executor recallUpdates,
            Clock clock,
            PetOperationalMetrics metrics) {
        this(memories, vectors, model, recallUpdates, clock, metrics,
                EmbeddingUsageRecorder.NOOP, AiBudgetService.UNLIMITED, ignored -> Optional.empty(),
                AiPricing.defaults());
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
        this(memories, vectors, model, recallUpdates, clock, metrics, usageRecorder,
                budget, ownerResolver, AiPricing.defaults());
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
            Function<UUID, Optional<UUID>> ownerResolver,
            AiPricing pricing) {
        this.memories = Objects.requireNonNull(memories, "memories");
        this.vectors = Objects.requireNonNull(vectors, "vectors");
        this.model = Objects.requireNonNull(model, "model");
        this.recallUpdates = Objects.requireNonNull(recallUpdates, "recallUpdates");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.usageRecorder = Objects.requireNonNull(usageRecorder, "usageRecorder");
        this.budget = Objects.requireNonNull(budget, "budget");
        this.pricing = Objects.requireNonNull(pricing, "pricing");
        this.ownerResolver = Objects.requireNonNull(ownerResolver, "ownerResolver");
    }

    public MemoryRetrievalResult retrieve(MemorySearchQuery query) {
        return retrieve(query, true);
    }

    /** Read-only retrieval for speculative dialogue; no usage, budget, or recall write is committed. */
    public MemoryRetrievalResult retrieveSpeculatively(MemorySearchQuery query) {
        return retrieve(query, false);
    }

    private MemoryRetrievalResult retrieve(MemorySearchQuery query, boolean recordEffects) {
        Objects.requireNonNull(query, "query");
        // A zero-card pet cannot have a valid Qdrant match. This cheap scoped SQL read
        // avoids an embedding provider round trip on first conversations and after reset.
        if (memories.listActiveForPet(query.petId(), 1).cards().isEmpty()) {
            return new MemoryRetrievalResult(List.of(), false);
        }
        final List<VectorMemoryMatch> matches;
        AiBudgetService.Reservation reservation = null;
        BigDecimal actualCost = BigDecimal.ZERO;
        try {
            String key = query.petId() + ":" + query.deterministicSearchText()
                    .toLowerCase(java.util.Locale.ROOT);
            EmbeddingVector queryEmbedding;
            CachedEmbedding cached = queryCache.get(key);
            if (cached != null && cached.expiresAt().isAfter(clock.instant())) {
                queryEmbedding = cached.vector();
                DialogueDiagnostics.elapsed("query_embedding_cache_hit", System.nanoTime());
            } else {
                Optional<UUID> owner = recordEffects ? ownerResolver.apply(query.petId()) : Optional.empty();
                if (owner.isPresent()) {
                    reservation = budget.tryReserve(
                            owner.orElseThrow(), pricing.maximumEmbeddingCost(8192),
                            clock.instant()).orElse(null);
                    if (reservation == null) return MemoryRetrievalResult.degraded();
                }
                long embeddingStart = System.nanoTime();
                EmbeddingModelResponse response = model.embedWithUsage(query.deterministicSearchText());
                DialogueDiagnostics.elapsed("query_embedding_provider", embeddingStart);
                actualCost = pricing.embeddingCost(response.inputTokens());
                if (recordEffects) usageRecorder.record(query.petId(), response, clock.instant(), "SUCCEEDED", null);
                queryEmbedding = response.embedding();
                synchronized (queryCache) {
                    if (queryCache.size() >= 64) queryCache.remove(queryCache.keySet().iterator().next());
                    queryCache.put(key, new CachedEmbedding(queryEmbedding,
                            clock.instant().plus(Duration.ofMinutes(10))));
                }
            }
            long vectorStart = System.nanoTime();
            matches = vectors.search(query, queryEmbedding);
            DialogueDiagnostics.elapsed("qdrant_search", vectorStart);
            if (matches == null) {
                return MemoryRetrievalResult.degraded();
            }
        } catch (RuntimeException failure) {
            return MemoryRetrievalResult.degraded();
        } finally {
            if (reservation != null) {
                budget.settle(reservation, actualCost);
            }
        }

        List<LongTermMemoryCard> cards = new ArrayList<>(query.maximumResults());
        java.util.Map<UUID, Double> scores = new java.util.HashMap<>();
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
            scores.put(card.memoryId(), match.similarity());
        }
        if (recordEffects && !cards.isEmpty()) {
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
        return new MemoryRetrievalResult(cards, false, scores);
    }

    private static boolean matchesMetadata(LongTermMemoryCard card, MemorySearchQuery query) {
        if (!query.namedEntities().isEmpty()
                && java.util.Collections.disjoint(card.entityTags(), query.namedEntities())) {
            return false;
        }
        return true;
    }

    private record CachedEmbedding(EmbeddingVector vector, Instant expiresAt) { }
}
