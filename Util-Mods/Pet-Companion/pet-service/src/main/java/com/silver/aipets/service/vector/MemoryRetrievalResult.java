package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.List;
import java.util.Objects;

public record MemoryRetrievalResult(List<LongTermMemoryCard> cards, boolean vectorDegraded) {
    public MemoryRetrievalResult {
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        if (cards.size() > 3) {
            throw new IllegalArgumentException("At most three long-term cards may be retrieved");
        }
    }

    public static MemoryRetrievalResult degraded() {
        return new MemoryRetrievalResult(List.of(), true);
    }
}
