package com.silver.enderfight.mixin;

import com.silver.enderfight.duck.NoiseChunkGeneratorExtension;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin implements NoiseChunkGeneratorExtension {
    @Unique
    private RandomState endfight$customNoiseConfig;

    @Override
    public void endfight$setCustomNoiseConfig(RandomState config) {
        this.endfight$customNoiseConfig = config;
    }

    @Override
    public RandomState endfight$getCustomNoiseConfig() {
        return this.endfight$customNoiseConfig;
    }

    @ModifyVariable(method = "createBiomes", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapPopulateBiomesConfig(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "fillFromNoise", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapPopulateNoiseConfig(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "buildSurface(Lnet/minecraft/server/level/WorldGenRegion;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/chunk/ChunkAccess;)V", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapBuildSurfaceConfig1(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "buildSurface(Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/levelgen/WorldGenerationContext;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/StructureManager;Lnet/minecraft/world/level/biome/BiomeManager;Lnet/minecraft/world/level/levelgen/blending/Blender;Ljava/util/Set;)V", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapBuildSurfaceConfig2(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "applyCarvers", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapCarveConfig(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "getBaseHeight", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapGetHeightConfig(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

    @ModifyVariable(method = "getBaseColumn", at = @At("HEAD"), argsOnly = true)
    private RandomState endfight$swapGetColumnSampleConfig(RandomState original) {
        return this.endfight$customNoiseConfig != null ? this.endfight$customNoiseConfig : original;
    }

}
