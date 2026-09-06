package com.silver.aipets.fabric.mixin;

import com.silver.aipets.fabric.PetCompanionMod;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ensures input captured by the private pet session never reaches public chat. */
@Mixin(PlayerManager.class)
public abstract class PetPrivateChatBroadcastMixin {
    @Inject(
            method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void aipets$suppressPrivatePetMessage(
            SignedMessage message,
            ServerPlayerEntity sender,
            MessageType.Parameters parameters,
            CallbackInfo callback) {
        if (PetCompanionMod.privateChatInput().shouldSuppressBroadcast(sender)) {
            callback.cancel();
        }
    }
}
