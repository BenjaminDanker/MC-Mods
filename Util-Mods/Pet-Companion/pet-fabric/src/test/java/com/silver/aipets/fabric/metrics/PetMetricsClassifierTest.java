package com.silver.aipets.fabric.metrics;

import com.silver.aipets.common.authority.EntityAuthorityDecision;
import com.silver.aipets.common.transport.PetMetricWireEvent;
import com.silver.aipets.fabric.transfer.PetTransferStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetMetricsClassifierTest {
    @Test
    void classifiesOnlyDuplicateStaleAndAutomaticTransferFailures() {
        assertEquals(PetMetricWireEvent.Metric.DUPLICATE_ENTITY_DISCARDS,
                PetMetricsClassifier.entityDiscard(EntityAuthorityDecision.DISCARD_ENTITY_UUID_MISMATCH));
        assertEquals(PetMetricWireEvent.Metric.STALE_ENTITY_DISCARDS,
                PetMetricsClassifier.entityDiscard(EntityAuthorityDecision.DISCARD_OWNER_MISMATCH));
        assertEquals(PetMetricWireEvent.Metric.STALE_ENTITY_DISCARDS,
                PetMetricsClassifier.missingEntityDiscard());
        assertEquals(PetMetricWireEvent.Metric.TRANSFER_AUTO_PICKUP_FAILURES,
                PetMetricsClassifier.transferFailure(true, PetTransferStatus.AUTHORITY_REJECTED).orElseThrow());
        assertEquals(PetMetricWireEvent.Metric.TRANSFER_AUTO_PLACE_FAILURES,
                PetMetricsClassifier.transferFailure(false,
                        PetTransferStatus.DESTINATION_SPAWN_FAILED_COMPENSATED).orElseThrow());
        assertTrue(PetMetricsClassifier.transferFailure(false,
                PetTransferStatus.DESTINATION_PLACED).isEmpty());
    }
}
