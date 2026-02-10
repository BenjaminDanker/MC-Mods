package com.silver.witherfight;

import com.silver.witherfight.config.ConfigManager;
import com.silver.wakeuplobby.portal.PortalRequestPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Ender Fight mod. Responsible for bootstrapping all subsystems and registering
 * Fabric callbacks. The goal is to keep this class light and defer real work to specialised managers.
 */
public final class WitherFightMod implements ModInitializer {
    public static final String MOD_ID = "witherfight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static ConfigManager configManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Wither Fight mod");

        try {
            PayloadTypeRegistry.playS2C().register(PortalRequestPayload.PACKET_ID, PortalRequestPayload.codec);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("Portal request payload type already registered; skipping duplicate registration");
        }

        configManager = new ConfigManager();
        configManager.load();
        LOGGER.info("Loaded config from {}", configManager.getConfigPath());
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
