package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.protect.ProtectionManager;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevent fire from destroying protected blocks.
 */
@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void atlantis$preventBurning(
        World world,
        BlockPos pos,
        int spreadFactor,
        Random random,
        int currentAge,
        CallbackInfo ci
    ) {
        if (!(world instanceof ServerWorld serverWorld)) {
            return;
        }

        if (ProtectionManager.INSTANCE.isAnyProtected(serverWorld, pos)) {
            ci.cancel();
        }
    }
}
