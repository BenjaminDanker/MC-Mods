package com.silver.enderfight.mixin;

import net.minecraft.world.RandomSequences;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RandomSequences.class)
public interface RandomSequencesStateAccessor {
    @Accessor("seed")
    long getSeed();

    @Accessor("seed")
    @Mutable
    void setSeed(long seed);
}
