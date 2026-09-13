package com.silver.disabledimensions;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DisableDimensionsMod implements ModInitializer {

    public static final String MOD_ID = "disabledimensions";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final DisableDimensionsManager MANAGER = new DisableDimensionsManager();

    @Override
    public void onInitialize() {
        MANAGER.load();
        DisableDimensionsCommands.register(MANAGER);
        LOGGER.info("Disable Dimensions initialized");
    }

    public static DisableDimensionsManager manager() {
        return MANAGER;
    }

    public static boolean shouldBlockTeleportInto(ServerPlayer player, ResourceKey<Level> targetDimension) {
        return MANAGER.shouldBlockTeleportInto(player, targetDimension);
    }

    public static void notifyBlockedTeleport(ServerPlayer player) {
        MANAGER.notifyBlockedTeleport(player);
    }
}
