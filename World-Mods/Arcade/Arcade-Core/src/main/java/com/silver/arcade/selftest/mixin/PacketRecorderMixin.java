package com.silver.arcade.selftest.mixin;

import com.silver.arcade.selftest.PacketLog;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Self-test packet recorder: every packet sent to a player passes through here; PacketLog keeps those sent to the
 * test bots, and only while a self-test runs (a single volatile read otherwise).
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class PacketRecorderMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"))
    private void arcade$selftestRecord(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        if (PacketLog.active && (Object) this instanceof ServerGamePacketListenerImpl game) {
            PacketLog.record(game.player, packet);
        }
    }
}
