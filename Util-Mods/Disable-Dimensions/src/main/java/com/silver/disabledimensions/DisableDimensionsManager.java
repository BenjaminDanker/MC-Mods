package com.silver.disabledimensions;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.world.World;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class DisableDimensionsManager {
    private static final Text BLOCKED_DIMENSION_MESSAGE = Text.literal("That dimension is currently disabled.");
    private static final long MESSAGE_THROTTLE_MS = 1000L;

    private final Path configPath = FabricLoader.getInstance().getConfigDir().resolve("disable-dimensions.properties");
    private final Map<UUID, Long> lastBlockedMessageByPlayer = new ConcurrentHashMap<>();
    private volatile DisableDimensionsConfig config = DisableDimensionsConfig.defaults();

    void load() {
        config = DisableDimensionsConfig.loadOrCreate(configPath);
        DisableDimensionsMod.LOGGER.info(
            "Disable Dimensions loaded: disableNether={}, disableEnd={}, allowOpBypass={}",
            config.disableNether(),
            config.disableEnd(),
            config.allowOpBypass()
        );
    }

    boolean shouldBlockTeleportInto(ServerPlayerEntity player, RegistryKey<World> targetDimension) {
        if (player == null || player.isRemoved() || targetDimension == null) {
            return false;
        }

        DisableDimensionsConfig snapshot = config;
        if (snapshot.allowOpBypass() && player.hasPermissionLevel(2)) {
            return false;
        }

        return (snapshot.disableNether() && targetDimension == World.NETHER)
            || (snapshot.disableEnd() && targetDimension == World.END);
    }

    void notifyBlockedTeleport(ServerPlayerEntity player) {
        if (player != null) {
            long now = System.currentTimeMillis();
            UUID playerId = player.getUuid();
            Long lastSent = lastBlockedMessageByPlayer.get(playerId);
            if (lastSent != null && (now - lastSent) < MESSAGE_THROTTLE_MS) {
                return;
            }

            lastBlockedMessageByPlayer.put(playerId, now);
            player.sendMessage(BLOCKED_DIMENSION_MESSAGE, false);
        }
    }

    boolean enableNether() {
        DisableDimensionsConfig snapshot = config;
        if (!snapshot.disableNether()) {
            return true;
        }

        return updateConfig(snapshot.withDisableNether(false));
    }

    boolean enableEnd() {
        DisableDimensionsConfig snapshot = config;
        if (!snapshot.disableEnd()) {
            return true;
        }

        return updateConfig(snapshot.withDisableEnd(false));
    }

    private boolean updateConfig(DisableDimensionsConfig updated) {
        try {
            DisableDimensionsConfig.write(configPath, updated);
            config = updated;
            return true;
        } catch (Exception e) {
            DisableDimensionsMod.LOGGER.warn("Failed writing Disable Dimensions config: {}", e.getMessage());
            return false;
        }
    }
}
