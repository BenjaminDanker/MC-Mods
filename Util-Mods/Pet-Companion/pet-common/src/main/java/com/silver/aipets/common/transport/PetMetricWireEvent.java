package com.silver.aipets.common.transport;

import java.util.Objects;

/**
 * A bounded, low-cardinality operational metric event sent by a Minecraft backend.
 * The enum deliberately contains only counters that are produced outside the central service.
 */
public record PetMetricWireEvent(Metric metric, long amount) {
    public enum Metric {
        DUPLICATE_ENTITY_DISCARDS,
        STALE_ENTITY_DISCARDS,
        TRANSFER_AUTO_PICKUP_FAILURES,
        TRANSFER_AUTO_PLACE_FAILURES
    }

    public PetMetricWireEvent {
        Objects.requireNonNull(metric, "metric");
        if (amount < 1 || amount > 1_000) {
            throw new IllegalArgumentException("metric amount must be in 1..1000");
        }
    }
}
