package com.silver.viewextend.mixin;

import com.silver.viewextend.ViewExtendMod;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheRadiusPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ServerCommonNetworkHandlerMixin {
    @Unique
    private boolean viewextend$sendingAdjustedRadius;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void viewextend$maybeCancelUnload(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        Object self = this;
        if (!(self instanceof ServerGamePacketListenerImpl playHandler)) {
            return;
        }
        if (ViewExtendMod.getService() == null) {
            return;
        }

        if (packet instanceof ClientboundRespawnPacket) {
            ViewExtendMod.getService().resetPlayer(playHandler.player);
        }
        if (packet instanceof ClientboundSetChunkCacheRadiusPacket radiusPacket
                && !this.viewextend$sendingAdjustedRadius) {
            int requiredRadius = ViewExtendMod.getService().getRequiredClientLoadDistance(playHandler.player);
            if (radiusPacket.getRadius() < requiredRadius) {
                ci.cancel();
                this.viewextend$sendingAdjustedRadius = true;
                try {
                    playHandler.send(new ClientboundSetChunkCacheRadiusPacket(requiredRadius), listener);
                } finally {
                    this.viewextend$sendingAdjustedRadius = false;
                }
                return;
            }
        }

        if (!(packet instanceof ClientboundForgetLevelChunkPacket unloadPacket)) {
            return;
        }

        if (ViewExtendMod.getService().shouldSuppressVanillaUnload(playHandler.player, unloadPacket.pos())) {
            ci.cancel();
            return;
        }

        ViewExtendMod.getService().onLikelyClientUnload(playHandler.player, unloadPacket.pos());
    }
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("TAIL"))
    private void viewextend$recordChunk(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl handler
                && packet instanceof ClientboundLevelChunkWithLightPacket chunk
                && ViewExtendMod.getService() != null) {
            ViewExtendMod.getService().onVanillaChunkSent(handler.player, chunk.getX(), chunk.getZ());
        }
    }
}
