package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.VillagerInterfaceMod;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerList.class)
public abstract class PlayerListMixin {
    @Inject(
        method = "broadcastChatMessage(Lnet/minecraft/network/chat/PlayerChatMessage;Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/network/chat/ChatType$Bound;)V",
        at = @At("HEAD"),
        cancellable = true
    )
    private void villagerinterface$suppressChatBroadcast(
        PlayerChatMessage message,
        ServerPlayer sender,
        ChatType.Bound params,
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
