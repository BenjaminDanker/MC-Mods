package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.List;
import java.util.Objects;

public record MemoryRetrievalResult(List<LongTermMemoryCard> cards, boolean vectorDegraded,
                                    java.util.Map<java.util.UUID, Double> scores) {
    public MemoryRetrievalResult(List<LongTermMemoryCard> cards, boolean vectorDegraded) {
        this(cards, vectorDegraded, java.util.Map.of());
    }

    public MemoryRetrievalResult {
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        scores = java.util.Map.copyOf(Objects.requireNonNull(scores, "scores"));
        if (cards.size() > 12) {
            throw new IllegalArgumentException("At most twelve long-term cards may be retrieved");
        }
    }

    public static MemoryRetrievalResult degraded() {
        return new MemoryRetrievalResult(List.of(), true);
    }
}
