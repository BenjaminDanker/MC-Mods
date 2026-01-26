package com.benjamin.skyislands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.biome.v1.ModificationPhase;
import net.minecraft.util.Identifier;
import net.minecraft.entity.EntityType;

public class SkyIslands implements ModInitializer {
    @Override
    public void onInitialize() {
        // Remove all natural creeper spawns from every biome using a modification callback
        BiomeModifications.create(Identifier.of("sky_islands", "no_creepers"))
                .add(ModificationPhase.REPLACEMENTS, BiomeSelectors.all(), context -> {
                    context.getSpawnSettings().removeSpawnsOfEntityType(EntityType.CREEPER);
                });
    }
}
