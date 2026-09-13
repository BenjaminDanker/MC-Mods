package com.silver.skyislands.giantmobs.mixins;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(FallingBlockEntity.class)
public interface FallingBlockEntityInvoker {
    @Invoker("<init>")
    static FallingBlockEntity skyIslands$create(Level world, double x, double y, double z, BlockState blockState) {
        throw new AssertionError();
    }
}