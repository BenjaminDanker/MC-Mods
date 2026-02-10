package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.VillagerInterfaceMod;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerManager.class)
public abstract class PlayerManagerMixin {
    @Inject(
        method = "broadcast(Lnet/minecraft/network/message/SignedMessage;Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/network/message/MessageType$Parameters;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void villagerinterface$suppressChatBroadcast(
        SignedMessage message,
        ServerPlayerEntity sender,
        MessageType.Parameters params,
        CallbackInfo ci
    ) {
        if (VillagerInterfaceMod.getConversationManager() == null) {
            return;
        }

        if (VillagerInterfaceMod.getConversationManager().shouldSuppressBroadcast(sender)) {
            ci.cancel();
        }
    }
}
