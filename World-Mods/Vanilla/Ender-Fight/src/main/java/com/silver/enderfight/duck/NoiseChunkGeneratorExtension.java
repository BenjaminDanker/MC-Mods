package com.silver.enderfight.duck;

import net.minecraft.world.level.levelgen.RandomState;

/**
 * Duck interface injected into {@link net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator} to allow
 * Ender Fight to supply a custom {@link RandomState} backed by the daily seed.
 */
public interface NoiseChunkGeneratorExtension {
    void endfight$setCustomNoiseConfig(RandomState config);

    RandomState endfight$getCustomNoiseConfig();
}
