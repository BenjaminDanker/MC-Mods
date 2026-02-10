package com.silver.villagerinterface;

import com.silver.villagerinterface.command.VillagerInterfaceCommands;
import com.silver.villagerinterface.config.ConfigManager;
import com.silver.villagerinterface.conversation.ConversationManager;
import com.silver.villagerinterface.villager.CustomVillagerManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VillagerInterfaceMod implements ModInitializer {
    public static final String MOD_ID = "villagerinterface";
    public static final String CUSTOM_VILLAGER_ID_KEY = "villagerinterface:custom_id";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ConfigManager configManager;
    private static CustomVillagerManager villagerManager;
    private static ConversationManager conversationManager;

    @Override
    public void onInitialize() {
        configManager = new ConfigManager();
        configManager.load();

        villagerManager = new CustomVillagerManager(configManager);
        conversationManager = new ConversationManager(villagerManager);

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            villagerManager.onServerStarted(server);
            VillagerInterfaceCommands.registerNow(server);
        });
        ServerTickEvents.END_SERVER_TICK.register(villagerManager::onServerTick);
        ServerTickEvents.END_SERVER_TICK.register(conversationManager::onServerTick);
        ServerPlayConnectionEvents.DISCONNECT.register(conversationManager::onPlayerDisconnect);
        VillagerInterfaceCommands.register();

        LOGGER.info("Villager Interface initialized");
    }

    public static ConfigManager getConfigManager() {
        return configManager;
    }

    public static CustomVillagerManager getVillagerManager() {
        return villagerManager;
    }

    public static ConversationManager getConversationManager() {
        return conversationManager;
    }
}
