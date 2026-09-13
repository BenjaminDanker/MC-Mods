package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.protect.ProtectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent fire from destroying protected blocks.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "checkBurnOut", at = @At("HEAD"), cancellable = true)
    private void atlantis$preventBurning(
        Level world,
        BlockPos pos,
        int spreadFactor,
        RandomSource random,
        int currentAge,
        CallbackInfo ci
    ) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }

        if (ProtectionManager.INSTANCE.isAnyProtected(serverWorld, pos)) {
            ci.cancel();
        }
    }
}
