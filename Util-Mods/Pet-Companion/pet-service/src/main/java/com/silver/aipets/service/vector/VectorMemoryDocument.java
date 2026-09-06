package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.LongTermMemoryCard;

import java.util.Objects;

/** The only vector document accepted by the repository is derived from a compact memory card. */
public record VectorMemoryDocument(LongTermMemoryCard card, EmbeddingVector embedding) {
    public VectorMemoryDocument {
        Objects.requireNonNull(card, "card");
        Objects.requireNonNull(embedding, "embedding");
        if (!card.active()) {
            throw new IllegalArgumentException("Cannot index an inactive memory card");
        }
    }
}
