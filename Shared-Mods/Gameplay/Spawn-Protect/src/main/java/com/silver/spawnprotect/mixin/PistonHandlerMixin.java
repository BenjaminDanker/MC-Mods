package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public abstract class PistonHandlerMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private Direction pushDirection;

    @Shadow
    @Final
    private List<BlockPos> toPush;

    @Shadow
    @Final
    private List<BlockPos> toDestroy;

    @Inject(method = "resolve", at = @At("RETURN"), cancellable = true)
    private void spawnprotect$blockProtectedPush(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }

        if (!(level instanceof ServerLevel serverWorld)) {
            return;
        }

        for (BlockPos pos : toPush) {
            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }

            BlockPos destination = pos.relative(pushDirection);
            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, destination)) {
                cir.setReturnValue(false);
                return;
            }
        }

        for (BlockPos pos : toDestroy) {
            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
