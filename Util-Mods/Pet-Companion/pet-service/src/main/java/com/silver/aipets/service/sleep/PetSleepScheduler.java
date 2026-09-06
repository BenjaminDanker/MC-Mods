package com.silver.aipets.service.sleep;

import com.silver.aipets.common.observability.StructuredPetEvent;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Single-threaded bounded due-state pump; failures are isolated from HTTP and later scans. */
public final class PetSleepScheduler implements AutoCloseable {
    private final PetSleepService service;
    private final int batchSize;
    private final Duration interval;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public PetSleepScheduler(PetSleepService service, int batchSize, Duration interval) {
        this.service = Objects.requireNonNull(service, "service");
        if (batchSize < 1 || batchSize > 1_000) {
            throw new IllegalArgumentException("batchSize must be between 1 and 1000");
        }
        this.batchSize = batchSize;
        this.interval = Objects.requireNonNull(interval, "interval");
        if (interval.isZero() || interval.isNegative()) {
            throw new IllegalArgumentException("interval must be positive");
        }
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "pet-service-sleep-scheduler");
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
            int changed = service.processDue(batchSize).size();
            if (changed > 0) {
                System.getLogger(PetSleepScheduler.class.getName()).log(
                        System.Logger.Level.INFO,
                        StructuredPetEvent.operation("sleep_due_scan")
                                .count(changed).outcome("advanced").toJson());
            }
        } catch (RuntimeException failure) {
            System.getLogger(PetSleepScheduler.class.getName()).log(
                    System.Logger.Level.ERROR,
                    StructuredPetEvent.operation("sleep_due_scan")
                            .failure(failure).outcome("retry").toJson());
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
