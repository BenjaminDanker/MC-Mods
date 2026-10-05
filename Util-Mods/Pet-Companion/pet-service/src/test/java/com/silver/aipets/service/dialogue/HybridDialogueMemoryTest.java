package com.silver.aipets.service.dialogue;

import com.silver.aipets.service.memory.*;
import com.silver.aipets.service.vector.*;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HybridDialogueMemoryTest {
    private static final UUID PET = new UUID(0, 301);

    @Test void partialTextMatchSurvivesSuccessfulButIrrelevantVectorResults() {
        var sql = new InMemoryLongTermMemoryStore();
        var bridge = card(1, "We crossed a spruce bridge in rain");
        var unrelated = card(2, "Owner fed the pet a salmon");
        sql.put(bridge); sql.put(unrelated);
        var vectors = new InMemoryVectorMemoryRepository();
        // The witnessed bridge has not been indexed yet; the other card is returned.
        vectors.upsert(new VectorMemoryDocument(unrelated,
                new EmbeddingVector("test", new float[]{.6f, .8f})));
        var results = retriever(sql, vectors).retrieve(PET,
                "Remember the bridge crossing yesterday", "survival", "minecraft:overworld", 3);
        assertEquals(List.of(bridge), results.stream().map(LongTermMemorySnippet::card).toList());
        assertEquals(LongTermMemorySnippet.Source.RELATIONAL, results.getFirst().source());
    }

    @Test void hybridUnionKeepsUnindexedTextEvidenceAndDeduplicatesVectorEvidence() {
        var sql = new InMemoryLongTermMemoryStore();
        var bridge = card(1, "We crossed a spruce bridge in rain");
        var tunnel = card(2, "We sheltered together in a tunnel");
        sql.put(bridge); sql.put(tunnel);
        var vectors = new InMemoryVectorMemoryRepository();
        vectors.upsert(new VectorMemoryDocument(tunnel,
                new EmbeddingVector("test", new float[]{1, 0})));
        var results = retriever(sql, vectors).retrieve(PET,
                "Remember the bridge and tunnel yesterday", "survival", "minecraft:overworld", 3);
        assertEquals(List.of(tunnel, bridge), results.stream().map(LongTermMemorySnippet::card).toList());
        assertEquals(LongTermMemorySnippet.Source.VECTOR, results.getFirst().source());
        assertEquals(LongTermMemorySnippet.Source.RELATIONAL, results.getLast().source());
        assertEquals(2, results.stream().map(item -> item.card().memoryId()).distinct().count());
    }

    @Test void unionStaysBoundedAndNeverIncludesAnotherPetsTextEvidence() {
        var sql = new InMemoryLongTermMemoryStore();
        for (int i = 1; i <= 4; i++) sql.put(card(i, "We crossed bridge number " + i));
        sql.put(new LongTermMemoryCard(new UUID(0, 90), new UUID(0, 302), 1,
                "Private bridge memory", MemoryImportance.HIGH, Set.of(), Set.of(), Set.of(), true));
        var vectors = new InMemoryVectorMemoryRepository();
        vectors.upsert(new VectorMemoryDocument(card(1, "We crossed bridge number 1"),
                new EmbeddingVector("test", new float[]{1, 0})));
        var results = retriever(sql, vectors).retrieve(PET,
                "Tell me about bridge crossings yesterday", "survival", "minecraft:overworld", 3);
        assertEquals(3, results.size());
        assertTrue(results.stream().allMatch(item -> item.card().petId().equals(PET)));
    }

    @Test void degradedVectorPathReusesTheAlreadyLoadedTextResults() {
        var sql = new InMemoryLongTermMemoryStore();
        var bridge = card(1, "We crossed a spruce bridge in rain");
        sql.put(bridge);
        var vectors = new InMemoryVectorMemoryRepository(); vectors.setAvailable(false);
        var reads = new java.util.concurrent.atomic.AtomicInteger();
        DialogueMemoryRetriever fallback = (pet, message, backend, dimension, limit) -> {
            reads.incrementAndGet();
            return new RelationalDialogueMemoryRetriever(sql).retrieve(pet, message, backend, dimension, limit);
        };
        EmbeddingModelClient model = new EmbeddingModelClient() {
            public String model() { return "test"; }
            public EmbeddingVector embed(String text) { return new EmbeddingVector(model(), new float[]{1, 0}); }
        };
        var retriever = new VectorDialogueMemoryRetriever(new MemoryRetrievalService(sql, vectors,
                model, Runnable::run, Clock.systemUTC()), fallback);
        assertEquals(List.of(bridge), retriever.retrieve(PET, "Remember the bridge crossing yesterday",
                "survival", "minecraft:overworld", 3).stream().map(LongTermMemorySnippet::card).toList());
        assertEquals(1, reads.get());
    }

    private static VectorDialogueMemoryRetriever retriever(InMemoryLongTermMemoryStore sql,
            InMemoryVectorMemoryRepository vectors) {
        EmbeddingModelClient model = new EmbeddingModelClient() {
            public String model() { return "test"; }
            public EmbeddingVector embed(String text) {
                return new EmbeddingVector(model(), new float[]{1, 0});
            }
        };
        return new VectorDialogueMemoryRetriever(new MemoryRetrievalService(sql, vectors,
                model, Runnable::run, Clock.systemUTC()), new RelationalDialogueMemoryRetriever(sql));
    }

    private static LongTermMemoryCard card(int id, String text) {
        return new LongTermMemoryCard(new UUID(0, id), PET, 1, text,
                MemoryImportance.MEDIUM, Set.of(), Set.of(), Set.of(), true);
    }
}
