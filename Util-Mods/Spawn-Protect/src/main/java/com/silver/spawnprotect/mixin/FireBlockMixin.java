package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$preventBurning(
        BlockState state,
        ServerLevel world,
        BlockPos pos,
        RandomSource random,
        CallbackInfo ci
    ) {
        if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, pos)) {
            ci.cancel();
        }
    }
}
