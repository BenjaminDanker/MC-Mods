package com.silver.skyislands.enderdragons;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EnderDragons {
    private static final Logger LOGGER = LoggerFactory.getLogger(EnderDragons.class);

    private EnderDragons() {
    }

    /**
     * Scaffold hook for future dragon spawning/management.
     */
    public static void init() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons] init()");
        }
        EnderDragonManager.init();
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][dragons] EnderDragonManager.init() complete");
        }
    }
}
