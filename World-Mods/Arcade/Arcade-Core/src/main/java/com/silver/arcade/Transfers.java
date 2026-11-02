package com.silver.arcade;

import net.minecraft.server.level.ServerPlayer;

/** Moves a player to another server through the proxy (Portals' signed request). */
public final class Transfers {
    private Transfers() {
    }

    /** {@code arrivalPortal} empty: the player arrives where that server last saved them. */
    public static boolean send(ServerPlayer player, String server, String arrivalPortal, String label) {
        return com.silver.portals.Portals.transfer(player, server, arrivalPortal == null ? "" : arrivalPortal, label);
    }
}
