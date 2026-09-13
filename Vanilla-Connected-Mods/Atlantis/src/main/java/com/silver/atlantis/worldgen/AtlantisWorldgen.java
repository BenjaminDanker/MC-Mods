package com.silver.atlantis.worldgen;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

public final class AtlantisWorldgen {
    private static final Identifier DRIFTWOOD_FEATURE_ID = Identifier.fromNamespaceAndPath(AtlantisMod.MOD_ID, "ocean_driftwood_feature");
    private static final Identifier DRIFTWOOD_PLACED_ID = Identifier.fromNamespaceAndPath(AtlantisMod.MOD_ID, "ocean_driftwood_placed");

    public static final ResourceKey<PlacedFeature> OCEAN_DRIFTWOOD_PLACED_KEY =
        ResourceKey.create(Registries.PLACED_FEATURE, DRIFTWOOD_PLACED_ID);

    private static boolean registered;

    private AtlantisWorldgen() {
    }

    public static void register() {
        if (registered) {
            AtlantisMod.LOGGER.info("[Atlantis][worldgen] register() called again; skipping duplicate registration");
            return;
        }

        AtlantisMod.LOGGER.info("[Atlantis][worldgen] registering ocean driftwood feature + biome injection");

        Feature<NoneFeatureConfiguration> driftwoodFeature = Registry.register(
            net.minecraft.core.registries.BuiltInRegistries.FEATURE,
            DRIFTWOOD_FEATURE_ID,
            new AtlantisDriftwoodFeature(NoneFeatureConfiguration.CODEC)
        );

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(
                Biomes.OCEAN,
                Biomes.DEEP_OCEAN,
                Biomes.COLD_OCEAN,
                Biomes.DEEP_COLD_OCEAN,
                Biomes.LUKEWARM_OCEAN,
                Biomes.DEEP_LUKEWARM_OCEAN,
                Biomes.WARM_OCEAN,
                Biomes.FROZEN_OCEAN,
                Biomes.DEEP_FROZEN_OCEAN
            ),
            GenerationStep.Decoration.VEGETAL_DECORATION,
            OCEAN_DRIFTWOOD_PLACED_KEY
        );

        AtlantisMod.LOGGER.info(
            "[Atlantis][worldgen] registered: featureId={} placedKey={} generationStep={}",
            DRIFTWOOD_FEATURE_ID,
            OCEAN_DRIFTWOOD_PLACED_KEY.identifier(),
            GenerationStep.Decoration.VEGETAL_DECORATION
        );
        registered = true;
    }
}
