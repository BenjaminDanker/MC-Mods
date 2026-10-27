package com.silver.magitech;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A player's faction ("magic" or "tech"), as Faction-Lock knows it; read by name so this mod builds without it. */
final class Factions {
    private static final Logger LOGGER = LoggerFactory.getLogger("magitech");
    private static final Method FACTION_OF = find();

    private Factions() {
    }

    private static Method find() {
        try {
            return Class.forName("com.silver.factionlock.FactionLockMod").getMethod("factionOf", UUID.class);
        } catch (ReflectiveOperationException missing) {
            LOGGER.info("Faction-Lock is not installed here, so nothing on Magitech knows who belongs to which side");
            return null;
        }
    }

    static boolean available() {
        return FACTION_OF != null;
    }

    /** "magic" or "tech", or null when the player has no faction (yet). */
    static String of(UUID player) {
        if (FACTION_OF == null) {
            return null;
        }
        try {
            Object faction = FACTION_OF.invoke(null, player);
            String id = faction == null ? null : faction.toString().toLowerCase(Locale.ROOT);
            return "magic".equals(id) || "tech".equals(id) ? id : null;
        } catch (ReflectiveOperationException failure) {
            LOGGER.error("ALERT: could not read the faction of {}", player, failure);
            return null;
        }
    }

    static String display(String faction) {
        return "magic".equals(faction) ? "Magic" : "tech".equals(faction) ? "Tech" : "None";
    }
}
