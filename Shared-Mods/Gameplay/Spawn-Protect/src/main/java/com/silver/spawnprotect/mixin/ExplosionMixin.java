package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void spawnprotect$filterProtectedBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        var serverWorld = ((ServerExplosion) (Object) this).level();
        blocks.removeIf(pos -> SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(serverWorld, pos));
    }
}
