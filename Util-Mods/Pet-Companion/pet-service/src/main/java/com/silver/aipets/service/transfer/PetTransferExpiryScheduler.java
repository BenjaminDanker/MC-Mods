package com.silver.aipets.service.transfer;

import com.silver.aipets.common.observability.StructuredPetEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Isolated bounded transfer recovery pump; a failed scan is retried on the next interval. */
public final class PetTransferExpiryScheduler implements AutoCloseable {
    private final PetTransferExpiryWorker worker;
    private final int batchSize;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public PetTransferExpiryScheduler(
            PetTransferExpiryWorker worker, int batchSize, Duration interval) {
        this.worker = Objects.requireNonNull(worker, "worker");
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        this.batchSize = batchSize;
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "pet-service-transfer-expiry");
            thread.setDaemon(false);
            return thread;
        });
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(
                    this::processSafely, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void processSafely() {
        try {
            PetTransferExpiryRun run = worker.processDue(batchSize);
            if (run.expired() > 0) {
                System.getLogger(PetTransferExpiryScheduler.class.getName()).log(
                        System.Logger.Level.INFO,
                        StructuredPetEvent.operation("transfer_expiry_scan")
                                .count(run.expired()).outcome("returned_to_held").toJson());
            }
        } catch (RuntimeException failure) {
            System.getLogger(PetTransferExpiryScheduler.class.getName()).log(
                    System.Logger.Level.ERROR,
                    StructuredPetEvent.operation("transfer_expiry_scan")
                            .failure(failure).outcome("retry").toJson());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
