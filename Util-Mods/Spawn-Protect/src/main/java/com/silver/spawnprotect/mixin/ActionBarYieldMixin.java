package com.silver.spawnprotect.mixin;

import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Status lines that refresh every tick (the desert's "Hydration: ..." display) would trample any other message above
 * the hotbar, such as OPAC's claim name. After any other action-bar message, those status lines wait 3 seconds.
 */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class ActionBarYieldMixin {
    @Unique
    private static final long YIELD_MS = 3000L;

    @Unique
    private long spawnprotect$otherShownAt;

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"), cancellable = true)
    private void spawnprotect$yieldToOtherMessages(Packet<?> packet, ChannelFutureListener listener, CallbackInfo ci) {
        String text;
        if (packet instanceof ClientboundSetActionBarTextPacket bar) {
            text = bar.text().getString();
        } else if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()) {
            text = chat.content().getString();
        } else {
            return;
        }
        long now = System.currentTimeMillis();
        if (spawnprotect$isStatusLine(text)) {
            if (now - spawnprotect$otherShownAt < YIELD_MS) {
                ci.cancel();
            }
        } else if (!text.isBlank()) {
            spawnprotect$otherShownAt = now;
        }
    }

    @Unique
    private static boolean spawnprotect$isStatusLine(String text) {
        return text.startsWith("Hydration: ");
    }
}
