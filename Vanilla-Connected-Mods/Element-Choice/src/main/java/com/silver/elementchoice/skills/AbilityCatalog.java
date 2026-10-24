package com.silver.elementchoice.skills;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Every ability the skill tree controls, and which permission path belongs to which ability. */
public final class AbilityCatalog {
    private static final Map<String, AbilityDef> BY_ID = new LinkedHashMap<>();
    private static final Map<String, List<AbilityDef>> BY_EXTRA = new LinkedHashMap<>();

    static {
        // Air
        free("airblast", "Air Blast", Realm.AIR);
        free("airswipe", "Air Swipe", Realm.AIR);
        defense("airshield", "Air Shield", Realm.AIR);
        base("airspout", "Air Spout", Realm.AIR);
        base("airscooter", "Air Scooter", Realm.AIR);
        base("airblade", "Air Blade", Realm.AIR);
        base("airpunch", "Air Punch", Realm.AIR);
        base("airbreath", "Air Breath", Realm.AIR);
        grouped("tornado", "Tornado", Realm.AIR, Group.AIR_MASTERY);
        grouped("airwheel", "Air Wheel", Realm.AIR, Group.AIR_MASTERY);
        grouped("airburst", "Air Burst", Realm.AIR, Group.AIR_MASTERY);
        grouped("skyglide", "Sky Glide", Realm.AIR, Group.AIR_MASTERY);
        grouped("updraft", "Updraft", Realm.AIR, Group.AIR_MASTERY);
        // Water
        free("watermanipulation", "Water Manipulation", Realm.WATER);
        free("waterring", "Water Ring", Realm.WATER);
        defense("tideward", "Tide Ward", Realm.WATER);
        base("waterspout", "Water Spout", Realm.WATER);
        base("waterwave", "Water Wave", Realm.WATER);
        base("waterbubble", "Water Bubble", Realm.WATER);
        base("torrent", "Torrent", Realm.WATER);
        base("octopusform", "Octopus Form", Realm.WATER);
        base("watergimbal", "Water Gimbal", Realm.WATER);
        base("gills", "Gills", Realm.WATER);
        grouped("icecrawl", "Ice Crawl", Realm.WATER, Group.ICE);
        grouped("icespike", "Ice Spike", Realm.WATER, Group.ICE);
        grouped("icewall", "Ice Wall", Realm.WATER, Group.ICE);
        grouped("iceberg", "Iceberg", Realm.WATER, Group.ICE);
        grouped("frostbreath", "Frost Breath", Realm.WATER, Group.ICE);
        grouped("phasechange", "Phase Change", Realm.WATER, Group.ICE);
        grouped("healingwaters", "Healing Waters", Realm.WATER, Group.HEALING);
        grouped("bloodbending", "Blood Bending", Realm.WATER, Group.BLOOD);
        // Earth
        free("earthblast", "Earth Blast", Realm.EARTH);
        free("raiseearth", "Raise Earth", Realm.EARTH);
        defense("eartharmor", "Earth Armor", Realm.EARTH);
        base("earthshot", "Earth Shot", Realm.EARTH);
        base("earthline", "Earth Line", Realm.EARTH);
        base("earthglove", "Earth Glove", Realm.EARTH);
        base("earthsmash", "Earth Smash", Realm.EARTH);
        base("earthsurf", "Earth Surf", Realm.EARTH);
        base("earthtunnel", "Earth Tunnel", Realm.EARTH);
        base("collapse", "Collapse", Realm.EARTH);
        base("shockwave", "Shockwave", Realm.EARTH);
        base("catapult", "Catapult", Realm.EARTH);
        base("bulwark", "Bulwark", Realm.EARTH);
        base("earthpillars", "Earth Pillars", Realm.EARTH);
        base("earthshards", "Earth Shards", Realm.EARTH);
        base("deepsight", "Deep Sight", Realm.EARTH);
        grouped("metalcable", "Metal Cable", Realm.EARTH, Group.METAL, "metal");
        grouped("ferrocontrol", "Ferro Control", Realm.EARTH, Group.METAL, "metal");
        grouped("locksmithing", "Locksmithing", Realm.EARTH, Group.METAL, "metal");
        grouped("lavadisk", "Lava Disk", Realm.EARTH, Group.LAVA, "lava");
        grouped("lavaflux", "Lava Flux", Realm.EARTH, Group.LAVA, "lava");
        // Fire
        free("fireblast", "Fire Blast", Realm.FIRE);
        free("firejet", "Fire Jet", Realm.FIRE);
        defense("fireshield", "Fire Shield", Realm.FIRE);
        base("blaze", "Blaze", Realm.FIRE);
        base("firebreath", "Fire Breath", Realm.FIRE);
        base("fireburst", "Fire Burst", Realm.FIRE);
        base("firewall", "Fire Wall", Realm.FIRE);
        base("flamerush", "Flame Rush", Realm.FIRE);
        base("cinderveil", "Cinder Veil", Realm.FIRE);
        base("heatcontrol", "Heat Control", Realm.FIRE);
        base("firekick", "Fire Kick", Realm.FIRE);
        base("firespin", "Fire Spin", Realm.FIRE);
        base("firewheel", "Fire Wheel", Realm.FIRE);
        grouped("combustion", "Combustion", Realm.FIRE, Group.COMBUSTION);
        grouped("lightning", "Lightning", Realm.FIRE, Group.LIGHTNING);
        // Ascendant: owned by no single element, invested under Air so it sits on the Air page
        grouped("avatarstate", "Ascendant State", Realm.AIR, Group.AVATAR);
    }

    private AbilityCatalog() {
    }

    private static void add(AbilityDef def) {
        BY_ID.put(def.id(), def);
        for (String extra : def.extraPermissions()) {
            BY_EXTRA.computeIfAbsent(extra, ignored -> new ArrayList<>()).add(def);
        }
    }

    private static void free(String id, String display, Realm realm) {
        add(new AbilityDef(id, display, realm, Group.BASE, true, false, List.of()));
    }

    private static void defense(String id, String display, Realm realm) {
        add(new AbilityDef(id, display, realm, Group.BASE, true, true, List.of()));
    }

    private static void base(String id, String display, Realm realm) {
        add(new AbilityDef(id, display, realm, Group.BASE, false, false, List.of()));
    }

    private static void grouped(String id, String display, Realm realm, Group group, String... extras) {
        add(new AbilityDef(id, display, realm, group, false, false, List.of(extras)));
    }

    public static List<AbilityDef> all() {
        return List.copyOf(BY_ID.values());
    }

    public static AbilityDef byId(String id) {
        return BY_ID.get(id);
    }

    /** What an element's page lists: its own abilities plus the combined families (Lava, Lightning) it helps unlock. */
    public static List<AbilityDef> shownOn(Realm realm) {
        return BY_ID.values().stream()
            .filter(def -> def.realm() == realm
                || def.group().requirements().size() == 2 && def.group().requirements().containsKey(realm))
            .toList();
    }

    public static List<AbilityDef> forRealm(Realm realm) {
        return BY_ID.values().stream().filter(def -> def.realm() == realm).toList();
    }

    /** Abilities that a Bending permission path such as "lava" depends on (empty when it is not an extra path). */
    public static List<AbilityDef> byExtraPermission(String path) {
        return BY_EXTRA.getOrDefault(path, Collections.emptyList());
    }
}
