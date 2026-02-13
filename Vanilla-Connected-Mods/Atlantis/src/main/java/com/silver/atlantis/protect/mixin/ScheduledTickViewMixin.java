package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.construct.UndoFluidTickGuard;
import net.minecraft.fluid.Fluid;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.tick.ScheduledTickView;
import net.minecraft.world.tick.TickPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScheduledTickView.class)
public interface ScheduledTickViewMixin {

    @Inject(method = "scheduleFluidTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;I)V", at = @At("HEAD"), cancellable = true)
    private void atlantis$cancelScheduledFluidTickInUndoBounds(BlockPos pos, Fluid fluid, int delay, CallbackInfo ci) {
        if ((Object) this instanceof ServerWorld world && UndoFluidTickGuard.INSTANCE.shouldSuppress(world, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleFluidTick(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/fluid/Fluid;ILnet/minecraft/world/tick/TickPriority;)V", at = @At("HEAD"), cancellable = true)
    private void atlantis$cancelScheduledFluidTickInUndoBoundsWithPriority(BlockPos pos, Fluid fluid, int delay, TickPriority priority, CallbackInfo ci) {
        if ((Object) this instanceof ServerWorld world && UndoFluidTickGuard.INSTANCE.shouldSuppress(world, pos)) {
            ci.cancel();
        }
    }
}
