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
        return fullyReady(stripeConfigured, false, false);
    }

    public static PetReadinessSnapshot fullyReady(
            boolean stripeConfigured, boolean vectorConfigured, boolean modelConfigured) {
        return new PetReadinessSnapshot(
                true, "UP", "CURRENT", configured(vectorConfigured),
                configured(modelConfigured), configured(stripeConfigured));
    }

    public static PetReadinessSnapshot databaseDown() {
        return databaseDown(false);
    }

    public static PetReadinessSnapshot databaseDown(boolean stripeConfigured) {
        return databaseDown(stripeConfigured, false, false);
    }

    public static PetReadinessSnapshot databaseDown(
            boolean stripeConfigured, boolean vectorConfigured, boolean modelConfigured) {
        return new PetReadinessSnapshot(
                false, "DOWN", "UNKNOWN", configured(vectorConfigured),
                configured(modelConfigured), configured(stripeConfigured));
    }

    public static PetReadinessSnapshot migrationsOutdated() {
        return migrationsOutdated(false);
    }

    public static PetReadinessSnapshot migrationsOutdated(boolean stripeConfigured) {
        return migrationsOutdated(stripeConfigured, false, false);
    }

    public static PetReadinessSnapshot migrationsOutdated(
            boolean stripeConfigured, boolean vectorConfigured, boolean modelConfigured) {
        return new PetReadinessSnapshot(
                false, "UP", "OUTDATED", configured(vectorConfigured),
                configured(modelConfigured), configured(stripeConfigured));
    }

    private static String configured(boolean configured) {
        return configured ? "CONFIGURED" : "DEGRADED_NOT_CONFIGURED";
    }
}
