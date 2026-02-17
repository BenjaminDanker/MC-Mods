package com.silver.atlantis.worldgen;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.PlacedFeature;

public final class AtlantisWorldgen {
    private static final Identifier DRIFTWOOD_FEATURE_ID = Identifier.of(AtlantisMod.MOD_ID, "ocean_driftwood_feature");
    private static final Identifier DRIFTWOOD_PLACED_ID = Identifier.of(AtlantisMod.MOD_ID, "ocean_driftwood_placed");

    public static final RegistryKey<PlacedFeature> OCEAN_DRIFTWOOD_PLACED_KEY =
        RegistryKey.of(RegistryKeys.PLACED_FEATURE, DRIFTWOOD_PLACED_ID);

    private static boolean registered;

    private AtlantisWorldgen() {
    }

    public static void register() {
        if (registered) {
            AtlantisMod.LOGGER.info("[Atlantis][worldgen] register() called again; skipping duplicate registration");
            return;
        }

        AtlantisMod.LOGGER.info("[Atlantis][worldgen] registering ocean driftwood feature + biome injection");

        Feature<DefaultFeatureConfig> driftwoodFeature = Registry.register(
            net.minecraft.registry.Registries.FEATURE,
            DRIFTWOOD_FEATURE_ID,
            new AtlantisDriftwoodFeature(DefaultFeatureConfig.CODEC)
        );

        BiomeModifications.addFeature(
            BiomeSelectors.includeByKey(
                BiomeKeys.OCEAN,
                BiomeKeys.DEEP_OCEAN,
                BiomeKeys.COLD_OCEAN,
                BiomeKeys.DEEP_COLD_OCEAN,
                BiomeKeys.LUKEWARM_OCEAN,
                BiomeKeys.DEEP_LUKEWARM_OCEAN,
                BiomeKeys.WARM_OCEAN,
                BiomeKeys.FROZEN_OCEAN,
                BiomeKeys.DEEP_FROZEN_OCEAN
            ),
            GenerationStep.Feature.VEGETAL_DECORATION,
            OCEAN_DRIFTWOOD_PLACED_KEY
        );

        AtlantisMod.LOGGER.info(
            "[Atlantis][worldgen] registered: featureId={} placedKey={} generationStep={}",
            DRIFTWOOD_FEATURE_ID,
            OCEAN_DRIFTWOOD_PLACED_KEY.getValue(),
            GenerationStep.Feature.VEGETAL_DECORATION
        );
        registered = true;
    }
}
