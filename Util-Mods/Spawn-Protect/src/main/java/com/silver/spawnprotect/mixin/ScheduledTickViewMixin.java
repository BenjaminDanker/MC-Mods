package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.ticks.TickPriority;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ScheduledTickAccess.class)
public interface ScheduledTickViewMixin {

    @Inject(method = "scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/Fluid;I)V", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$cancelScheduledFluidTick(BlockPos pos, Fluid fluid, int delay, CallbackInfo ci) {
        if ((Object) this instanceof ServerLevel world && SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "scheduleTick(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/material/Fluid;ILnet/minecraft/world/ticks/TickPriority;)V", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$cancelScheduledFluidTickWithPriority(BlockPos pos, Fluid fluid, int delay, TickPriority priority, CallbackInfo ci) {
        if ((Object) this instanceof ServerLevel world && SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, pos)) {
            ci.cancel();
        }
    }
}
