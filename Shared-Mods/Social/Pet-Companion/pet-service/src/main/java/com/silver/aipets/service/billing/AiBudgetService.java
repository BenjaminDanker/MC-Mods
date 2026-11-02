package com.silver.aipets.service.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Period-aware AI budget boundary shared by dialogue, consolidation, and embeddings. */
public interface AiBudgetService {
    Optional<Reservation> tryReserve(UUID ownerUuid, BigDecimal maximumCost, Instant now);

    void settle(Reservation reservation, BigDecimal actualCost);

    AiBudgetSnapshot snapshot(UUID ownerUuid, Instant now);

    record Reservation(UUID ownerUuid, String periodKey, BigDecimal maximumCost) {
        public Reservation {
            if (ownerUuid == null || periodKey == null || maximumCost == null || maximumCost.signum() < 0) {
                throw new IllegalArgumentException("Invalid budget reservation");
            }
        }
    }

    AiBudgetService UNLIMITED = new AiBudgetService() {
        @Override
        public Optional<Reservation> tryReserve(UUID ownerUuid, BigDecimal maximumCost, Instant now) {
            return Optional.of(new Reservation(ownerUuid, "unlimited", maximumCost));
        }

        @Override
        public void settle(Reservation reservation, BigDecimal actualCost) {
            // No-op compatibility implementation for unit tests and disabled billing.
        }

        @Override
        public AiBudgetSnapshot snapshot(UUID ownerUuid, Instant now) {
            return new AiBudgetSnapshot(
                    new BigDecimal("0.00"), BigDecimal.ZERO, BigDecimal.ZERO, null, null);
        }
    };
}
