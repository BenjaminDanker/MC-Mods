package com.silver.skyislands;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import com.silver.skyislands.command.SkyIslandsCommands;
import com.silver.skyislands.dragonbreath.DragonBreathTracking;
import com.silver.skyislands.enderdragons.EnderDragons;
import com.silver.skyislands.giantmobs.GiantMobs;
import com.silver.skyislands.nightghasts.NightGhasts;
import com.silver.skyislands.nocreepers.NoCreepers;
import com.silver.skyislands.portal.PortalRedirector;
import com.silver.skyislands.portal.VoidDeathRedirectHandler;
import com.silver.skyislands.proxy.PortalRequestPayload;
import com.silver.skyislands.specialitems.SpecialItemConversionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SkyIslands implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger(SkyIslands.class);

    @Override
    public void onInitialize() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] onInitialize() start");
        }

        try {
            PayloadTypeRegistry.playS2C().register(PortalRequestPayload.PACKET_ID, PortalRequestPayload.codec);
        } catch (IllegalArgumentException ex) {
            LOGGER.debug("[Sky-Islands][init] portal request payload already registered");
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

        GiantMobs.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] GiantMobs.init() complete");
        }

        PortalRedirector.init();
        VoidDeathRedirectHandler.init();

        DragonBreathTracking.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] DragonBreathTracking.init() complete");
        }
        NightGhasts.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] NightGhasts.init() complete");
        }

        SpecialItemConversionManager.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][init] SpecialItemConversionManager.init() complete");
            LOGGER.debug("[Sky-Islands][init] onInitialize() done");
        }
    }
}
