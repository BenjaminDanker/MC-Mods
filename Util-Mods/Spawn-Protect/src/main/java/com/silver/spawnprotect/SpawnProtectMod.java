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
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("openpartiesandclaims")) {
            net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                com.silver.spawnprotect.claims.ClaimListCommand.register(dispatcher));
        }
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            com.silver.spawnprotect.protect.OpenPacClaimBar.forget(handler.getPlayer().getUUID()));
        LOGGER.info("Spawn Protect initialized");
    }
}
