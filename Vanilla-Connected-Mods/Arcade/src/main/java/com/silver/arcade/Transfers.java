package com.silver.arcade;

import de.michiruf.serverportals.ServerPortalsMod;
import net.minecraft.server.level.ServerPlayer;

/** Moves a player to another server through the proxy (server-portals' signed request). */
public final class Transfers {
    private Transfers() {
    }

    /** {@code arrivalPortal} empty: the player arrives where that server last saved them. */
    public static boolean send(ServerPlayer player, String server, String arrivalPortal, String label) {
        return ServerPortalsMod.transfer(player, server, arrivalPortal == null ? "" : arrivalPortal, label);
    }
}
