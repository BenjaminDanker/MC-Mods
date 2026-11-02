package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.service.memory.InMemoryLongTermMemoryStore;
import com.silver.aipets.service.memory.LongTermMemoryCard;
import com.silver.aipets.service.memory.MemoryImportance;
import com.silver.aipets.service.vector.EmbeddingModelClient;
import com.silver.aipets.service.vector.EmbeddingVector;
import com.silver.aipets.service.vector.InMemoryEmbeddingJobStore;
import com.silver.aipets.service.vector.InMemoryVectorMemoryRepository;
import com.silver.aipets.service.vector.MemoryEmbeddingWorker;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class PetEmbeddingPostResponseWorkTest {
    @Test
    void nextPetPromptWaitsForExistingEmbeddingAndQdrantCompletion() throws Exception {
        Instant now = Instant.parse("2026-09-27T12:00:00Z");
        UUID pet = UUID.randomUUID();
        InMemoryLongTermMemoryStore memories = new InMemoryLongTermMemoryStore();
        LongTermMemoryCard card = new LongTermMemoryCard(UUID.randomUUID(), pet, 1,
                "Owner is building a bridge", MemoryImportance.HIGH, Set.of(), Set.of(),
                Set.of(), true, now, com.silver.aipets.service.memory.MemoryProvenance.OWNER_STATEMENT);
        memories.put(card);
        CountDownLatch embeddingStarted = new CountDownLatch(1);
        CountDownLatch releaseEmbedding = new CountDownLatch(1);
        EmbeddingModelClient model = new EmbeddingModelClient() {
            @Override public String model() { return "test-embedding"; }
            @Override public EmbeddingVector embed(String text) {
                embeddingStarted.countDown();
                try {
                    if (!releaseEmbedding.await(2, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("test embedding gate timed out");
                    }
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(interrupted);
                }
                return new EmbeddingVector(model(), new float[]{1.0f, 0.0f});
            }
        };
        MemoryEmbeddingWorker worker = new MemoryEmbeddingWorker(memories,
                new InMemoryEmbeddingJobStore(), model, new InMemoryVectorMemoryRepository(),
                Clock.fixed(now, ZoneOffset.UTC), UUID::randomUUID, 3,
                Duration.ofSeconds(5), Duration.ofMillis(10));
        var background = Executors.newSingleThreadExecutor();
        try {
            var postWork = new PetEmbeddingPostResponseWork(memories, () -> worker,
                    background, Duration.ofSeconds(3));
            UUID owner = UUID.randomUUID();
            UUID requestId = UUID.randomUUID();
            PetDialogueWireRequest request = new PetDialogueWireRequest(requestId,
                    UUID.randomUUID(), owner, pet, new BackendId("test"),
                    "minecraft:overworld", "Did you see the bridge?");
            PetDialogueWireResult response = new PetDialogueWireResult(requestId,
                    request.sessionId(), pet, PetDialogueWireResult.Status.SUCCEEDED, "Yes.");
            var consolidation = postWork.afterCommit(request, response).toCompletableFuture();
            assertTrue(embeddingStarted.await(2, TimeUnit.SECONDS));

            var queue = new PetInteractionQueue<String, String>(3,
                    Duration.ofSeconds(2), Runnable::run);
            AtomicBoolean nextStarted = new AtomicBoolean();
            var first = queue.submit(pet, "first", input ->
                    java.util.concurrent.CompletableFuture.completedFuture(
                            new PetInteractionQueue.Interaction<>(
                                    java.util.concurrent.CompletableFuture.completedFuture(input), consolidation)));
            var next = queue.submit(pet, "next", input -> {
                nextStarted.set(true);
                return java.util.concurrent.CompletableFuture.completedFuture(
                        new PetInteractionQueue.Interaction<>(
                                java.util.concurrent.CompletableFuture.completedFuture(input),
                                java.util.concurrent.CompletableFuture.completedFuture(null)));
            });
            assertEquals("first", first.response().get(1, TimeUnit.SECONDS));
            assertFalse(nextStarted.get());
            assertFalse(next.response().isDone());

            releaseEmbedding.countDown();
            consolidation.get(2, TimeUnit.SECONDS);
            assertEquals("next", next.response().get(2, TimeUnit.SECONDS));
            assertTrue(nextStarted.get());
            assertEquals("READY", memories.embeddingStatus(card.memoryId()));
            queue.close();
        } finally {
            releaseEmbedding.countDown();
            background.shutdownNow();
        }
    }
}
