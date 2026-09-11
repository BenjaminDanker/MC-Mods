package com.silver.atlantis.mixin;

import com.silver.atlantis.AtlantisMod;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @Shadow
    private int idleTickCount;

    @Inject(method = "tick(Ljava/util/function/BooleanSupplier;)V", at = @At("HEAD"))
    private void atlantis$preventEmptyPause(BooleanSupplier shouldKeepTicking, CallbackInfo ci) {
        if (AtlantisMod.shouldKeepServerTicking((MinecraftServer) (Object) this)) {
            // Vanilla increments this counter before deciding to skip the
            // world tick. Reset it while Atlantis has work to perform so the
            // normal pause-when-empty countdown cannot reach its threshold.
            idleTickCount = 0;
        }
    }
}
