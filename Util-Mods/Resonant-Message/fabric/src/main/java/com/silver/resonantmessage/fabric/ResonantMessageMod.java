package com.silver.resonantmessage.fabric;

import java.nio.file.Path;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ResonantMessageMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("resonantmessage");
    private static volatile ResonantMessageRuntime runtime;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(ResonancePayload.ID, ResonancePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ResonancePayload.ID, ResonancePayload.CODEC);
        Path configDirectory = FabricLoader.getInstance().getConfigDir();
        BackendConfig config = BackendConfig.load(configDirectory, LOGGER);
        ResonantMessageRuntime initialized = new ResonantMessageRuntime(config, LOGGER);
        runtime = initialized;
        initialized.register();
        LOGGER.info("[ResonantMessage] Fabric component initialized for backend={}",
                config.backendId().isEmpty() ? "<unconfigured>" : config.backendId());
    }

    public static void onSelectedSlotChange(ServerPlayer player, int newSlot) {
        ResonantMessageRuntime current = runtime;
        if (current != null) current.onSelectedSlotChange(player, newSlot);
    }

    public static void onPossibleInventoryChange(ServerPlayer player) {
        ResonantMessageRuntime current = runtime;
        if (current != null) current.onPossibleInventoryChange(player);
    }
}