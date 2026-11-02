package com.silver.aipets.service.dialogue;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Function;
import java.util.function.BiFunction;

/** In-memory FIFO with one active operation and a bounded number of waiting inputs per pet. */
public final class PetInteractionQueue<I, O> implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(PetInteractionQueue.class.getName());
    private final int maximumWaiting;
    private final Duration backgroundTimeout;
    private final Executor executor;
    private final Map<UUID, PetQueue> queues = new HashMap<>();
    private boolean closed;

    public PetInteractionQueue(int maximumWaiting, Duration backgroundTimeout, Executor executor) {
        if (maximumWaiting < 0 || maximumWaiting > 3) {
            throw new IllegalArgumentException("maximumWaiting must be in [0,3]");
        }
        this.maximumWaiting = maximumWaiting;
        this.backgroundTimeout = Objects.requireNonNull(backgroundTimeout, "backgroundTimeout");
        if (backgroundTimeout.isZero() || backgroundTimeout.isNegative()) {
            throw new IllegalArgumentException("backgroundTimeout must be positive");
        }
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public Submission<O> submit(UUID petId, I input,
            Function<I, CompletionStage<Interaction<O>>> processor) {
        Objects.requireNonNull(processor, "processor");
        return submitWithQueueStatus(petId, input, (value, queued) -> processor.apply(value));
    }

    public Submission<O> submitWithQueueStatus(UUID petId, I input,
            BiFunction<I, Boolean, CompletionStage<Interaction<O>>> processor) {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(processor, "processor");
        Entry entry;
        boolean start = false;
        synchronized (this) {
            if (closed) return rejected("Pet interaction service is shutting down.");
            PetQueue queue = queues.computeIfAbsent(petId, ignored -> new PetQueue());
            if (!queue.active) {
                entry = new Entry(input, processor, false);
                queue.active = true;
                queue.current = entry;
                start = true;
            } else if (queue.waiting.size() < maximumWaiting) {
                entry = new Entry(input, processor, true);
                queue.waiting.addLast(entry);
            } else {
                return rejected("Your pet is still processing. Please try again shortly.");
            }
        }
        if (start) start(petId, entry);
        return new Submission<>(true, entry.response);
    }

    public synchronized int waiting(UUID petId) {
        PetQueue queue = queues.get(petId);
        return queue == null ? 0 : queue.waiting.size();
    }

    public synchronized boolean active(UUID petId) {
        PetQueue queue = queues.get(petId);
        return queue != null && queue.active;
    }

    @Override
    public void close() {
        ArrayDeque<Entry> dropped = new ArrayDeque<>();
        synchronized (this) {
            if (closed) return;
            closed = true;
            for (PetQueue queue : queues.values()) {
                dropped.addAll(queue.waiting);
                queue.waiting.clear();
            }
            queues.entrySet().removeIf(entry -> !entry.getValue().active);
        }
        dropped.forEach(entry -> entry.response.completeExceptionally(
                new QueueClosedException("Queued interaction dropped during shutdown")));
    }

    private void start(UUID petId, Entry entry) {
        int waitingCount = waiting(petId);
        LOGGER.log(System.Logger.Level.INFO,
                "dialogue_queue_start pet_id={0} queued={1} queue_wait_ms={2} waiting_after_start={3}",
                petId, entry.queued, (System.nanoTime() - entry.submittedNanos) / 1_000_000,
                waitingCount);
        try {
            executor.execute(() -> {
                CompletionStage<Interaction<O>> stage;
                try {
                    stage = Objects.requireNonNull(entry.processor.apply(entry.input, entry.queued),
                            "interaction processor returned null");
                } catch (Throwable failure) {
                    finish(petId, entry, failure);
                    return;
                }
                stage.whenComplete((interaction, failure) -> {
                    if (failure != null || interaction == null) {
                        finish(petId, entry, failure == null
                                ? new IllegalStateException("interaction returned null") : failure);
                        return;
                    }
                    interaction.response().whenComplete((response, responseFailure) -> {
                        if (responseFailure == null) entry.response.complete(response);
                        else entry.response.completeExceptionally(responseFailure);
                    });
                    interaction.completion().toCompletableFuture()
                            .orTimeout(backgroundTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                            .whenComplete((ignored, backgroundFailure) ->
                                    finish(petId, entry, backgroundFailure));
                });
            });
        } catch (RejectedExecutionException rejected) {
            finish(petId, entry, rejected);
        }
    }

    private void finish(UUID petId, Entry entry, Throwable failure) {
        Entry next = null;
        synchronized (this) {
            PetQueue queue = queues.get(petId);
            if (queue == null || queue.current != entry) return;
            queue.current = null;
            if (!closed && !queue.waiting.isEmpty()) {
                next = queue.waiting.removeFirst();
                queue.current = next;
            } else {
                queue.active = false;
                queues.remove(petId);
            }
        }
        if (!entry.response.isDone()) {
            entry.response.completeExceptionally(failure == null
                    ? new IllegalStateException("Interaction finalized without a response") : failure);
        }
        if (next != null) start(petId, next);
    }

    private Submission<O> rejected(String message) {
        return new Submission<>(false, CompletableFuture.failedFuture(new QueueFullException(message)));
    }

    public record Submission<O>(boolean accepted, CompletableFuture<O> response) {
        public Submission { Objects.requireNonNull(response); }
    }

    /** Response may be sent immediately; completion gates the next prompt for this pet. */
    public record Interaction<O>(CompletionStage<O> response, CompletionStage<Void> finalized) {
        public Interaction {
            Objects.requireNonNull(response, "response");
            Objects.requireNonNull(finalized, "finalized");
        }

        /** Completion covers synchronous commit work plus all chained post-response work. */
        public CompletionStage<Void> completion() { return finalized; }
    }

    public static final class QueueFullException extends RuntimeException {
        public QueueFullException(String message) { super(message); }
    }

    public static final class QueueClosedException extends RuntimeException {
        public QueueClosedException(String message) { super(message); }
    }

    private final class Entry {
        private final I input;
        private final BiFunction<I, Boolean, CompletionStage<Interaction<O>>> processor;
        private final boolean queued;
        private final long submittedNanos = System.nanoTime();
        private final CompletableFuture<O> response = new CompletableFuture<>();
        private Entry(I input, BiFunction<I, Boolean, CompletionStage<Interaction<O>>> processor,
                boolean queued) {
            this.input = input;
            this.processor = processor;
            this.queued = queued;
        }
    }

    private final class PetQueue {
        private boolean active;
        private Entry current;
        private final ArrayDeque<Entry> waiting = new ArrayDeque<>();
    }
}
