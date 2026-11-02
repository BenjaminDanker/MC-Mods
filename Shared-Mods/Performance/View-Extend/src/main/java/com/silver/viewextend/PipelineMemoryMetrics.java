package com.silver.viewextend;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Thread-safe numeric gauges for NBT ownership. Never retains an NBT or packet object. */
final class PipelineMemoryMetrics {
    private final AtomicLong rawCount = new AtomicLong();
    private final AtomicLong rawBytes = new AtomicLong();
    private final AtomicLong peakRawCount = new AtomicLong();
    private final AtomicLong peakRawBytes = new AtomicLong();
    private final LongAdder measuredCount = new LongAdder();
    private final LongAdder measuredBytes = new LongAdder();
    private final AtomicLong maxRawBytes = new AtomicLong();

    private final AtomicLong queuedTasks = new AtomicLong();
    private final AtomicLong activeTasks = new AtomicLong();
    private final AtomicLong queuedRawBytes = new AtomicLong();
    private final AtomicLong activeRawBytes = new AtomicLong();
    private final AtomicLong peakQueuedTasks = new AtomicLong();
    private final AtomicLong peakActiveTasks = new AtomicLong();
    private final AtomicLong copyingNbt = new AtomicLong();
    private final AtomicLong peakCopyingNbt = new AtomicLong();

    Lease retain(int bytes, boolean hasNbt) {
        int safeBytes = Math.max(0, bytes);
        if (hasNbt) {
            measuredCount.increment();
            measuredBytes.add(safeBytes);
            maxRawBytes.accumulateAndGet(safeBytes, Math::max);
            updatePeak(peakRawCount, rawCount.incrementAndGet());
            updatePeak(peakRawBytes, rawBytes.addAndGet(safeBytes));
            queuedRawBytes.addAndGet(safeBytes);
        }
        updatePeak(peakQueuedTasks, queuedTasks.incrementAndGet());
        return new Lease(this, safeBytes, hasNbt);
    }

    void copyStarted() { updatePeak(peakCopyingNbt, copyingNbt.incrementAndGet()); }
    void copyFinished() { copyingNbt.decrementAndGet(); }

    Snapshot snapshot() {
        long count = measuredCount.sum();
        long bytes = measuredBytes.sum();
        return new Snapshot(rawCount.get(), rawBytes.get(), count, bytes,
                maxRawBytes.get(), peakRawCount.get(), peakRawBytes.get(), queuedTasks.get(),
                activeTasks.get(), peakQueuedTasks.get(), peakActiveTasks.get(),
                queuedRawBytes.get(), activeRawBytes.get(), copyingNbt.get(), peakCopyingNbt.get());
    }

    private static void updatePeak(AtomicLong peak, long value) {
        peak.accumulateAndGet(value, Math::max);
    }

    static final class Lease {
        private final PipelineMemoryMetrics owner;
        private final int bytes;
        private final boolean hasNbt;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(PipelineMemoryMetrics owner, int bytes, boolean hasNbt) {
            this.owner = owner;
            this.bytes = bytes;
            this.hasNbt = hasNbt;
        }

        void start() {
            if (!started.compareAndSet(false, true)) return;
            owner.queuedTasks.decrementAndGet();
            owner.updatePeak(owner.peakActiveTasks, owner.activeTasks.incrementAndGet());
            if (hasNbt) {
                owner.queuedRawBytes.addAndGet(-bytes);
                owner.activeRawBytes.addAndGet(bytes);
            }
        }

        void release() {
            if (!released.compareAndSet(false, true)) return;
            if (started.get()) {
                owner.activeTasks.decrementAndGet();
                if (hasNbt) owner.activeRawBytes.addAndGet(-bytes);
            } else {
                owner.queuedTasks.decrementAndGet();
                if (hasNbt) owner.queuedRawBytes.addAndGet(-bytes);
            }
            if (hasNbt) {
                owner.rawCount.decrementAndGet();
                owner.rawBytes.addAndGet(-bytes);
            }
        }
    }

    record Snapshot(
            long rawCount,
            long rawBytes,
            long measuredCount,
            long measuredBytes,
            long maxRawBytes,
            long peakRawCount,
            long peakRawBytes,
            long queuedTasks,
            long activeTasks,
            long peakQueuedTasks,
            long peakActiveTasks,
            long queuedRawBytes,
            long activeRawBytes,
            long copyingNbt,
            long peakCopyingNbt) {
        long averageRawBytes() { return measuredCount == 0 ? 0 : measuredBytes / measuredCount; }
    }
}
