package com.silver.viewextend;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * For other mods: caps how far View Extend sends chunks to one player (in chunks, counting the normal view distance).
 * The arcade uses it so each game gets the view it needs and no more; a cap never goes below the normal view distance.
 * Call from the server thread; {@link #clear} restores the configured behaviour.
 */
public final class ViewExtendApi {
    static final Map<UUID, Integer> CAPS = new ConcurrentHashMap<>();

    private ViewExtendApi() {
    }

    public static void setMaxDistance(UUID player, int chunks) {
        CAPS.put(player, Math.max(1, Math.min(127, chunks)));
    }

    public static void clear(UUID player) {
        CAPS.remove(player);
    }
}
