package com.silver.magitech;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Someone who dies without a bed or anchor comes back on their own side: the landing dock of their faction, not the
 * world spawn out at sea. A player without a known faction (or with a bed) respawns as usual. The faction comes from
 * Faction-Lock, read by name so this mod does not need it to build.
 */
final class DockRespawn {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    /** A respawn this close to the world spawn (horizontally) used the world spawn, not a bed. */
    private static final double WORLD_SPAWN_RADIUS = 48;

    private final MinecraftServer server;
    private final Landings landings;
    private final Method factionOf;

    private DockRespawn(MinecraftServer server, Landings landings, Method factionOf) {
        this.server = server;
        this.landings = landings;
        this.factionOf = factionOf;
    }

    static void register(MinecraftServer server, Landings landings) {
        Method factionOf;
        try {
            factionOf = Class.forName("com.silver.factionlock.FactionLockMod").getMethod("factionOf", UUID.class);
        } catch (ReflectiveOperationException missing) {
            LOGGER.info("Faction-Lock is not installed here, so respawns stay at the world spawn");
            return;
        }
        DockRespawn respawn = new DockRespawn(server, landings, factionOf);
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (!alive) {
                respawn.onRespawn(newPlayer);
            }
        });
    }

    private void onRespawn(ServerPlayer player) {
        if (!usedWorldSpawn(player)) {
            return;
        }
        String faction = faction(player.getUUID());
        if (faction == null) {
            return;
        }
        landings.arrival(faction).ifPresent(at -> {
            ServerLevel overworld = server.overworld();
            player.teleportTo(overworld, at.x, at.y, at.z, Set.of(), player.getYRot(), player.getXRot(), true);
            LOGGER.info("{} respawned at the {} dock", player.getName().getString(), faction);
        });
    }

    private boolean usedWorldSpawn(ServerPlayer player) {
        LevelData.RespawnData spawn = server.getRespawnData();
        if (spawn == null || player.level() != server.getLevel(spawn.dimension())) {
            return false;
        }
        double dx = player.getX() - (spawn.pos().getX() + 0.5);
        double dz = player.getZ() - (spawn.pos().getZ() + 0.5);
        return dx * dx + dz * dz <= WORLD_SPAWN_RADIUS * WORLD_SPAWN_RADIUS;
    }

    /** "magic" or "tech", or null when the player has no faction (yet). */
    private String faction(UUID player) {
        try {
            Object faction = factionOf.invoke(null, player);
            String id = faction == null ? null : faction.toString().toLowerCase(java.util.Locale.ROOT);
            return "magic".equals(id) || "tech".equals(id) ? id : null;
        } catch (ReflectiveOperationException failure) {
            LOGGER.error("ALERT: could not read the faction of {}", player, failure);
            return null;
        }
    }
}
