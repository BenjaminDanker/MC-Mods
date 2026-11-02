package com.silver.aipets.service.vector;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.service.metrics.PetOperationalMetrics;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-worker bounded embedding pump; provider failures remain in job retry state. */
public final class EmbeddingScheduler implements AutoCloseable {
    private final MemoryEmbeddingWorker worker;
    private final String workerId;
    private final int batchSize;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final PetOperationalMetrics metrics;

    public EmbeddingScheduler(
            MemoryEmbeddingWorker worker, int batchSize, Duration interval, String workerId) {
        this(worker, batchSize, interval, workerId, new PetOperationalMetrics());
    }

    public EmbeddingScheduler(
            MemoryEmbeddingWorker worker, int batchSize, Duration interval, String workerId,
            PetOperationalMetrics metrics) {
        this.worker = Objects.requireNonNull(worker, "worker");
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batchSize must be between 1 and 100");
        }
        this.batchSize = batchSize;
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.workerId = workerId == null || workerId.isBlank()
                ? "embedding-" + UUID.randomUUID() : workerId;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        if (this.workerId.length() > 191) {
            throw new IllegalArgumentException("workerId is too long");
        }
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "pet-service-embedding-scheduler");
            thread.setDaemon(false);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executor.scheduleWithFixedDelay(
                this::processSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
    }

    private void processSafely() {
        try {
            int processed = worker.processDue(workerId, batchSize);
            if (processed > 0) {
                System.getLogger(EmbeddingScheduler.class.getName()).log(
                        System.Logger.Level.INFO,
                        StructuredPetEvent.operation("embedding_due_scan")
                                .count(processed).outcome("processed").toJson());
            }
        } catch (RuntimeException failure) {
            metrics.increment(PetOperationalMetrics.Counter.EMBEDDING_FAILURES);
            System.getLogger(EmbeddingScheduler.class.getName()).log(
                    System.Logger.Level.ERROR,
                    StructuredPetEvent.operation("embedding_due_scan")
                            .failure(failure).outcome("retry").toJson());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
