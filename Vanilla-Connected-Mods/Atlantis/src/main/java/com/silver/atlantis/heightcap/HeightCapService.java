package com.silver.atlantis.heightcap;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.Locale;

public final class HeightCapService {

    // Disallow players at/above this Y.
    private static final double MAX_ALLOWED_Y_EXCLUSIVE = 318.0;

    // If they are at/above 318, put them at 317.
    private static final double TELEPORT_TO_Y = 317.0;

    private volatile boolean enabled;

    public HeightCapService() {
        this.enabled = HeightCapConfig.loadEnabledOrDefault(true);
        if (this.enabled) {
            AtlantisMod.LOGGER.info("Height cap is enabled (>=318 -> teleport to 317). Use /atlantis heightcap disable to turn it off.");
        } else {
            AtlantisMod.LOGGER.info("Height cap is disabled. Use /atlantis heightcap enable to turn it on.");
        }
    }

    public void register() {
        ServerTickEvents.END_SERVER_TICK.register(this::onEndServerTick);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        try {
            HeightCapConfig.saveEnabled(enabled);
        } catch (Exception e) {
            AtlantisMod.LOGGER.warn("Failed to persist height cap enabled state: {}", e.getMessage());
        }
    }

    public void sendStatus(ServerPlayer player) {
        String msg = String.format(Locale.ROOT,
            "Height cap is %s. Players at/above Y=318 are teleported to Y=317.",
            enabled ? "ENABLED" : "DISABLED"
        );
        player.sendSystemMessage(Component.literal(msg), false);
    }

    private void onEndServerTick(MinecraftServer server) {
        if (!enabled) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player == null) {
                continue;
            }

            if (AuthorizationChecks.has(player, PermissionNodes.ATLANTIS_HEIGHTCAP_BYPASS)) {
                continue;
            }

            if (player.getY() >= MAX_ALLOWED_Y_EXCLUSIVE) {
                // Keep X/Z and rotation; clamp to 317.
                double x = player.getX();
                double z = player.getZ();
                player.setPos(x, TELEPORT_TO_Y, z);
                player.connection.teleport(x, TELEPORT_TO_Y, z, player.getYRot(), player.getXRot());
            }
        }
    }
}
