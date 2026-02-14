package com.silver.disabledimensions;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.World;
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

    public static boolean shouldBlockTeleportInto(ServerPlayerEntity player, RegistryKey<World> targetDimension) {
        return MANAGER.shouldBlockTeleportInto(player, targetDimension);
    }

    public static void notifyBlockedTeleport(ServerPlayerEntity player) {
        MANAGER.notifyBlockedTeleport(player);
    }
}
