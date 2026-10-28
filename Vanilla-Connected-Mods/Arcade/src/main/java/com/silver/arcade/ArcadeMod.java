package com.silver.arcade;

import com.silver.arcade.door.DoorRole;
import com.silver.arcade.realm.RealmRole;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The Gamemaster's Arcade. config/arcade.json picks the role: "realm" (the arcade server) or "door" (world servers). */
public final class ArcadeMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("arcade");

    @Override
    public void onInitialize() {
        ArcadeConfig config;
        try {
            config = ArcadeConfig.load(FabricLoader.getInstance().getConfigDir().resolve("arcade.json"));
        } catch (Exception failure) {
            LOGGER.error("ALERT: could not read config/arcade.json; the arcade is off on this server", failure);
            return;
        }
        ArcadeDb db = new ArcadeDb();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            switch (config.role) {
                case "realm" -> new RealmRole(server, config, db).register();
                case "door" -> {
                    if (config.serverName.isBlank()) {
                        LOGGER.error("ALERT: config/arcade.json has no serverName; doors and prizes are off here");
                        return;
                    }
                    new DoorRole(server, config, db).register();
                }
                default -> LOGGER.warn("config/arcade.json role '{}' is unknown; the arcade is off here", config.role);
            }
        });
    }
}
