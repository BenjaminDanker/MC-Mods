package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.explosion.ExplosionImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ExplosionImpl.class)
public abstract class ExplosionMixin {

    @Inject(method = "destroyBlocks", at = @At("HEAD"))
    private void spawnprotect$filterProtectedBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        var serverWorld = ((ExplosionImpl) (Object) this).getWorld();
        blocks.removeIf(pos -> SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos));
    }
}
