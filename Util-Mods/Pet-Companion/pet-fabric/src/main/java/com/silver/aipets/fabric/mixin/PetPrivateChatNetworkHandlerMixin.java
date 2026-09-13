package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.PetCompanionMod;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures pet-session chat using the same input hook as Villager-Interface. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class PetPrivateChatNetworkHandlerMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"))
    private void aipets$capturePrivatePetMessage(
            ServerboundChatPacket packet, CallbackInfo callback) {
        // Keep vanilla's signing/chain handling intact. The broadcast hook consumes the
        // captured message after this hook, including !exit, so private input never becomes
        // public chat without invalidating the secure-chat chain.
        PetCompanionMod.privateChatInput().handleChatMessage(player, packet.message());
    }
}
