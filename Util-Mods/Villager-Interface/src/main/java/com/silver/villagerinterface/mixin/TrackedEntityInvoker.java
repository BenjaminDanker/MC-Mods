package com.silver.villagerinterface.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface TrackedEntityInvoker {
    @Invoker("updatePlayer")
    void villagerinterface$updatePlayer(ServerPlayer player);
}
