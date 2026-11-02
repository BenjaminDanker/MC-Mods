package com.silver.villagerinterface.mixin;

import com.silver.villagerinterface.dialogue.Dialogues;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Dialog buttons of the scripted NPC conversations (handled on the server thread, after the vanilla thread check). */
@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CustomClickMixin {
    @Inject(
        method = "handleCustomClickAction(Lnet/minecraft/network/protocol/common/ServerboundCustomClickActionPacket;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;handleCustomClickAction(Lnet/minecraft/resources/Identifier;Ljava/util/Optional;)V"),
        cancellable = true
    )
    private void villagerinterface$dialogueChoice(ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        if ((Object) this instanceof ServerGamePacketListenerImpl game && Dialogues.click(game.player, packet.id(), packet.payload())) {
            ci.cancel();
        }
    }
}
