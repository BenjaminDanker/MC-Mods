package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.VillagerInterfaceMod;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {
    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void villagerinterface$onChatMessage(ServerboundChatPacket packet, CallbackInfo ci) {
        if (VillagerInterfaceMod.getConversationManager() == null) {
            return;
        }

        if (VillagerInterfaceMod.getConversationManager().handleChatMessage(player, packet.message())) {
            ci.cancel();
        }
    }
}
