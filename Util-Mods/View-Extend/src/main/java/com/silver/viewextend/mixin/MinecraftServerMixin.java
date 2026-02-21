package com.silver.viewextend.mixin;

import com.silver.viewextend.ViewExtendMod;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void viewextend$onServerTick(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (ViewExtendMod.getService() != null) {
            ViewExtendMod.getService().tick((MinecraftServer) (Object) this);
        }
    }

    @Inject(method = "shutdown", at = @At("HEAD"))
    private void viewextend$onServerShutdown(CallbackInfo ci) {
        if (ViewExtendMod.getService() != null) {
            ViewExtendMod.getService().shutdown();
        }
    }
}
