package com.silver.arcade.realm;

import java.lang.reflect.Method;
import net.minecraft.server.level.ServerPlayer;

/**
 * How far each player sees, per game (via View Extend when it is installed): the normal view distance unless a room
 * asks for more (a big open map), so the server never sends far chunks nobody needs, and nobody sees the next room.
 */
public final class ViewDistance {
    private static Method setMax;
    private static boolean looked;

    private ViewDistance() {
    }

    /** {@code chunks} <= 0 means the server's normal view distance. */
    public static void set(ServerPlayer player, int chunks) {
        Method method = method();
        if (method == null) {
            return;
        }
        int normal = player.level().getServer().getPlayerList().getViewDistance();
        try {
            method.invoke(null, player.getUUID(), chunks > 0 ? Math.max(normal, chunks) : normal);
        } catch (ReflectiveOperationException ignored) {
            // View Extend changed its API: players keep its configured distance.
        }
    }

    private static synchronized Method method() {
        if (!looked) {
            looked = true;
            try {
                setMax = Class.forName("com.silver.viewextend.ViewExtendApi").getMethod("setMaxDistance", java.util.UUID.class, int.class);
            } catch (ReflectiveOperationException absent) {
                setMax = null;
            }
        }
        return setMax;
    }
}
