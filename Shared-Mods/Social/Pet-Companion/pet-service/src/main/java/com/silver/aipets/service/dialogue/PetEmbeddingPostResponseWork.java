package com.silver.aipets.service.dialogue;

import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.service.memory.LongTermMemoryStore;
import com.silver.aipets.service.vector.MemoryEmbeddingWorker;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Chains this pet's durable pending memory vectors into its interaction finalization. */
public final class PetEmbeddingPostResponseWork implements DialoguePostResponseWork {
    private final LongTermMemoryStore memories;
    private final Supplier<MemoryEmbeddingWorker> workers;
    private final Executor executor;
    private final Duration maximumWait;

    public PetEmbeddingPostResponseWork(LongTermMemoryStore memories,
            Supplier<MemoryEmbeddingWorker> workers, Executor executor, Duration maximumWait) {
        this.memories = Objects.requireNonNull(memories, "memories");
        this.workers = Objects.requireNonNull(workers, "workers");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.maximumWait = Objects.requireNonNull(maximumWait, "maximumWait");
        if (maximumWait.isZero() || maximumWait.isNegative()) {
            throw new IllegalArgumentException("maximumWait must be positive");
        }
    }

    @Override
    public CompletionStage<Void> afterCommit(PetDialogueWireRequest request,
            PetDialogueWireResult result) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(result, "result");
        if (result.status() != PetDialogueWireResult.Status.SUCCEEDED) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(() -> finishPetVectors(
                request.petId(), request.requestId()), executor);
    }

    private void finishPetVectors(UUID petId, UUID requestId) {
        long deadline = System.nanoTime() + maximumWait.toNanos();
        MemoryEmbeddingWorker worker = Objects.requireNonNull(workers.get(),
                "embedding worker is not initialized");
        String workerId = "dialogue-post-" + requestId;
        do {
            var pending = memories.listPendingEmbeddingsForPet(petId, 100).cards();
            if (pending.isEmpty()) return;
            pending.forEach(worker::enqueue); // Existing scheduler jobs are found idempotently.
            worker.processDueForPet(petId, workerId, 100);
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Pet memory embedding finalization timed out");
            }
            try {
                TimeUnit.MILLISECONDS.sleep(100);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Pet memory finalization interrupted", interrupted);
            }
        } while (true);
    }
}
