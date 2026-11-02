package com.silver.witherfight;

import com.silver.witherfight.config.ConfigManager;
import net.fabricmc.api.ModInitializer;
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

        configManager = new ConfigManager();
        configManager.load();
        LOGGER.info("Loaded config from {}", configManager.getConfigPath());
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }
}
