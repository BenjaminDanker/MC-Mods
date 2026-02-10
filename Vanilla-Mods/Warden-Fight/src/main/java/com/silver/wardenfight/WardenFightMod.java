package com.silver.wardenfight;

import com.silver.wardenfight.config.ConfigManager;
import com.silver.wakeuplobby.portal.PortalRequestPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Warden Fight mod. Keeps initialization light and defers real work to
 * dedicated helpers/mixins.
 */
public final class WardenFightMod implements ModInitializer {
    public static final String MOD_ID = "wardenfight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ConfigManager configManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Warden Fight mod");

        try {
            PayloadTypeRegistry.playS2C().register(PortalRequestPayload.PACKET_ID, PortalRequestPayload.codec);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("Portal request payload type already registered; skipping duplicate registration");
        }
        configManager = new ConfigManager();
        configManager.load();
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
