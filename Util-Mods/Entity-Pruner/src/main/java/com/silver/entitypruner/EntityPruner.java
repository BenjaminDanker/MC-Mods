package com.silver.entitypruner;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityPruner implements ModInitializer {
    public static final String MOD_ID = "entitypruner";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        EntityPrunerConfig.load();
        LOGGER.info("Entity Pruner initialized.");
    }
}