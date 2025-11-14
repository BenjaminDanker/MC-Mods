package com.silver.enderfight.mixin;

import com.silver.enderfight.duck.NoiseChunkGeneratorExtension;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin implements NoiseChunkGeneratorExtension {
    @Unique
    private NoiseConfig endfight$customNoiseConfig;

    @Override
    public void endfight$setCustomNoiseConfig(NoiseConfig config) {
        this.endfight$customNoiseConfig = config;
    }

    @Override
    public NoiseConfig endfight$getCustomNoiseConfig() {
        return this.endfight$customNoiseConfig;
    }

    @ModifyVariable(method = "populateBiomes", at = @At("HEAD"), argsOnly = true)
    private NoiseConfig endfight$swapPopulateBiomesConfig(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "populateNoise", at = @At("HEAD"), argsOnly = true)
    private NoiseConfig endfight$swapPopulateNoiseConfig(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V", at = @At("HEAD"), argsOnly = true)
    private NoiseConfig endfight$swapBuildSurfaceConfig1(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "buildSurface(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/HeightContext;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/biome/source/BiomeAccess;Lnet/minecraft/registry/Registry;Lnet/minecraft/world/gen/chunk/Blender;)V", at = @At("HEAD"), argsOnly = true)
    private NoiseConfig endfight$swapBuildSurfaceConfig2(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "carve", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private NoiseConfig endfight$swapCarveConfig(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "getHeight", at = @At("HEAD"), argsOnly = true)
    private NoiseConfig endfight$swapGetHeightConfig(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "getColumnSample", at = @At("HEAD"), argsOnly = true)
    private NoiseConfig endfight$swapGetColumnSampleConfig(NoiseConfig original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

}
