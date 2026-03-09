package com.silver.skyislands.giantmobs.mixins;

import com.silver.skyislands.giantmobs.GiantMobManager;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerChunkLoadingManager;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerChunkLoadingManager.class)
public class ServerChunkLoadingManagerMixin {

    @Inject(method = "unloadEntity", at = @At("HEAD"), cancellable = true)
    private void keepManagedGiantsTracked(Entity entity, CallbackInfo ci) {
        if (GiantMobManager.shouldPreventUnload(entity) && !entity.isRemoved()) {
            ci.cancel();
        }
    }
}