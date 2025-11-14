package com.silver.enderfight.duck;

import net.minecraft.world.gen.noise.NoiseConfig;

/**
 * Duck interface injected into {@link net.minecraft.world.gen.chunk.NoiseChunkGenerator} to allow
 * Ender Fight to supply a custom {@link NoiseConfig} backed by the daily seed.
 */
public interface NoiseChunkGeneratorExtension {
    void endfight$setCustomNoiseConfig(NoiseConfig config);

    NoiseConfig endfight$getCustomNoiseConfig();
}
