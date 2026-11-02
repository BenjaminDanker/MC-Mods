package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.protect.ProtectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;

@Mixin(ServerExplosion.class)
public abstract class ExplosionMixin {

    @Inject(method = "interactWithBlocks", at = @At("HEAD"))
    private void atlantis$filterProtectedBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        // ExplosionImpl is server-side; getWorld() returns ServerWorld.
        var serverWorld = ((ServerExplosion) (Object) this).level();
        blocks.removeIf(pos -> ProtectionManager.INSTANCE.isAnyProtected(serverWorld, pos));
    }
}
