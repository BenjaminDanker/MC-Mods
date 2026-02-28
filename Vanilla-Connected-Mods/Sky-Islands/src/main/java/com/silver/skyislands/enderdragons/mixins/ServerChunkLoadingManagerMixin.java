package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkLoadingManager.class)
public class ServerChunkLoadingManagerMixin {

    @Inject(method = "unloadEntity", at = @At("HEAD"), cancellable = true)
    private void keepManagedDragonsTracked(Entity entity, CallbackInfo ci) {
        if (entity instanceof EnderDragonEntity dragon && EnderDragonManager.isManaged(dragon)) {
            if (!dragon.isRemoved()) {
                // Cancel the un-tracking process for our managed dragons
                // so they don't get untracked and re-tracked during chunk generation 
                // which results in visual despawns and network spam
                ci.cancel();
            }
        }
    }
}
