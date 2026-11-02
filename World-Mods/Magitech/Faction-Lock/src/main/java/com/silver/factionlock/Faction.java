package com.silver.factionlock;

import java.util.Locale;

/** Magic and Tech are the two factions; NONE is anyone whose faction is not known yet. */
public enum Faction {
    MAGIC, TECH, NONE;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The faction a finished story gives. Someone who finished both (not possible through Babylon) gets none, so nobody can use both. */
    public static Faction of(boolean magicCompleted, boolean techCompleted) {
        if (magicCompleted == techCompleted) {
            return NONE;
        }
        return magicCompleted ? MAGIC : TECH;
    }

    public static Faction fromId(String id) {
        if (id == null) {
            return NONE;
        }
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "magic" -> MAGIC;
            case "tech" -> TECH;
            default -> NONE;
        };
    }
}
