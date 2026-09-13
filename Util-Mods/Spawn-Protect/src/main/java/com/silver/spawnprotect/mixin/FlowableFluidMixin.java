package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowingFluid.class)
public abstract class FlowableFluidMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$cancelFluidTickInsideProtectedBounds(ServerLevel world, BlockPos pos, BlockState blockState, FluidState state, CallbackInfo ci) {
        if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, pos)) {
            ci.cancel();
        }
    }
}
