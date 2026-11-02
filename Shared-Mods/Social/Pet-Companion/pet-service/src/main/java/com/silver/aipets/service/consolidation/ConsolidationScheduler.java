package com.silver.aipets.service.consolidation;

import com.silver.aipets.common.observability.StructuredPetEvent;
import com.silver.aipets.service.metrics.PetOperationalMetrics;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immediate-on-start durable job pump with isolated bounded scans. */
public final class ConsolidationScheduler implements AutoCloseable {
    private final ConsolidationWorker worker;
    private final String workerId;
    private final int batchSize;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();
    private final PetOperationalMetrics metrics;

    public ConsolidationScheduler(
            ConsolidationWorker worker, String workerId, int batchSize, Duration interval) {
        this(worker, workerId, batchSize, interval, new PetOperationalMetrics());
    }

    public ConsolidationScheduler(
            ConsolidationWorker worker, String workerId, int batchSize, Duration interval,
            PetOperationalMetrics metrics) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        this.interval = Objects.requireNonNull(interval, "interval");
        if (workerId.isBlank() || workerId.length() > 191 || batchSize < 1 || batchSize > 100
                || interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("Invalid consolidation scheduler configuration");
        }
        this.batchSize = batchSize;
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pet-service-consolidation-scheduler");
            thread.setDaemon(false);
            return thread;
        };
        this.executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(
                    this::processSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void processSafely() {
        try {
            int claimed = worker.processDue(workerId, batchSize);
            if (claimed > 0) {
                metrics.add(PetOperationalMetrics.Counter.CONSOLIDATION_RUN, claimed);
                System.getLogger(ConsolidationScheduler.class.getName()).log(
                        System.Logger.Level.INFO,
                        StructuredPetEvent.operation("consolidation_scan")
                                .count(claimed).outcome("processed").toJson());
            }
        } catch (RuntimeException failure) {
            metrics.increment(PetOperationalMetrics.Counter.CONSOLIDATION_FAILED);
            System.getLogger(ConsolidationScheduler.class.getName()).log(
                    System.Logger.Level.ERROR,
                    StructuredPetEvent.operation("consolidation_scan")
                            .failure(failure).outcome("retry").toJson());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
