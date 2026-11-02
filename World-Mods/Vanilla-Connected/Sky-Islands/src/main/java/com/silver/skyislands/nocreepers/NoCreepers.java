package com.silver.skyislands.nocreepers;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NoCreepers {
    private static final Logger LOGGER = LoggerFactory.getLogger(NoCreepers.class);
    private static final EntityType<?> CREEPER_TYPE = BuiltInRegistries.ENTITY_TYPE
            .getValue(Identifier.fromNamespaceAndPath("minecraft", "creeper"));

    private NoCreepers() {
    }

    public static void init() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][nocreepers] init(): registering biome spawn removal");
        }
        BiomeModifications.create(Identifier.fromNamespaceAndPath("sky_islands", "no_creepers"))
                .add(ModificationPhase.REPLACEMENTS, BiomeSelectors.all(), context ->
                        context.getMobSpawnSettings().removeSpawnsOfEntityType(CREEPER_TYPE));
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][nocreepers] init(): registration complete");
        }
    }
}
