package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.PetCompanionMod;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures pet-session chat using the same input hook as Villager-Interface. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class PetPrivateChatNetworkHandlerMixin {
    @Shadow
    public ServerPlayerEntity player;

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void aipets$capturePrivatePetMessage(
            ChatMessageC2SPacket packet, CallbackInfo callback) {
        // Keep vanilla's signing/chain handling intact. The broadcast hook consumes the
        // captured message after this hook, including !exit, so private input never becomes
        // public chat without invalidating the secure-chat chain.
        PetCompanionMod.privateChatInput().handleChatMessage(player, packet.chatMessage());
    }
}
