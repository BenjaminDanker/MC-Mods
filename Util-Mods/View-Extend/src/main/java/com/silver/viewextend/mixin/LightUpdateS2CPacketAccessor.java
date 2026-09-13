package com.silver.viewextend.mixin;

import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLightUpdatePacket.class)
public interface LightUpdateS2CPacketAccessor {
    @Mutable
    @Accessor("lightData")
    void viewextend$setData(ClientboundLightUpdatePacketData data);
}
