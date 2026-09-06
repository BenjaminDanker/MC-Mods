package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.domain.PetTraits;
import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.List;
import java.util.Objects;

public record ConsolidationCommitResult(
        List<LongTermMemoryCard> cards, PetTraits traits, int consolidatedEvents, int discardedEvents) {
    public ConsolidationCommitResult {
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        Objects.requireNonNull(traits, "traits");
        if (consolidatedEvents < 0 || discardedEvents < 0) {
            throw new IllegalArgumentException("Event counts cannot be negative");
        }
    }
}
