package com.silver.resonantmessage.fabric.mixin;

import com.silver.resonantmessage.fabric.ResonantMessageMod;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerGamePacketListenerMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At("HEAD"))
    private void resonantMessage$selectedSlotChanged(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        ResonantMessageMod.onSelectedSlotChange(player, packet.getSlot());
    }

    @Inject(method = "handleContainerClick", at = @At("TAIL"))
    private void resonantMessage$inventoryClicked(ServerboundContainerClickPacket packet, CallbackInfo ci) {
        ResonantMessageMod.onPossibleInventoryChange(player);
    }

    @Inject(method = "handlePlayerAction", at = @At("TAIL"))
    private void resonantMessage$playerAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        ResonantMessageMod.onPossibleInventoryChange(player);
    }
}