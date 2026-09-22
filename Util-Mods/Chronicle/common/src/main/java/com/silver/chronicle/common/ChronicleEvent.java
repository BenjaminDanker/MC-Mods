package com.silver.chronicle.common;

import java.util.LinkedHashMap;
import java.util.Map;

public enum ChronicleEvent {
    FIRST_ENTER_SKY_ISLAND("first_enter_sky_island", "enter Sky Islands"),
    FIRST_ENTER_OCEAN("first_enter_ocean", "enter Ocean"),
    FIRST_ENTER_DESERT("first_enter_desert", "enter Desert"),
    FIRST_ENTER_CAVE("first_enter_cave", "enter Cave"),
    FIRST_ENTER_MAGIC("first_enter_magic", "enter Magic"),
    FIRST_DISCOVER_ATLANTIS("first_discover_atlantis", "discover Atlantis"),
    FIRST_DEFEAT_SKY_DRAGON("first_defeat_sky_dragon", "defeat a Sky Islands dragon"),
    FIRST_DEFEAT_SKY_GIANT("first_defeat_sky_giant", "defeat a Sky Islands giant"),
    FIRST_FIRE_SPECIAL_ARROW("first_fire_special_arrow", "fire the special arrow"),
    FIRST_DEFEAT_DESERT_PHARAO("first_defeat_desert_pharao", "defeat the Pharao"),
    FIRST_DEFEAT_CAVE_LEGENDARY("first_defeat_cave_legendary", "defeat a Legendary Cave mob"),
    FIRST_DEFEAT_OCEAN_LEVIATHAN("first_defeat_ocean_leviathan", "defeat an Atlantis Leviathan");

    private static final Map<String, ChronicleEvent> BY_ID = new LinkedHashMap<>();
    static { for (ChronicleEvent event : values()) BY_ID.put(event.id, event); }

    private final String id;
    private final String display;
    ChronicleEvent(String id, String display) { this.id = id; this.display = display; }
    public String id() { return id; }
    public String display() { return display; }
    public static ChronicleEvent byId(String id) { return BY_ID.get(id); }
}
