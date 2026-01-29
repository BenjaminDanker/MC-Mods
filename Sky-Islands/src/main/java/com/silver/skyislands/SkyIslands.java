package com.silver.skyislands;

import net.fabricmc.api.ModInitializer;

import com.silver.skyislands.command.SkyIslandsCommands;
import com.silver.skyislands.dragonbreath.DragonBreathTracking;
import com.silver.skyislands.enderdragons.EnderDragons;
import com.silver.skyislands.nightghasts.NightGhasts;
import com.silver.skyislands.nocreepers.NoCreepers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyIslands implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkyIslands.class);

    @Override
    public void onInitialize() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] onInitialize() start");
        }

        SkyIslandsCommands.register();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] commands registered (/skyislands)");
        }

        NoCreepers.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] NoCreepers.init() complete");
        }
        EnderDragons.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] EnderDragons.init() complete");
        }

        DragonBreathTracking.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] DragonBreathTracking.init() complete");
        }
        NightGhasts.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] NightGhasts.init() complete");
            LOGGER.debug("[Sky-Islands][init] onInitialize() done");
        }
    }
}
