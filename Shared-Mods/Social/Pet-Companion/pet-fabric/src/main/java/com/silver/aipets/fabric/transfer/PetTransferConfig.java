package com.silver.aipets.fabric.transfer;

import java.time.Duration;
import java.util.Objects;

/** Cross-backend carry policy; placement state remains authoritative in the central service. */
public record PetTransferConfig(double carryRadius, Duration reservationLifetime) {
    public PetTransferConfig {
        if (!Double.isFinite(carryRadius) || carryRadius <= 0.0 || carryRadius > 64.0) {
            throw new IllegalArgumentException("carryRadius must be finite and in (0, 64]");
        }
        Objects.requireNonNull(reservationLifetime, "reservationLifetime");
        if (reservationLifetime.compareTo(Duration.ofSeconds(10)) < 0
                || reservationLifetime.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("reservationLifetime must be between 10 seconds and 15 minutes");
        }
    }

    public static PetTransferConfig defaults() {
        return new PetTransferConfig(16.0, Duration.ofMinutes(5));
    }
}
