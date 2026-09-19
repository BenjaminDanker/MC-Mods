package com.silver.viewextend.mixin;

import net.minecraft.server.network.PlayerChunkSender;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Share vanilla's quota and ACK accounting; two independent senders would misattribute ACKs. */
@Mixin(PlayerChunkSender.class)
public interface PlayerChunkSenderAccessor {
    @Accessor("desiredChunksPerTick") float viewextend$getDesiredRate();
    @Accessor("batchQuota") float viewextend$getQuota();
    @Accessor("batchQuota") void viewextend$setQuota(float quota);
    @Accessor("unacknowledgedBatches") int viewextend$getOutstanding();
    @Accessor("unacknowledgedBatches") void viewextend$setOutstanding(int count);
    @Accessor("maxUnacknowledgedBatches") int viewextend$getMaxOutstanding();
}
