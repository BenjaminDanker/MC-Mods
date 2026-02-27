package com.silver.viewextend.mixin.client;

import com.silver.viewextend.client.ClientPacketDebugTracker;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "method_11128", at = @At("TAIL"), remap = false)
    private void viewextend$onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        ClientPacketDebugTracker.onChunkData(packet);
    }

    @Inject(method = "method_11143", at = @At("TAIL"), remap = false)
    private void viewextend$onLightUpdate(LightUpdateS2CPacket packet, CallbackInfo ci) {
        ClientPacketDebugTracker.onLightUpdate(packet);
    }

    @Inject(method = "method_18784", at = @At("TAIL"), remap = false, require = 0)
    private void viewextend$onTick(CallbackInfo ci) {
        ClientPacketDebugTracker.onClientTick();
    }
}
