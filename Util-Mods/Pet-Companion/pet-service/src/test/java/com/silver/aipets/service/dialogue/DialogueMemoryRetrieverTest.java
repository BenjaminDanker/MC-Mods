package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.InMemoryLongTermMemoryStore;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.vector.EmbeddingModelClient;
import com.silver.aipets.service.vector.EmbeddingVector;
import com.silver.aipets.service.vector.InMemoryVectorMemoryRepository;
import com.silver.aipets.service.vector.MemoryRetrievalService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DialogueMemoryRetrieverTest {
    private static final UUID PET = UUID.fromString("10000000-0000-0000-0000-000000000101");

    @Test
    void vectorRecallUsesPetScopedCardsAndFallsBackToRelationalCards() {
        InMemoryLongTermMemoryStore memories = new InMemoryLongTermMemoryStore();
        LongTermMemoryCard card = card(1, PET);
        memories.put(card);
        InMemoryVectorMemoryRepository vectors = new InMemoryVectorMemoryRepository();
        vectors.upsert(new com.silver.aipets.service.vector.VectorMemoryDocument(
                card, new EmbeddingVector("test", new float[] {1.0f, 0.0f})));
        DialogueMemoryRetriever fallback = new RelationalDialogueMemoryRetriever(memories);
        VectorDialogueMemoryRetriever retriever = new VectorDialogueMemoryRetriever(
                new MemoryRetrievalService(
                        memories, vectors, new TestEmbeddingModel(), Runnable::run,
                        Clock.systemUTC()),
                fallback);

        assertEquals(List.of(card), retriever.retrieve(
                PET, "remember this", "vanilla1", "minecraft:overworld", 3)
                .stream().map(LongTermMemorySnippet::card).toList());

        vectors.setAvailable(false);
        assertEquals(List.of(card), retriever.retrieve(
                PET, "remember this", "vanilla1", "minecraft:overworld", 3)
                .stream().map(LongTermMemorySnippet::card).toList());
    }

    private static LongTermMemoryCard card(int suffix, UUID petId) {
        return new LongTermMemoryCard(
                new UUID(0L, suffix), petId, 1, "remembered card " + suffix,
                MemoryImportance.MEDIUM, Set.of("happy"), Set.of(), Set.of(), true);
    }

    private static final class TestEmbeddingModel implements EmbeddingModelClient {
        @Override
        public String model() {
            return "test";
        }

        @Override
        public EmbeddingVector embed(String normalizedText) {
            return new EmbeddingVector(model(), new float[] {1.0f, 0.0f});
        }
    }
}
