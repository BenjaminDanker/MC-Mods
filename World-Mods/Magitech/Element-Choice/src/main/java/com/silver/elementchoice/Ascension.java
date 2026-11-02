package com.silver.elementchoice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Players who have begun the Ascension rite: their next pinnacle cast, inside the window, opens the way to Magitech. */
final class Ascension {
    /** How long a player has, after /skills ascend, to cast their pinnacle. */
    static final long WINDOW_TICKS = 30 * 20L;

    private final Map<UUID, Long> armedUntil = new HashMap<>();

    void arm(UUID id, long nowTick) {
        armedUntil.put(id, nowTick + WINDOW_TICKS);
    }

    boolean armed(UUID id, long nowTick) {
        Long until = armedUntil.get(id);
        if (until != null && nowTick >= until) {
            armedUntil.remove(id);
            return false;
        }
        return until != null;
    }

    /** Ends the rite for this player, whether or not it was armed. */
    void clear(UUID id) {
        armedUntil.remove(id);
    }
}
