package com.silver.skyislands.giantmobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GiantMobs {
    private static final Logger LOGGER = LoggerFactory.getLogger(GiantMobs.class);

    private GiantMobs() {
    }

    public static void init() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("[Sky-Islands][giants] init()");
        }
        GiantMobManager.init();
    }
}