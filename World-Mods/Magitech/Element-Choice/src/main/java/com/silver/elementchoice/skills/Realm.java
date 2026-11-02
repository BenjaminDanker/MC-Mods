package com.silver.elementchoice.skills;

import java.util.Locale;

/** The four elements. A boss unlock makes a realm choosable; points make it yours. */
public enum Realm {
    AIR("Air", 1),
    WATER("Water", 2),
    EARTH("Earth", 4),
    FIRE("Fire", 8);

    /** Cost of owning a realm, by how many the player already owns: the first is free. */
    private static final int[] OWN_COSTS = {0, 8, 12, 16};

    private final String display;
    private final int unlockBit;

    Realm(String display, int unlockBit) {
        this.display = display;
        this.unlockBit = unlockBit;
    }

    public String display() {
        return display;
    }

    /** The bit this element has in the Player Sync unlock mask. */
    public int unlockBit() {
        return unlockBit;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static int ownCost(int alreadyOwned) {
        return OWN_COSTS[Math.min(alreadyOwned, OWN_COSTS.length - 1)];
    }

    public static Realm fromId(String id) {
        for (Realm realm : values()) {
            if (realm.id().equalsIgnoreCase(id)) {
                return realm;
            }
        }
        return null;
    }
}
