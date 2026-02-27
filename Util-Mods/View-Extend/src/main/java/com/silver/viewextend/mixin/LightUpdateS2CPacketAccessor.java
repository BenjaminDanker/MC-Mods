package com.silver.viewextend.mixin;

import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.LightUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LightUpdateS2CPacket.class)
public interface LightUpdateS2CPacketAccessor {
    @Mutable
    @Accessor("data")
    void viewextend$setData(LightData data);
}
