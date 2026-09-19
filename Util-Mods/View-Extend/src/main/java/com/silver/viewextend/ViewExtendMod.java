package com.silver.viewextend;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewExtendMod implements ModInitializer {
    public static final String MOD_ID = "viewextend";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ViewExtendService service;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            if (service != null) service.shutdown();
            ViewExtendConfig config = ViewExtendConfig.load();
            service = new ViewExtendService(config);
            LOGGER.info("View Extend: up to 127 chunks, equal player budget={}, disk starts/tick={}, cache={} MiB",
                    config.maxChunksPerPlayerPerTick(), config.maxNbtReadsPerTick(), config.globalPacketTemplateCacheMaxMiB());
        });
        // Vanilla must update the client's cache center before extended packets arrive.
        ServerTickEvents.START_SERVER_TICK.register(server -> { if (service != null) service.beginTick(); });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (service != null) service.tick(server); });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
            if (service != null) service.onEntityLoaded(entity, world);
        });
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_UNLOAD.register((entity, world) -> {
            if (service != null) service.onEntityUnloaded(entity, world);
        });
        ServerChunkEvents.CHUNK_LOAD.register((world, chunk, generated) -> {
            if (service != null) service.onChunkAvailable(world, chunk);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (service != null) service.resetPlayer(handler.player);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (service != null) { service.shutdown(); service = null; }
        });
    }

    public static ViewExtendService getService() { return service; }
}
