package com.silver.gaterites;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Gate Rites: how each faction reaches Magitech, each by their own pinnacle. The role in config/gate-rites.json picks which half runs. */
public final class GateRitesMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("gate-rites");

    @Override
    public void onInitialize() {
        GateConfig config = GateConfig.load(FabricLoader.getInstance().getConfigDir().resolve("gate-rites.json"));
        switch (config.role) {
            case "tech" -> {
                TechGate.register();
                EngineerPlans.register();
                ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                    TechPortal portal = new TechPortal(server, config);
                    ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> portal.shutdown());
                });
                LOGGER.info("Gate Rites: tech role (Gate Core registered)");
            }
            case "magic" -> ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                MagicGate gate = new MagicGate(server, config);
                ServerLifecycleEvents.SERVER_STOPPING.register(stopping -> gate.shutdown());
            });
            default -> LOGGER.warn("Gate Rites has no role set in config/gate-rites.json; it does nothing");
        }
    }
}
