package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.construct.UndoFluidTickGuard;
import net.minecraft.block.BlockState;
import net.minecraft.fluid.FlowableFluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FlowableFluid.class)
public abstract class FlowableFluidMixin {

    @Inject(method = "onScheduledTick", at = @At("HEAD"), cancellable = true)
    private void atlantis$cancelFluidTickInsideActiveUndoBounds(ServerWorld world, BlockPos pos, BlockState blockState, FluidState state, CallbackInfo ci) {
        if (UndoFluidTickGuard.INSTANCE.shouldSuppress(world, pos)) {
            ci.cancel();
        }
    }
}
