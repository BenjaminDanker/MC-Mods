package com.silver.atlantis.heightcap;

import com.silver.atlantis.AtlantisMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

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

    public void sendStatus(ServerPlayerEntity player) {
        String msg = String.format(Locale.ROOT,
            "Height cap is %s. Players at/above Y=318 are teleported to Y=317.",
            enabled ? "ENABLED" : "DISABLED"
        );
        player.sendMessage(Text.literal(msg), false);
    }

    private void onEndServerTick(MinecraftServer server) {
        if (!enabled) {
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player == null) {
                continue;
            }

            // Allow admins/operators to bypass the cap.
            if (player.hasPermissionLevel(2)) {
                continue;
            }

            if (player.getY() >= MAX_ALLOWED_Y_EXCLUSIVE) {
                // Keep X/Z and rotation; clamp to 317.
                double x = player.getX();
                double z = player.getZ();
                player.setPosition(x, TELEPORT_TO_Y, z);
                player.networkHandler.requestTeleport(x, TELEPORT_TO_Y, z, player.getYaw(), player.getPitch());
            }
        }
    }
}
