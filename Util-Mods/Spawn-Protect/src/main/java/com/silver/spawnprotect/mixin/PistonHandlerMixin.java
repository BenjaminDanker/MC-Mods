package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.block.piston.PistonHandler;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PistonHandler.class)
public abstract class PistonHandlerMixin {

    @Shadow
    @Final
    private World world;

    @Shadow
    @Final
    private Direction motionDirection;

    @Shadow
    @Final
    private List<BlockPos> movedBlocks;

    @Shadow
    @Final
    private List<BlockPos> brokenBlocks;

    @Inject(method = "calculatePush", at = @At("RETURN"), cancellable = true)
    private void spawnprotect$blockProtectedPush(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }

        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        for (BlockPos pos : movedBlocks) {
            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }

            BlockPos destination = pos.offset(motionDirection);
            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, destination)) {
                cir.setReturnValue(false);
                return;
            }
        }

        for (BlockPos pos : brokenBlocks) {
            if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
