package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.Objects;

public record LongTermMemorySnippet(LongTermMemoryCard card, double similarity) {
    public LongTermMemorySnippet {
        Objects.requireNonNull(card, "card");
        if (!card.active() || !Double.isFinite(similarity) || similarity < -1 || similarity > 1) {
            throw new IllegalArgumentException("Invalid long-term memory snippet");
        }
    }
}
