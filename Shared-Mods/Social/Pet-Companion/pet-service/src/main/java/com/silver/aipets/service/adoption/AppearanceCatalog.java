package com.silver.aipets.service.adoption;

import com.silver.aipets.common.domain.PetSpecies;
import com.silver.aipets.common.domain.ResourceId;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Valid registry identifiers observed in the target Minecraft version. */
public final class AppearanceCatalog {
    private final Map<PetSpecies, List<ResourceId>> variants;

    public AppearanceCatalog(Map<PetSpecies, List<ResourceId>> variants) {
        Objects.requireNonNull(variants, "variants");
        EnumMap<PetSpecies, List<ResourceId>> copy = new EnumMap<>(PetSpecies.class);
        for (PetSpecies species : PetSpecies.values()) {
            List<ResourceId> configured = List.copyOf(
                    Objects.requireNonNull(variants.get(species), "Missing variants for " + species));
            if (configured.isEmpty()) {
                throw new IllegalArgumentException("At least one variant is required for " + species);
            }
            if (configured.stream().distinct().count() != configured.size()) {
                throw new IllegalArgumentException("Duplicate variant configured for " + species);
            }
            copy.put(species, configured);
        }
        if (copy.size() != variants.size()) {
            throw new IllegalArgumentException("Appearance catalog contains an unknown species");
        }
        this.variants = Map.copyOf(copy);
    }

    public static AppearanceCatalog vanilla12110() {
        return new AppearanceCatalog(Map.of(
                PetSpecies.CAT, resourceIds(
                        "minecraft:tabby",
                        "minecraft:jellie",
                        "minecraft:black",
                        "minecraft:persian",
                        "minecraft:ragdoll",
                        "minecraft:red",
                        "minecraft:siamese",
                        "minecraft:calico",
                        "minecraft:all_black",
                        "minecraft:british_shorthair",
                        "minecraft:white"),
                PetSpecies.DOG, resourceIds(
                        "minecraft:woods",
                        "minecraft:chestnut",
                        "minecraft:spotted",
                        "minecraft:ashen",
                        "minecraft:rusty",
                        "minecraft:striped",
                        "minecraft:snowy",
                        "minecraft:pale",
                        "minecraft:black")));
    }

    public List<ResourceId> variantsFor(PetSpecies species) {
        return variants.get(Objects.requireNonNull(species, "species"));
    }

    private static List<ResourceId> resourceIds(String... values) {
        return java.util.Arrays.stream(values).map(ResourceId::parse).toList();
    }
}
