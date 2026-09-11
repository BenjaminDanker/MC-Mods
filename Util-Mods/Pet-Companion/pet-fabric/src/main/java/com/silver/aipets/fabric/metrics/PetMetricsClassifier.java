package com.silver.aipets.fabric.metrics;

import com.silver.aipets.common.authority.EntityAuthorityDecision;
import com.silver.aipets.common.transport.PetMetricWireEvent;
import com.silver.aipets.fabric.transfer.PetTransferStatus;

import java.util.Optional;

/** Pure mapping from bounded Fabric outcomes to the central metric wire vocabulary. */
public final class PetMetricsClassifier {
    private PetMetricsClassifier() { }

    public static PetMetricWireEvent.Metric entityDiscard(EntityAuthorityDecision decision) {
        return decision == EntityAuthorityDecision.DISCARD_ENTITY_UUID_MISMATCH
                ? PetMetricWireEvent.Metric.DUPLICATE_ENTITY_DISCARDS
                : PetMetricWireEvent.Metric.STALE_ENTITY_DISCARDS;
    }

    public static PetMetricWireEvent.Metric missingEntityDiscard() {
        return PetMetricWireEvent.Metric.STALE_ENTITY_DISCARDS;
    }

    public static Optional<PetMetricWireEvent.Metric> transferFailure(
            boolean source, PetTransferStatus status) {
        if (source) {
            return status == PetTransferStatus.SERVICE_FAILURE
                    || status == PetTransferStatus.AUTHORITY_REJECTED
                    ? Optional.of(PetMetricWireEvent.Metric.TRANSFER_AUTO_PICKUP_FAILURES)
                    : Optional.empty();
        }
        return switch (status) {
            case SERVICE_FAILURE, AUTHORITY_REJECTED, DESTINATION_NO_SAFE_POSITION,
                    DESTINATION_SPAWN_FAILED_COMPENSATED, DESTINATION_SPAWN_FAILED_UNRESOLVED ->
                    Optional.of(PetMetricWireEvent.Metric.TRANSFER_AUTO_PLACE_FAILURES);
            default -> Optional.empty();
        };
    }
}
