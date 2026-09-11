package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.Objects;

public record LongTermMemorySnippet(LongTermMemoryCard card, double similarity, Source source) {
    public LongTermMemorySnippet(LongTermMemoryCard card, double similarity) {
        this(card, similarity, Source.RELATIONAL);
    }

    public LongTermMemorySnippet {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(source, "source");
        if (!card.active() || !Double.isFinite(similarity) || similarity < -1 || similarity > 1) {
            throw new IllegalArgumentException("Invalid long-term memory snippet");
        }
    }

    public enum Source {
        RELATIONAL,
        VECTOR
    }
}
