package com.silver.aipets.common.transport;

import java.util.Objects;
import java.util.Set;

/** Bounded facts sampled by the owning Fabric server when a conversation is submitted. */
public record PetSceneSnapshot(
        String petBiome, String ownerBiome, String proximity,
        boolean ownerVisible, String ownerActivity, String ownerHealth,
        String petActivity, String weather, boolean nearbyThreat,
        java.util.Optional<SpatialLocation> location) {
    public PetSceneSnapshot(String petBiome, String ownerBiome, String proximity,
            boolean ownerVisible, String ownerActivity, String ownerHealth,
            String petActivity, String weather, boolean nearbyThreat) {
        this(petBiome, ownerBiome, proximity, ownerVisible, ownerActivity, ownerHealth,
                petActivity, weather, nearbyThreat, java.util.Optional.empty());
    }
    private static final Set<String> PROXIMITY = Set.of("TOUCHING", "NEAR", "IN_SIGHT", "FAR", "UNKNOWN");
    private static final Set<String> ACTIVITY = Set.of("STILL", "MOVING", "USING_ITEM",
            "RECENTLY_HURT", "UNKNOWN");
    private static final Set<String> HEALTH = Set.of("HEALTHY", "HURT", "CRITICAL", "UNKNOWN");
    private static final Set<String> WEATHER = Set.of("CLEAR", "RAIN", "THUNDER", "UNKNOWN");

    public PetSceneSnapshot {
        Objects.requireNonNull(location, "location");
        petBiome = biome(petBiome);
        ownerBiome = biome(ownerBiome);
        if (!PROXIMITY.contains(proximity) || !ACTIVITY.contains(ownerActivity)
                || !HEALTH.contains(ownerHealth) || !WEATHER.contains(weather)) {
            throw new IllegalArgumentException("Invalid scene category");
        }
        Objects.requireNonNull(petActivity, "petActivity");
        if (!petActivity.matches("[A-Z_]{3,32}")) {
            throw new IllegalArgumentException("Invalid pet activity");
        }
    }

    public static PetSceneSnapshot unknown() {
        return new PetSceneSnapshot("unknown", "unknown", "UNKNOWN", false,
                "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", false);
    }

    private static String biome(String value) {
        Objects.requireNonNull(value, "biome");
        if (value.equals("unknown")) return value;
        if (value.length() > 128 || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("Invalid biome identifier");
        }
        return value;
    }
}
