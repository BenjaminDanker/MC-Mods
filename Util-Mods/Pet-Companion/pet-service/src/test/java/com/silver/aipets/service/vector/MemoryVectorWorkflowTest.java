package com.silver.aipets.service.vector;

import com.silver.aipets.service.memory.InMemoryLongTermMemoryStore;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MemoryVectorWorkflowTest {
    private static final Instant START = Instant.parse("2026-08-31T12:00:00Z");
    private static final UUID PET_A = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID PET_B = UUID.fromString("10000000-0000-0000-0000-000000000002");

    @Test
    void fullReindexIsCardOnlyIdempotentAndRetriesAnUnavailableVectorStore() {
        InMemoryLongTermMemoryStore memories = new InMemoryLongTermMemoryStore();
        List<LongTermMemoryCard> active = List.of(
                card(1, PET_A, true), card(2, PET_A, true), card(3, PET_A, true),
                card(4, PET_B, true), card(5, PET_B, true));
        active.forEach(memories::put);
        memories.put(card(6, PET_A, false));

        InMemoryEmbeddingJobStore jobs = new InMemoryEmbeddingJobStore();
        InMemoryVectorMemoryRepository vectors = new InMemoryVectorMemoryRepository();
        vectors.setAvailable(false);
        MutableClock clock = new MutableClock(START);
        CapturingEmbeddingModel model = new CapturingEmbeddingModel();
        AtomicInteger ids = new AtomicInteger();
        MemoryEmbeddingWorker worker = new MemoryEmbeddingWorker(
                memories, jobs, model, vectors, clock,
                () -> new UUID(0L, ids.incrementAndGet()),
                3, Duration.ofMinutes(2), Duration.ofSeconds(30));
        MemoryReindexService reindex = new MemoryReindexService(memories, worker);

        MemoryReindexResult first = reindex.enqueueAll(2);
        assertEquals(new MemoryReindexResult(5, 5, 0), first);
        MemoryReindexResult replay = reindex.enqueueAll(3);
        assertEquals(new MemoryReindexResult(5, 0, 5), replay);
        assertThrows(IllegalArgumentException.class, () -> worker.enqueue(card(7, PET_A, false)));

        assertEquals(5, worker.processDue("worker-a", 10));
        for (LongTermMemoryCard card : active) {
            EmbeddingJob job = jobs.findByIdempotencyKey(
                    EmbeddingJobKeys.forMemory(
                            card.petId(), card.memoryId(), card.version(), model.model()))
                    .orElseThrow();
            assertEquals(EmbeddingJobStatus.RETRY, job.status());
            assertEquals(1, job.attemptCount());
            assertEquals("PENDING", memories.embeddingStatus(card.memoryId()));
        }

        vectors.setAvailable(true);
        clock.advance(Duration.ofSeconds(30));
        assertEquals(5, worker.processDue("worker-b", 10));
        for (LongTermMemoryCard card : active) {
            EmbeddingJob job = jobs.findByIdempotencyKey(
                    EmbeddingJobKeys.forMemory(
                            card.petId(), card.memoryId(), card.version(), model.model()))
                    .orElseThrow();
            assertEquals(EmbeddingJobStatus.SUCCEEDED, job.status());
            assertEquals(2, job.attemptCount());
            assertEquals("READY", memories.embeddingStatus(card.memoryId()));
        }
        assertTrue(model.inputs.stream().allMatch(input -> input.startsWith("compact-card-")));
        assertFalse(model.inputs.stream().anyMatch(input -> input.contains("raw dialogue")));
    }

    @Test
    void retrievalScopesBeforeRankingFiltersMetadataCapsThreeAndDegradesOnOutage() {
        InMemoryLongTermMemoryStore memories = new InMemoryLongTermMemoryStore();
        InMemoryVectorMemoryRepository vectors = new InMemoryVectorMemoryRepository();
        for (int index = 1; index <= 5; index++) {
            LongTermMemoryCard card = taggedCard(index, PET_A, "Alex", "minecraft:the_nether");
            memories.put(card);
            vectors.upsert(new VectorMemoryDocument(
                    card, new EmbeddingVector("test-embedding", new float[]{1.0f, index / 100.0f})));
        }
        LongTermMemoryCard wrongPet = taggedCard(20, PET_B, "Alex", "minecraft:the_nether");
        memories.put(wrongPet);
        vectors.upsert(new VectorMemoryDocument(
                wrongPet, new EmbeddingVector("test-embedding", new float[]{1.0f, 0.0f})));
        LongTermMemoryCard wrongLocation = taggedCard(21, PET_A, "Alex", "minecraft:overworld");
        memories.put(wrongLocation);
        vectors.upsert(new VectorMemoryDocument(
                wrongLocation, new EmbeddingVector("test-embedding", new float[]{1.0f, 0.0f})));

        CapturingEmbeddingModel model = new CapturingEmbeddingModel();
        MemoryRetrievalService retrieval = new MemoryRetrievalService(
                memories, vectors, model, Runnable::run,
                Clock.fixed(START, ZoneOffset.UTC));
        MemorySearchQuery query = new MemorySearchQuery(
                PET_A,
                "Do you remember Alex?",
                Optional.of("survival"),
                Optional.of("minecraft:the_nether"),
                Optional.of("dialogue"),
                Set.of("Alex"),
                0.95,
                3);

        MemoryRetrievalResult result = retrieval.retrieve(query);
        assertFalse(result.vectorDegraded());
        assertEquals(3, result.cards().size());
        assertTrue(result.cards().stream().allMatch(card -> card.petId().equals(PET_A)));
        assertTrue(result.cards().stream()
                .allMatch(card -> card.locationTags().contains("minecraft:the_nether")));
        assertEquals(List.of(query.deterministicSearchText()), model.inputs);
        assertEquals(3L, result.cards().stream().mapToLong(
                card -> memories.recallCount(card.memoryId())).sum());

        vectors.setAvailable(false);
        MemoryRetrievalResult degraded = retrieval.retrieve(query);
        assertTrue(degraded.vectorDegraded());
        assertTrue(degraded.cards().isEmpty());

        MemoryRetrievalService embeddingFailure = new MemoryRetrievalService(
                memories, vectors, new FailingEmbeddingModel(), Runnable::run,
                Clock.fixed(START, ZoneOffset.UTC));
        MemoryRetrievalResult embeddingDegraded = embeddingFailure.retrieve(query);
        assertTrue(embeddingDegraded.vectorDegraded());
        assertTrue(embeddingDegraded.cards().isEmpty());

        vectors.setAvailable(true);
        MemoryRetrievalService rejectedRecallUpdate = new MemoryRetrievalService(
                memories, vectors, model,
                ignored -> { throw new java.util.concurrent.RejectedExecutionException(); },
                Clock.fixed(START, ZoneOffset.UTC));
        MemoryRetrievalResult retrievedDespiteRejection = rejectedRecallUpdate.retrieve(query);
        assertFalse(retrievedDespiteRejection.vectorDegraded());
        assertEquals(3, retrievedDespiteRejection.cards().size());
    }

    private static LongTermMemoryCard card(int suffix, UUID petId, boolean active) {
        return new LongTermMemoryCard(
                new UUID(0L, suffix), petId, 1, "compact-card-" + suffix,
                MemoryImportance.MEDIUM, Set.of("content"), Set.of(), Set.of(), active);
    }

    private static LongTermMemoryCard taggedCard(
            int suffix, UUID petId, String entity, String location) {
        return new LongTermMemoryCard(
                new UUID(0L, suffix), petId, 1, "compact-card-" + suffix,
                MemoryImportance.HIGH, Set.of("happy"), Set.of(entity), Set.of(location), true);
    }

    private static final class CapturingEmbeddingModel implements EmbeddingModelClient {
        private final List<String> inputs = new ArrayList<>();

        @Override
        public String model() {
            return "test-embedding";
        }

        @Override
        public EmbeddingVector embed(String normalizedText) {
            inputs.add(normalizedText);
            return new EmbeddingVector(model(), new float[]{1.0f, 0.0f});
        }
    }

    private static final class FailingEmbeddingModel implements EmbeddingModelClient {
        @Override
        public String model() {
            return "failing-embedding";
        }

        @Override
        public EmbeddingVector embed(String normalizedText) {
            throw new IllegalStateException("simulated provider outage");
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            if (!ZoneOffset.UTC.equals(zone)) {
                throw new IllegalArgumentException("Only UTC is supported by this test clock");
            }
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
