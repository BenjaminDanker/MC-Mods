package com.silver.aipets.service.retention;

import com.silver.aipets.common.observability.StructuredPetEvent;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Hourly bounded pump; the daily unique job makes restarts and duplicate ticks harmless. */
public final class RetentionCleanupScheduler implements AutoCloseable {
    private final RetentionCleanupWorker worker;
    private final RetentionPolicy policy;
    private final String workerId;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean started = new AtomicBoolean();

    public RetentionCleanupScheduler(
            RetentionCleanupWorker worker, RetentionPolicy policy, String workerId) {
        this.worker = Objects.requireNonNull(worker, "worker");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.workerId = Objects.requireNonNull(workerId, "workerId");
        if (workerId.isBlank() || workerId.length() > 191) throw new IllegalArgumentException("Invalid workerId");
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "pet-service-retention-cleanup");
            thread.setDaemon(false); return thread;
        };
        executor = Executors.newSingleThreadScheduledExecutor(factory);
    }

    public void start() {
        if (started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(this::runSafely, 0,
                    policy.scheduleInterval().toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private void runSafely() {
        try {
            worker.ensureDailyJob();
            worker.processDue(workerId, 1);
        } catch (RuntimeException failure) {
            System.getLogger(RetentionCleanupScheduler.class.getName()).log(
                    System.Logger.Level.ERROR,
                    StructuredPetEvent.operation("retention_cleanup_scan")
                            .failure(failure).outcome("retry").toJson());
        }
    }

    @Override public void close() { executor.shutdownNow(); }
}
