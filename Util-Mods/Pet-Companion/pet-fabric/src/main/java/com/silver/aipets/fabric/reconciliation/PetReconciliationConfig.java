package com.silver.aipets.fabric.reconciliation;

public record PetReconciliationConfig(int intervalTicks, int maximumChecksPerTick) {
    public PetReconciliationConfig {
        if (intervalTicks < 20) {
            throw new IllegalArgumentException("intervalTicks must be at least one second");
        }
        if (maximumChecksPerTick < 1 || maximumChecksPerTick > 100) {
            throw new IllegalArgumentException("maximumChecksPerTick must be in 1..100");
        }
    }

    public static PetReconciliationConfig defaults() {
        return new PetReconciliationConfig(1_200, 4);
    }
}
