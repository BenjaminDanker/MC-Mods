package com.silver.aipets.service.memory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Thread-safe development adapter retaining relational scoping semantics. */
public final class InMemoryLongTermMemoryStore implements LongTermMemoryStore {
    private final Object monitor = new Object();
    private final Map<UUID, LongTermMemoryCard> cards = new HashMap<>();
    private final Map<UUID, EmbeddingState> embeddings = new HashMap<>();
    private final Map<UUID, Long> recallCounts = new HashMap<>();

    public void put(LongTermMemoryCard card) {
        Objects.requireNonNull(card, "card");
        synchronized (monitor) {
            cards.put(card.memoryId(), card);
        }
    }

    @Override
    public Optional<LongTermMemoryCard> findActive(UUID petId, UUID memoryId) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(memoryId, "memoryId");
        synchronized (monitor) {
            LongTermMemoryCard card = cards.get(memoryId);
            return card != null && card.active() && card.petId().equals(petId)
                    ? Optional.of(card) : Optional.empty();
        }
    }

    @Override
    public MemoryPage listActive(Optional<UUID> afterMemoryId, int limit) {
        Objects.requireNonNull(afterMemoryId, "afterMemoryId");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        synchronized (monitor) {
            List<LongTermMemoryCard> ordered = cards.values().stream()
                    .filter(LongTermMemoryCard::active)
                    .sorted(Comparator.comparing(card -> card.memoryId().toString()))
                    .toList();
            int start = 0;
            if (afterMemoryId.isPresent()) {
                String after = afterMemoryId.orElseThrow().toString();
                while (start < ordered.size()
                        && ordered.get(start).memoryId().toString().compareTo(after) <= 0) {
                    start++;
                }
            }
            int end = Math.min(start + limit, ordered.size());
            List<LongTermMemoryCard> page = new ArrayList<>(ordered.subList(start, end));
            Optional<UUID> next = end < ordered.size() && !page.isEmpty()
                    ? Optional.of(page.getLast().memoryId()) : Optional.empty();
            return new MemoryPage(page, next);
        }
    }

    @Override
    public MemoryPage listActiveForPet(UUID petId, int limit) {
        Objects.requireNonNull(petId, "petId");
        if (limit < 1 || limit > 1_000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000");
        }
        synchronized (monitor) {
            List<LongTermMemoryCard> cardsForPet = cards.values().stream()
                    .filter(card -> card.active() && card.petId().equals(petId))
                    .sorted(Comparator.comparing(card -> card.memoryId().toString()))
                    .limit(limit)
                    .toList();
            return new MemoryPage(cardsForPet, Optional.empty());
        }
    }

    @Override
    public void markEmbeddingReady(
            UUID petId, UUID memoryId, long expectedVersion,
            String model, String reference, Instant at) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(at, "at");
        synchronized (monitor) {
            requireExact(petId, memoryId, expectedVersion);
            embeddings.put(memoryId, new EmbeddingState("READY", model, reference));
        }
    }

    @Override
    public void markEmbeddingFailed(
            UUID petId, UUID memoryId, long expectedVersion, Instant at) {
        Objects.requireNonNull(at, "at");
        synchronized (monitor) {
            requireExact(petId, memoryId, expectedVersion);
            embeddings.put(memoryId, new EmbeddingState("FAILED", null, null));
        }
    }

    @Override
    public void recordRecalled(UUID petId, UUID memoryId, Instant at) {
        Objects.requireNonNull(at, "at");
        synchronized (monitor) {
            LongTermMemoryCard card = cards.get(memoryId);
            if (card != null && card.active() && card.petId().equals(petId)) {
                recallCounts.merge(memoryId, 1L, Math::addExact);
            }
        }
    }

    public String embeddingStatus(UUID memoryId) {
        synchronized (monitor) {
            EmbeddingState state = embeddings.get(memoryId);
            return state == null ? "PENDING" : state.status();
        }
    }

    public long recallCount(UUID memoryId) {
        synchronized (monitor) {
            return recallCounts.getOrDefault(memoryId, 0L);
        }
    }

    private LongTermMemoryCard requireExact(UUID petId, UUID memoryId, long version) {
        LongTermMemoryCard card = cards.get(memoryId);
        if (card == null || !card.petId().equals(petId) || card.version() != version) {
            throw new IllegalStateException("Memory changed before embedding state update");
        }
        return card;
    }

    private record EmbeddingState(String status, String model, String reference) {
    }
}
