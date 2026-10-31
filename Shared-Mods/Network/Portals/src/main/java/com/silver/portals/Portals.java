package com.silver.portals;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What other mods use from Portals. Mods that build against it call this directly; others call it by name
 * ({@code Class.forName("com.silver.portals.Portals")}). Server thread unless said otherwise.
 */
public final class Portals {
    private Portals() {
    }

    /**
     * Moves the player to {@code server} through the proxy, arriving at the arrival point named {@code arrival} there
     * (blank: the server's usual spawn). A sleeping server is woken while they wait in the lobby. {@code label} names
     * the caller in the log. False when no request was made (no server, no secret, or a trip already being prepared).
     */
    public static boolean transfer(ServerPlayer player, String server, String arrival, String label) {
        return Travel.send(player, server, arrival, label);
    }

    /** True while the player is arriving through a portal (other portals and redirects should leave them alone). */
    public static boolean arrivalPending(UUID player) {
        return Arrivals.pending(player);
    }

    /** Moves the player to this server's arrival point called {@code arrival}; false when there is none. */
    public static boolean sendToArrival(ServerPlayer player, String arrival) {
        return Arrivals.moveTo(player, arrival);
    }

    /**
     * Records that the player just used the portal called {@code portal}, so the portal it leads back from won't send
     * them straight back (for mods that move players through gates of their own).
     */
    public static void cameThrough(ServerPlayer player, String portal) {
        Travel.cameThrough(player.getUUID(), portal);
    }

    /**
     * Lights the portal frame around {@code inside} as if {@code item} were used on it. {@code player} null means
     * the server itself lights it (allowed only for portals marked anyoneCanLight). True when a portal was made.
     */
    public static boolean light(Level level, BlockPos inside, Item item, Player player) {
        return Frames.light(level, inside, item, player);
    }

    /** True for the inside of a lit portal. */
    public static boolean isPortal(BlockState state) {
        return state.getBlock() instanceof PortalBlock;
    }

    /**
     * Changes where travellers announced as {@code portal} appear on this server (config/portals.json; takes effect at
     * the next start). Facing stays as configured. False when there is no portal of that name.
     */
    public static boolean setArrival(MinecraftServer server, String portal, String world, double x, double y, double z) {
        return PortalsMod.setArrival(portal, world, x, y, z);
    }

    /** Ender-Fight redirects end portals; a player arriving or leaving through ours must not be redirected too. */
    static void quietEndPortalRedirect(ServerPlayer player) {
        try {
            Class.forName("com.silver.enderfight.portal.PortalInterceptor").getMethod("suppressNextRedirect", ServerPlayer.class)
                .invoke(null, player);
        } catch (ReflectiveOperationException absent) {
            // Ender-Fight isn't on this server
        }
    }
}
