package com.silver.prettychat.fabric;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PrettyChatMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Pretty Chat");

    @Override
    public void onInitialize() {
        LOGGER.info("Pretty Chat local player chat formatting initialized");
    }
}
