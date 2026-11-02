package com.silver.elementchoice.skills;

import java.util.Map;

/**
 * A family of abilities. Base abilities are open to anyone who owns the element. Every other family
 * unlocks once the player has invested enough points in the elements it names.
 */
public enum Group {
    BASE("Basics", Map.of()),
    ICE("Ice", Map.of(Realm.WATER, 10)),
    HEALING("Healing", Map.of(Realm.WATER, 10)),
    BLOOD("Blood", Map.of(Realm.WATER, 15)),
    METAL("Metal", Map.of(Realm.EARTH, 10)),
    COMBUSTION("Combustion", Map.of(Realm.FIRE, 15)),
    AIR_MASTERY("Air Mastery", Map.of(Realm.AIR, 10)),
    LAVA("Lava", Map.of(Realm.EARTH, 12, Realm.FIRE, 8)),
    LIGHTNING("Lightning", Map.of(Realm.FIRE, 10, Realm.AIR, 10)),
    AVATAR("Ascendant", Map.of(Realm.AIR, 15, Realm.WATER, 15, Realm.EARTH, 15, Realm.FIRE, 15));

    private final String display;
    private final Map<Realm, Integer> requirements;

    Group(String display, Map<Realm, Integer> requirements) {
        this.display = display;
        this.requirements = requirements;
    }

    public String display() {
        return display;
    }

    /** Minimum points invested per element before this family opens. */
    public Map<Realm, Integer> requirements() {
        return requirements;
    }
}
