package com.silver.atlantis.protect.mixin;

import com.silver.atlantis.protect.ProtectionManager;
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
    private void atlantis$filterProtectedBlocks(List<BlockPos> blocks, CallbackInfo ci) {
        // ExplosionImpl is server-side; getWorld() returns ServerWorld.
        var serverWorld = ((ExplosionImpl) (Object) this).getWorld();
        blocks.removeIf(pos -> ProtectionManager.INSTANCE.isAnyProtected(serverWorld, pos));
    }
}
