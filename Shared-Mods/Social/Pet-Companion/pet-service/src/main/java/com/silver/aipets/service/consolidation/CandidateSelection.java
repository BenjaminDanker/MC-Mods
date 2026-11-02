package com.silver.aipets.service.consolidation;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CandidateSelection(
        List<ConsolidationEvent> selected,
        List<UUID> discardedEventIds,
        int selectedTokens) {
    public CandidateSelection {
        selected = List.copyOf(Objects.requireNonNull(selected, "selected"));
        discardedEventIds = List.copyOf(Objects.requireNonNull(discardedEventIds, "discardedEventIds"));
        if (selectedTokens < 0) {
            throw new IllegalArgumentException("selectedTokens cannot be negative");
        }
    }
}
