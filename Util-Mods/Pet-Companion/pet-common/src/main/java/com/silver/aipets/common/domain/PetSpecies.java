package com.silver.aipets.common.domain;

import java.util.Arrays;

/** The complete first-release species allowlist. */
public enum PetSpecies {
    CAT("minecraft:cat", "cat"),
    DOG("minecraft:wolf", "dog");

    private final ResourceId entityTypeId;
    private final String displayName;

    PetSpecies(String entityTypeId, String displayName) {
        this.entityTypeId = ResourceId.parse(entityTypeId);
        this.displayName = displayName;
    }

    public ResourceId entityTypeId() {
        return entityTypeId;
    }

    public String displayName() {
        return displayName;
    }

    public static PetSpecies fromEntityTypeId(ResourceId entityTypeId) {
        java.util.Objects.requireNonNull(entityTypeId, "entityTypeId");
        return Arrays.stream(values())
                .filter(species -> species.entityTypeId.equals(entityTypeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Entity type is not in the pet species allowlist: " + entityTypeId));
    }

    public static PetSpecies fromEntityTypeId(String entityTypeId) {
        return fromEntityTypeId(ResourceId.parse(entityTypeId));
    }
}
