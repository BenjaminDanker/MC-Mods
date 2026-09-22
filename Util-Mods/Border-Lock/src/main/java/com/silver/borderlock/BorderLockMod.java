package com.silver.borderlock;

import com.silver.authorization.PermissionNodes;
import com.silver.authorization.fabric.AuthorizationChecks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.border.WorldBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class BorderLockMod implements ModInitializer {
    public static final String MOD_ID = "borderlock";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Border Lock initialized");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                enforceBorder(player);
            }
        });
    }

    private static void enforceBorder(ServerPlayer player) {
        if (AuthorizationChecks.has(player, PermissionNodes.BORDER_BYPASS)) {
            return;
        }

        ServerLevel world = player.level();
        WorldBorder border = world.getWorldBorder();

        double x = player.getX();
        double z = player.getZ();

        double west = border.getMinX();
        double east = border.getMaxX();
        double north = border.getMinZ();
        double south = border.getMaxZ();

        boolean inside = x > west && x < east && z > north && z < south;
        if (inside) {
            return;
        }

        double targetX = Mth.clamp(x, west + 0.5, east - 0.5);
        double targetZ = Mth.clamp(z, north + 0.5, south - 0.5);

        double targetY = Math.max(player.getY(), world.getMinY() + 1);
        player.teleportTo(targetX, targetY, targetZ);
    }
}
