package com.silver.viewextend;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/** Numeric worker-preparation telemetry. It deliberately never retains a chunk, tag, or packet. */
final class PreparationMetrics {
    private static final int HISTOGRAM_BUCKETS = 32;

    private final LongAdder attempts = new LongAdder();
    private final LongAdder successes = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final LongAdder successfulNanos = new LongAdder();
    private final LongAdder validationNanos = new LongAdder();
    private final LongAdder copyNanos = new LongAdder();
    private final LongAdder transformNanos = new LongAdder();
    private final LongAdder parseNanos = new LongAdder();
    private final LongAdder lightNanos = new LongAdder();
    private final LongAdder packetNanos = new LongAdder();
    private final LongAdder packetSectionNanos = new LongAdder();
    private final LongAdder packetEncodingNanos = new LongAdder();
    private final LongAdder packetDecodeNanos = new LongAdder();
    private final LongAdder sectionCopyBytesEliminated = new LongAdder();
    private final LongAdder packetBufferGrowths = new LongAdder();
    private final LongAdder sectionsProcessed = new LongAdder();
    private final LongAdder lightLayersFastPathed = new LongAdder();
    private final LongAdder lightBytesScanAvoided = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();
    private final AtomicLong[] histogram = new AtomicLong[HISTOGRAM_BUCKETS];

    PreparationMetrics() {
        for (int index = 0; index < histogram.length; index++) histogram[index] = new AtomicLong();
    }

    void recordSuccess(long total, long validation, long copy, long transform, long parse, long light, long packet,
            long packetSection, long packetEncoding, long packetDecode, long eliminatedSectionCopyBytes,
            boolean packetBufferGrew, int sections, int fastPathedLayers, long scanBytesAvoided) {
        attempts.increment();
        successes.increment();
        totalNanos.add(total);
        successfulNanos.add(total);
        validationNanos.add(validation);
        copyNanos.add(copy);
        transformNanos.add(transform);
        parseNanos.add(parse);
        lightNanos.add(light);
        packetNanos.add(packet);
        packetSectionNanos.add(packetSection);
        packetEncodingNanos.add(packetEncoding);
        packetDecodeNanos.add(packetDecode);
        sectionCopyBytesEliminated.add(eliminatedSectionCopyBytes);
        if (packetBufferGrew) packetBufferGrowths.increment();
        sectionsProcessed.add(sections);
        lightLayersFastPathed.add(fastPathedLayers);
        lightBytesScanAvoided.add(scanBytesAvoided);
        maxNanos.accumulateAndGet(total, Math::max);
        histogram[histogramBucket(total)].incrementAndGet();
    }

    void recordFailure(long total) {
        attempts.increment();
        failures.increment();
        totalNanos.add(total);
        maxNanos.accumulateAndGet(total, Math::max);
        histogram[histogramBucket(total)].incrementAndGet();
    }

    Snapshot drainSnapshot() {
        long[] buckets = new long[HISTOGRAM_BUCKETS];
        long sampleCount = 0;
        for (int index = 0; index < buckets.length; index++) {
            buckets[index] = histogram[index].getAndSet(0);
            sampleCount += buckets[index];
        }
        return new Snapshot(attempts.sumThenReset(), successes.sumThenReset(), failures.sumThenReset(),
                totalNanos.sumThenReset(), successfulNanos.sumThenReset(), validationNanos.sumThenReset(), copyNanos.sumThenReset(), transformNanos.sumThenReset(),
                parseNanos.sumThenReset(), lightNanos.sumThenReset(), packetNanos.sumThenReset(),
                packetSectionNanos.sumThenReset(), packetEncodingNanos.sumThenReset(),
                packetDecodeNanos.sumThenReset(), sectionCopyBytesEliminated.sumThenReset(),
                packetBufferGrowths.sumThenReset(),
                sectionsProcessed.sumThenReset(), lightLayersFastPathed.sumThenReset(),
                lightBytesScanAvoided.sumThenReset(), maxNanos.getAndSet(0), buckets, sampleCount);
    }

    private static int histogramBucket(long nanos) {
        long micros = Math.max(1L, nanos / 1_000L);
        return Math.min(HISTOGRAM_BUCKETS - 1, 63 - Long.numberOfLeadingZeros(micros));
    }

    record Snapshot(long attempts, long successes, long failures, long totalNanos, long successfulNanos, long validationNanos, long copyNanos,
            long transformNanos, long parseNanos, long lightNanos, long packetNanos,
            long packetSectionNanos, long packetEncodingNanos, long packetDecodeNanos,
            long sectionCopyBytesEliminated, long packetBufferGrowths,
            long sectionsProcessed, long lightLayersFastPathed, long lightBytesScanAvoided,
            long maxNanos, long[] histogram, long sampleCount) {
        long averageNanos() { return successes == 0 ? 0 : successfulNanos / successes; }
        long percentileNanos(double percentile) {
            if (sampleCount == 0) return 0;
            long target = Math.max(1L, (long) Math.ceil(sampleCount * percentile));
            long seen = 0;
            for (int index = 0; index < histogram.length; index++) {
                seen += histogram[index];
                if (seen >= target) return (1L << index) * 1_000L;
            }
            return (1L << (histogram.length - 1)) * 1_000L;
        }
    }
}
