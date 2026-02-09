package com.silver.wardenfight;

import com.silver.wardenfight.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
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
        configManager = new ConfigManager();
        configManager.load();
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
