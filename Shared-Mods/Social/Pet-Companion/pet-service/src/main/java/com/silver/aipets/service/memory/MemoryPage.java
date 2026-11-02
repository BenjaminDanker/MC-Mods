package com.silver.aipets.service.memory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record MemoryPage(List<LongTermMemoryCard> cards, Optional<UUID> nextAfterMemoryId) {
    public MemoryPage {
        cards = List.copyOf(Objects.requireNonNull(cards, "cards"));
        Objects.requireNonNull(nextAfterMemoryId, "nextAfterMemoryId");
    }
}
