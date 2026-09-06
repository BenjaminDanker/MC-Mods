package com.silver.aipets.service.health;

import java.util.Objects;

public record PetReadinessSnapshot(
        boolean ready,
        String database,
        String migrations,
        String vector,
        String model,
        String stripe) {
    public PetReadinessSnapshot {
        Objects.requireNonNull(database, "database");
        Objects.requireNonNull(migrations, "migrations");
        Objects.requireNonNull(vector, "vector");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(stripe, "stripe");
    }

    public static PetReadinessSnapshot fullyReady() {
        return fullyReady(false);
    }

    public static PetReadinessSnapshot fullyReady(boolean stripeConfigured) {
        return new PetReadinessSnapshot(
                true, "UP", "CURRENT", "DEGRADED_NOT_CONFIGURED",
                "DEGRADED_NOT_CONFIGURED", stripe(stripeConfigured));
    }

    public static PetReadinessSnapshot databaseDown() {
        return databaseDown(false);
    }

    public static PetReadinessSnapshot databaseDown(boolean stripeConfigured) {
        return new PetReadinessSnapshot(
                false, "DOWN", "UNKNOWN", "DEGRADED_NOT_CONFIGURED",
                "DEGRADED_NOT_CONFIGURED", stripe(stripeConfigured));
    }

    public static PetReadinessSnapshot migrationsOutdated() {
        return migrationsOutdated(false);
    }

    public static PetReadinessSnapshot migrationsOutdated(boolean stripeConfigured) {
        return new PetReadinessSnapshot(
                false, "UP", "OUTDATED", "DEGRADED_NOT_CONFIGURED",
                "DEGRADED_NOT_CONFIGURED", stripe(stripeConfigured));
    }

    private static String stripe(boolean configured) {
        return configured ? "CONFIGURED" : "DEGRADED_NOT_CONFIGURED";
    }
}
