package com.silver.aipets.service.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

/** Read-only period budget projection used by HTTP and Minecraft status views. */
public record AiBudgetSnapshot(
        BigDecimal budgetUsd,
        BigDecimal consumedUsd,
        BigDecimal remainingUsd,
        Instant periodStart,
        Instant periodEnd) {
    public AiBudgetSnapshot {
        Objects.requireNonNull(budgetUsd, "budgetUsd");
        Objects.requireNonNull(consumedUsd, "consumedUsd");
        Objects.requireNonNull(remainingUsd, "remainingUsd");
        if (budgetUsd.signum() < 0 || consumedUsd.signum() < 0 || remainingUsd.signum() < 0) {
            throw new IllegalArgumentException("Budget values cannot be negative");
        }
    }
}
