package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.PetCompanionMod;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ensures input captured by the private pet session never reaches public chat. */
@Mixin(PlayerList.class)
public abstract class PetPrivateChatBroadcastMixin {
    @Inject(
            method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$suppressPrivatePetMessage(
            PlayerChatMessage message,
            ServerPlayer sender,
            ChatType.Bound parameters,
            CallbackInfo callback) {
        if (PetCompanionMod.privateChatInput().shouldSuppressBroadcast(sender)) {
            callback.cancel();
        }
    }
}
