package com.silver.aipets.fabric.metrics;

import com.silver.aipets.common.transport.PetMetricWireEvent;

/** Non-blocking producer boundary for metrics generated on a Minecraft backend. */
@FunctionalInterface
public interface PetMetricsReporter {
    void increment(PetMetricWireEvent.Metric metric);

    static PetMetricsReporter noop() {
        return metric -> { };
    }
}
