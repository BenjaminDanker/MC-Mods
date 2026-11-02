package com.silver.soulbounditems;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SoulboundItemsMod implements ModInitializer {
    public static final String MOD_ID = "soulbounditems";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Soulbound Items initialized");
    }
}
