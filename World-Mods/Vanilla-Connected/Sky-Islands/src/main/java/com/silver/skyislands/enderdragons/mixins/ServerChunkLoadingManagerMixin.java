package com.silver.skyislands.enderdragons.mixins;

import com.silver.skyislands.enderdragons.EnderDragonManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkCache.class)
public class ServerChunkLoadingManagerMixin {

    @Inject(method = "removeEntity", at = @At("HEAD"), cancellable = true)
    private void keepManagedDragonsTracked(Entity entity, CallbackInfo ci) {
        if (entity instanceof EnderDragon dragon && EnderDragonManager.isManaged(dragon)) {
            if (!dragon.isRemoved()) {
                // Cancel the un-tracking process for our managed dragons
                // so they don't get untracked and re-tracked during chunk generation 
                // which results in visual despawns and network spam
                ci.cancel();
            }
        }
    }
}
