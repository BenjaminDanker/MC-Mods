package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.protect.ProtectionManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;

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
    private void atlantis$blockProtectedPush(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }

        if (!(level instanceof ServerLevel serverWorld)) {
            return;
        }

        // If any moved/broken block is protected, or if any moved block would land in protected interior, cancel.
        for (BlockPos pos : toPush) {
            if (ProtectionManager.INSTANCE.isAnyProtected(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }

            BlockPos to = pos.relative(pushDirection);
            if (ProtectionManager.INSTANCE.isPlaceProtected(serverWorld, to)) {
                cir.setReturnValue(false);
                return;
            }
        }

        for (BlockPos pos : toDestroy) {
            if (ProtectionManager.INSTANCE.isAnyProtected(serverWorld, pos)) {
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
