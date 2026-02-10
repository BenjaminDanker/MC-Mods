package com.silver.enderfight.mixin;

import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.world.chunk.ChunkGenerationContext;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkLoadingManagerAccessor {
    @Accessor("generationContext")
    ChunkGenerationContext getGenerationContext();

    @Accessor("generationContext")
    @Mutable
    void setGenerationContext(ChunkGenerationContext context);

    @Accessor("noiseConfig")
    NoiseConfig getNoiseConfig();

    @Accessor("noiseConfig")
    @Mutable
    void setNoiseConfig(NoiseConfig noiseConfig);

    @Accessor("structurePlacementCalculator")
    StructurePlacementCalculator getStructurePlacementCalculator();

    @Accessor("structurePlacementCalculator")
    @Mutable
    void setStructurePlacementCalculator(StructurePlacementCalculator calculator);
}
