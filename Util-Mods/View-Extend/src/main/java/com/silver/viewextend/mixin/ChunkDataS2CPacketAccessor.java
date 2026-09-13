package com.silver.viewextend.mixin;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ClientboundLevelChunkWithLightPacket.class)
public interface ChunkDataS2CPacketAccessor {
    @Mutable
    @Accessor("lightData")
    void viewextend$setLightData(ClientboundLightUpdatePacketData lightData);
}
