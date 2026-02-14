package com.silver.spawnprotect.mixin;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevent non-player entities from modifying blocks in protected spawn bounds.
 *
 * This blocks mob grief vectors like enderman pickup/place and wither terrain breaking.
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = "canModifyAt", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$preventMobGriefInProtectedBounds(ServerWorld world, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof ServerPlayerEntity) {
            return;
        }

        if (SpawnProtectionManager.INSTANCE.isWithinProtectedBounds(world, pos)) {
            cir.setReturnValue(false);
        }
    }
}
