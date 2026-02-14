package com.silver.spawnprotect;

import com.silver.spawnprotect.protect.SpawnProtectionManager;
import com.silver.spawnprotect.protect.SpawnMobControlService;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpawnProtectMod implements ModInitializer {

    public static final String MOD_ID = "spawnprotect";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private final SpawnMobControlService spawnMobControlService = new SpawnMobControlService();

    @Override
    public void onInitialize() {
        SpawnProtectionManager.INSTANCE.load();
        spawnMobControlService.register();
        LOGGER.info("Spawn Protect initialized");
    }
}
