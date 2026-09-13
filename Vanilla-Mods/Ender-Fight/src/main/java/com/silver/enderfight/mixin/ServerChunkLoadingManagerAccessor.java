package com.silver.enderfight.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChunkMap.class)
public interface ServerChunkLoadingManagerAccessor {
    @Accessor("worldGenContext")
    WorldGenContext getGenerationContext();

    @Accessor("worldGenContext")
    @Mutable
    void setGenerationContext(WorldGenContext context);

    @Accessor("randomState")
    RandomState getNoiseConfig();

    @Accessor("randomState")
    @Mutable
    void setNoiseConfig(RandomState noiseConfig);

    @Accessor("chunkGeneratorState")
    ChunkGeneratorStructureState getStructurePlacementCalculator();

    @Accessor("chunkGeneratorState")
    @Mutable
    void setStructurePlacementCalculator(ChunkGeneratorStructureState calculator);
}
