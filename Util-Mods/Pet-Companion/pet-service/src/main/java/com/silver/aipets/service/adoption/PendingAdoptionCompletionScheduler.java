package com.silver.aipets.service.adoption;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Startup scan plus small periodic retry; persisted intent rows remain authoritative. */
public final class PendingAdoptionCompletionScheduler implements AutoCloseable {
    private final PendingAdoptionCompletionWorker worker;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "pet-pending-adoption-completion");
                thread.setDaemon(false);
                return thread;
            });
    private final AtomicBoolean started = new AtomicBoolean();

    public PendingAdoptionCompletionScheduler(PendingAdoptionCompletionWorker worker) {
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::runSafely, 0, 10, TimeUnit.SECONDS);
        }
    }

    private void runSafely() {
        try {
            worker.processBatch();
        } catch (RuntimeException failure) {
            System.getLogger(PendingAdoptionCompletionScheduler.class.getName()).log(
                    System.Logger.Level.ERROR,
                    "Pending adoption retry failed; it will be retried on the next scan", failure);
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
