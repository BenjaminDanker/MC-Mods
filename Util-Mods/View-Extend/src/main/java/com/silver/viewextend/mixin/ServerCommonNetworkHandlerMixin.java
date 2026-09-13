package com.silver.viewextend.mixin;

import com.silver.viewextend.ViewExtendMod;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @Inject(method = "send", at = @At("HEAD"), cancellable = true)
    private void viewextend$maybeCancelUnload(Packet<?> packet, CallbackInfo ci) {
        if (!(packet instanceof ClientboundForgetLevelChunkPacket unloadPacket)) {
            return;
        }
        Object self = this;
        if (!(self instanceof ServerGamePacketListenerImpl playHandler)) {
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
