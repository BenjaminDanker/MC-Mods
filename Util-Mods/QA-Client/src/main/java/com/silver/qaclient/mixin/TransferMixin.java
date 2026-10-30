package com.silver.qaclient.mixin;

import com.silver.qaclient.Rec;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The QA client never follows a transfer to another server (the real network is off limits); it records it. */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class TransferMixin {
    @Inject(method = "handleTransfer", at = @At("HEAD"), cancellable = true)
    private void qaclient$noTransfer(ClientboundTransferPacket packet, CallbackInfo ci) {
        Rec.event("transfer_blocked", "host", packet.host(), "port", packet.port());
        ci.cancel();
    }
}
