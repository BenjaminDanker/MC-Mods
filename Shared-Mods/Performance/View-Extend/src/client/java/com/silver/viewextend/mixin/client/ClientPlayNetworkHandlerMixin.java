package com.silver.viewextend.mixin.client;

import com.silver.viewextend.client.ClientPacketDebugTracker;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "handleLevelChunkWithLight", at = @At("TAIL"))
    private void viewextend$onChunkData(ClientboundLevelChunkWithLightPacket packet, CallbackInfo ci) {
        ClientPacketDebugTracker.onChunkData(packet);
    }

    @Inject(method = "handleLightUpdatePacket", at = @At("TAIL"))
    private void viewextend$onLightUpdate(ClientboundLightUpdatePacket packet, CallbackInfo ci) {
        ClientPacketDebugTracker.onLightUpdate(packet);
    }

    @Inject(method = "handleForgetLevelChunk", at = @At("TAIL"))
    private void viewextend$onForgetChunk(ClientboundForgetLevelChunkPacket packet, CallbackInfo ci) {
        ClientPacketDebugTracker.onForgetChunk(packet.pos());
    }

    @Inject(method = "clearLevel", at = @At("HEAD"))
    private void viewextend$onClearLevel(CallbackInfo ci) {
        ClientPacketDebugTracker.clear();
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void viewextend$onTick(CallbackInfo ci) {
        ClientPacketDebugTracker.onClientTick();
    }
}
