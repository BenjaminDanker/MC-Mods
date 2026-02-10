package com.silver.enderfight.portal;

import com.silver.enderfight.EnderFightMod;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Ensures End deaths do not trigger cross-server portal redirects by suppressing the next interception
 * right after the player respawns in the overworld.
 */
public final class RespawnRedirectHandler {
    private RespawnRedirectHandler() {
    }

    public static void register() {
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (oldPlayer == null || newPlayer == null) {
                return;
            }

            ServerWorld previousWorld = getServerWorld(oldPlayer);
            if (previousWorld == null) {
                return;
            }
            if (!PortalInterceptor.isManagedEndDimension(previousWorld.getRegistryKey())) {
                return;
            }

            PortalInterceptor.suppressNextRedirect(newPlayer);
            EnderFightMod.LOGGER.info("Suppressed portal redirect after End death for {}", newPlayer.getName().getString());
        });
    }

    private static ServerWorld getServerWorld(ServerPlayerEntity player) {
        return player.getCommandSource().getWorld();
    }
}
