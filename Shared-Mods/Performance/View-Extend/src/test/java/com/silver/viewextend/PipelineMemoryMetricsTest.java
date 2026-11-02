package com.silver.viewextend;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PipelineMemoryMetricsTest {
    @Test void nbtLeaseMovesThroughQueueActiveAndReleaseExactlyOnce() {
        var metrics = new PipelineMemoryMetrics();
        var first = metrics.retain(1200, true);
        var second = metrics.retain(800, true);
        var queued = metrics.snapshot();
        assertEquals(2, queued.rawCount());
        assertEquals(2000, queued.rawBytes());
        assertEquals(2, queued.queuedTasks());
        assertEquals(2000, queued.queuedRawBytes());

        first.start();
        var active = metrics.snapshot();
        assertEquals(1, active.queuedTasks());
        assertEquals(1, active.activeTasks());
        assertEquals(800, active.queuedRawBytes());
        assertEquals(1200, active.activeRawBytes());

        first.release();
        first.release();
        second.release(); // cancellation before the task starts
        var released = metrics.snapshot();
        assertEquals(0, released.rawCount());
        assertEquals(0, released.rawBytes());
        assertEquals(0, released.queuedTasks());
        assertEquals(0, released.activeTasks());
        assertEquals(2, released.peakRawCount());
        assertEquals(2000, released.peakRawBytes());
        assertEquals(1000, released.averageRawBytes());
        assertEquals(1200, released.maxRawBytes());
    }

    @Test void emptyReadCountsAsPreparationTaskButNotRawNbt() {
        var metrics = new PipelineMemoryMetrics();
        var lease = metrics.retain(0, false);
        assertEquals(1, metrics.snapshot().queuedTasks());
        assertEquals(0, metrics.snapshot().rawCount());
        lease.start();
        lease.release();
        assertEquals(0, metrics.snapshot().activeTasks());
    }

    @Test void copyConcurrencyGaugeReturnsToZero() {
        var metrics = new PipelineMemoryMetrics();
        metrics.copyStarted();
        metrics.copyStarted();
        metrics.copyFinished();
        metrics.copyFinished();
        assertEquals(0, metrics.snapshot().copyingNbt());
        assertEquals(2, metrics.snapshot().peakCopyingNbt());
    }
}
