package com.silver.viewextend.mixin;

import com.silver.viewextend.ViewExtendMod;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonNetworkHandler.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void viewextend$maybeCancelUnload(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof UnloadChunkS2CPacket unloadPacket)) {
            return;
        }
        Object self = this;
        if (!(self instanceof ServerPlayNetworkHandler playHandler)) {
            return;
        }
        if (ViewExtendMod.getService() == null) {
            return;
        }

        if (ViewExtendMod.getService().shouldSuppressVanillaUnload(playHandler.player, unloadPacket.pos())) {
            ci.cancel();
            return;
        }

        ViewExtendMod.getService().onLikelyClientUnload(playHandler.player, unloadPacket.pos());
    }
}