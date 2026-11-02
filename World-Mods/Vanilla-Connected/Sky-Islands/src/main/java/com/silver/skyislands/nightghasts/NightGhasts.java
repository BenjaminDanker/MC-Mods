package com.silver.skyislands.nightghasts;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NightGhasts {

    private static final Logger LOGGER = LoggerFactory.getLogger(NightGhasts.class);

    private static NightGhastsConfig config;
    private static long ticks;

    private NightGhasts() {
    }

    public static void init() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][nightghasts] init()");
        }
        config = NightGhastsConfig.loadOrCreate();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][nightghasts] config loaded: enabled={} target={} interval={} attempts={}",
                    config.enabled, config.targetGhastsPerPlayer, config.spawnIntervalTicks, config.spawnAttemptsPerInterval);
        }
        ServerTickEvents.END_SERVER_TICK.register(NightGhasts::tick);
    }

    private static void tick(MinecraftServer server) {
        ticks++;
        if (config == null || !config.enabled) {
            if (LOGGER.isDebugEnabled() && (ticks % 1200L) == 0) {
                LOGGER.debug("[Sky-Islands][nightghasts] tick(): skipped (disabled)");
            }
            return;
        }

        NightGhastSpawner.tick(server, ticks, config);
    }
}
