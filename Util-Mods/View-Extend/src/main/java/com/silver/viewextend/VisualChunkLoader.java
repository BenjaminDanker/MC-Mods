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
    record Result(PreparedVisualChunk prepared, boolean missing, Throwable error, long workerNanos) {}
    private final VisualChunkPreparer preparer;
    private final ThreadPoolExecutor workers;

    VisualChunkLoader(ViewExtendConfig config) {
        preparer = new VisualChunkPreparer(config);
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
        // Timeout our dependent future, never the IO worker's possibly shared read future.
        return diskRead.thenApplyAsync(nbt -> {
            if (nbt.isEmpty()) return new Result(null, true, null, 0);
            long started = System.nanoTime();
            try {
                PreparedVisualChunk prepared = preparer.prepare(pos, lod, nbt.get(), context);
                return new Result(prepared, false, null, System.nanoTime() - started);
            } catch (RuntimeException error) {
                return new Result(null, false, error, System.nanoTime() - started);
            }
        }, workers).orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(error -> new Result(null, false, VisualChunkFailure.unwrap(error), 0));
    }

    double pressure() {
        return (double) (workers.getActiveCount() + workers.getQueue().size())
                / Math.max(1, workers.getMaximumPoolSize() * 2);
    }
    @Override public void close() { workers.shutdownNow(); }
}
