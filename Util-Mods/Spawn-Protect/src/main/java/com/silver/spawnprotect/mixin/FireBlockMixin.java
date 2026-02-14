package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.block.FireBlock;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireBlock.class)
public abstract class FireBlockMixin {

    @Inject(method = "trySpreadingFire", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$preventBurning(
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

        if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos)) {
            ci.cancel();
        }
    }
}
