package com.silver.magitech;

import net.minecraft.server.level.ServerPlayer;

/** For other mods, called by name (Villager-Interface's quartermaster NPCs). Server thread. */
public final class MagitechApi {
    private MagitechApi() {
    }

    /** Turns in the story Seal the player holds in their main hand; false when they hold none. */
    public static boolean turnInHeldSeal(ServerPlayer player) {
        return Arrival.turnInHeld(player);
    }
}
