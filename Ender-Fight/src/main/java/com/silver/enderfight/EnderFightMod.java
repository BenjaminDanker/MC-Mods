package com.silver.enderfight;

import com.silver.enderfight.config.ConfigManager;
import com.silver.enderfight.reset.EndResetManager;
import com.silver.enderfight.dragon.DragonBreathModifier;
import com.silver.enderfight.portal.PortalInterceptor;
import com.silver.enderfight.portal.RespawnRedirectHandler;
import com.silver.enderfight.command.EnderFightCommands;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entry point for the Ender Fight mod. Responsible for bootstrapping all subsystems and registering
 * Fabric callbacks. The goal is to keep this class light and defer real work to specialised managers.
 */
public final class EnderFightMod implements ModInitializer {
    public static final String MOD_ID = "enderfight";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ConfigManager configManager;
    private static EndResetManager endResetManager;

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Ender Fight mod");

        configManager = new ConfigManager();
        configManager.load();

        endResetManager = new EndResetManager(configManager);

        // Register lifecycle hooks once so subsystem setup is centralised.
        ServerLifecycleEvents.SERVER_STARTED.register(server -> endResetManager.onServerStarted(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> endResetManager.onServerStopped(server));
        ServerTickEvents.END_SERVER_TICK.register(this::handleServerTick);

        try {
            DragonBreathModifier.register(configManager);
        } catch (LinkageError linkageError) {
            LOGGER.error("Skipping dragon breath customization; Minecraft class linkage failed: {}", linkageError.toString());
        }
        PortalInterceptor.register(configManager);
        RespawnRedirectHandler.register();
        EnderFightCommands.register();
    }

    private void handleServerTick(MinecraftServer server) {
        if (endResetManager != null) {
            endResetManager.tick(server);
        }
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static EndResetManager getEndResetManager() {
        return endResetManager;
    }
}
