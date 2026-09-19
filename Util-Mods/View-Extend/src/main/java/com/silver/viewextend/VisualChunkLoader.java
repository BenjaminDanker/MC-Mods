package com.silver.viewextend;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import com.silver.viewextend.VisualChunkPreparer.LoadContext;
import com.silver.viewextend.VisualChunkPreparer.PreparedVisualChunk;

/** Bounded worker pipeline. Every read/prepare request produces exactly one terminal result. */
final class VisualChunkLoader implements AutoCloseable {
    record Result(PreparedVisualChunk prepared, boolean missing, Throwable error, long workerNanos,
            int rawNbtBytes) {}
    private final VisualChunkPreparer preparer;
    private final ThreadPoolExecutor workers;
    private final PipelineMemoryMetrics memoryMetrics = new PipelineMemoryMetrics();
    private final PreparationMetrics preparationMetrics = new PreparationMetrics();

    VisualChunkLoader(ViewExtendConfig config) {
        preparer = new VisualChunkPreparer(config, memoryMetrics, preparationMetrics);
        int count = config.workerThreads() > 0 ? config.workerThreads()
                : Math.max(2, Math.min(16, Runtime.getRuntime().availableProcessors() / 2));
        AtomicInteger threadNumber = new AtomicInteger();
        workers = new ThreadPoolExecutor(count, count, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.preparedQueueHardLimit()), runnable -> {
                    Thread thread = new Thread(runnable, "viewextend-preprocess-" + threadNumber.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
        workers.allowCoreThreadTimeOut(true);
    }

    CompletableFuture<Result> load(CompletableFuture<Optional<CompoundTag>> diskRead,
            ChunkPos pos, int lod, LoadContext context) {
        CompletableFuture<Result> terminal = new CompletableFuture<>();
        // Measure once when the disk future hands ownership to the bounded worker queue.
        diskRead.whenComplete((nbt, diskError) -> {
            if (diskError != null) {
                terminal.complete(new Result(null, false, VisualChunkFailure.unwrap(diskError), 0, 0));
                return;
            }
            boolean hasNbt = nbt.isPresent();
            int rawBytes;
            try {
                rawBytes = hasNbt ? nbt.get().sizeInBytes() : 0;
            } catch (RuntimeException measurementError) {
                terminal.complete(new Result(null, false, measurementError, 0, 0));
                return;
            }
            PipelineMemoryMetrics.Lease lease = memoryMetrics.retain(rawBytes, hasNbt);
            MeasuredTask task = new MeasuredTask(lease, () -> {
                if (!hasNbt) return new Result(null, true, null, 0, 0);
                long started = System.nanoTime();
                try {
                    PreparedVisualChunk prepared = preparer.prepare(pos, lod, nbt.get(), context);
                    return new Result(prepared, false, null, System.nanoTime() - started, rawBytes);
                } catch (RuntimeException error) {
                    return new Result(null, false, error, System.nanoTime() - started, rawBytes);
                }
            }, terminal);
            try {
                workers.execute(task);
            } catch (RuntimeException rejected) {
                task.reject(rejected);
            }
        });
        // Timeout only our result, never Minecraft's original storage future or worker task.
        return terminal.orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(error -> new Result(null, false, VisualChunkFailure.unwrap(error), 0, 0));
    }

    double pressure() {
        return (double) (workers.getActiveCount() + workers.getQueue().size())
                / Math.max(1, workers.getMaximumPoolSize() * 2);
    }
    PipelineMemoryMetrics.Snapshot memorySnapshot() { return memoryMetrics.snapshot(); }
    PreparationMetrics.Snapshot drainPreparationMetrics() { return preparationMetrics.drainSnapshot(); }

    @Override public void close() {
        for (Runnable queued : workers.shutdownNow()) {
            if (queued instanceof MeasuredTask task) task.releaseWithoutRunning();
        }
    }

    private static final class MeasuredTask implements Runnable {
        private final PipelineMemoryMetrics.Lease lease;
        private final java.util.function.Supplier<Result> operation;
        private final CompletableFuture<Result> result;

        private MeasuredTask(PipelineMemoryMetrics.Lease lease,
                java.util.function.Supplier<Result> operation, CompletableFuture<Result> result) {
            this.lease = lease;
            this.operation = operation;
            this.result = result;
        }

        @Override public void run() {
            lease.start();
            try {
                result.complete(operation.get());
            } catch (Throwable error) {
                result.complete(new Result(null, false, VisualChunkFailure.unwrap(error), 0, 0));
            } finally {
                lease.release();
            }
        }

        void reject(Throwable error) {
            lease.release();
            result.complete(new Result(null, false, VisualChunkFailure.unwrap(error), 0, 0));
        }

        void releaseWithoutRunning() { lease.release(); }
    }
}
