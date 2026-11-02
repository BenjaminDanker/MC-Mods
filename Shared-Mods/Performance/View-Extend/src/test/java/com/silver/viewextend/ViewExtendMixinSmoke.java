package com.silver.viewextend;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Loaded only by the runMixinSmokeTest development task, never shipped in the mod. */
public final class ViewExtendMixinSmoke implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        try {
            ClassLoader loader = getClass().getClassLoader();
            String[][] contracts = {
                {"net.minecraft.server.network.PlayerChunkSender", "com.silver.viewextend.mixin.PlayerChunkSenderAccessor"},
                {"net.minecraft.server.level.ChunkMap", "com.silver.viewextend.mixin.ServerChunkLoadingManagerAccessor"},
                {"net.minecraft.server.level.ChunkMap$TrackedEntity", "com.silver.viewextend.ExtendedPlayerTracker"},
                {"net.minecraft.server.network.ServerCommonPacketListenerImpl", "com.silver.viewextend.mixin.ServerConnectionAccessor"},
                {"net.minecraft.network.Connection", "com.silver.viewextend.mixin.ConnectionAccessor"}
            };
            for (String[] contract : contracts) {
                Class<?> target = Class.forName(contract[0], false, loader);
                Class<?> api = Class.forName(contract[1], false, loader);
                if (!api.isAssignableFrom(target)) throw new AssertionError("Mixin missing: " + contract[0]);
            }
            var sender = new net.minecraft.server.network.PlayerChunkSender(false);
            var flow = (com.silver.viewextend.mixin.PlayerChunkSenderAccessor) sender;
            flow.viewextend$setQuota(7);
            flow.viewextend$setOutstanding(2); // One vanilla batch and one visual batch.
            sender.onChunkBatchReceivedByClient(12);
            if (flow.viewextend$getOutstanding() != 1 || flow.viewextend$getQuota() != 7)
                throw new AssertionError("First ACK must leave the other batch outstanding");
            sender.onChunkBatchReceivedByClient(12);
            if (flow.viewextend$getOutstanding() != 0 || flow.viewextend$getQuota() != 1
                    || flow.viewextend$getMaxOutstanding() != 10) throw new AssertionError("Vanilla ACK accounting failed");
            System.out.println("View Extend Fabric mixin smoke test passed (5 targets, including networking injections)");
        } catch (ClassNotFoundException exception) {
            throw new AssertionError(exception);
        }
    }
}
