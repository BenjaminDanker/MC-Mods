package com.silver.borderlock;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.border.WorldBorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.Set;

public final class BorderLockMod implements ModInitializer {
    public static final String MOD_ID = "borderlock";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Border Lock initialized");

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                enforceBorder(player);
            }
        });
    }

    private static void enforceBorder(ServerPlayerEntity player) {
        if (player.hasPermissionLevel(2)) {
            return;
        }

        ServerWorld world = player.getEntityWorld();
        WorldBorder border = world.getWorldBorder();

        double x = player.getX();
        double z = player.getZ();

        double west = border.getBoundWest();
        double east = border.getBoundEast();
        double north = border.getBoundNorth();
        double south = border.getBoundSouth();

        boolean inside = x > west && x < east && z > north && z < south;
        if (inside) {
            return;
        }

        double targetX = MathHelper.clamp(x, west + 0.5, east - 0.5);
        double targetZ = MathHelper.clamp(z, north + 0.5, south - 0.5);

        double targetY = Math.max(player.getY(), world.getBottomY() + 1);

        Set<PositionFlag> flags = EnumSet.noneOf(PositionFlag.class);
        player.teleport(world, targetX, targetY, targetZ, flags, player.getYaw(), player.getPitch(), false);
    }
}
