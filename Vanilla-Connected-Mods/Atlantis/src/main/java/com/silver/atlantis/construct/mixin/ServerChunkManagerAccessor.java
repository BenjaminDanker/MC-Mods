package com.silver.atlantis.construct.mixin;

import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerChunkLoadingManager.class)
public interface ServerChunkManagerAccessor {

    @Invoker("markChunkNeedsSaving")
    void atlantis$markChunkNeedsSaving(ChunkPos pos);
}
