package com.silver.elementchoice.skills;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One player's skill tree: the element unlocks they have earned from bosses (a bitmask), the points
 * they can spend, the elements they own, the level of each ability, and their Resilience. Pure data
 * and rules, no Minecraft.
 */
public final class SkillState {
    public static final int RESILIENCE_MAX = 10;
    /** Ley Attunement: the highest level, the Ley Crystals each level costs, and how much invested skill an element needs per level. */
    public static final int ATTUNE_MAX = 5;
    public static final int ATTUNE_PER_INVESTED = 10;
    private static final int[] ATTUNE_COSTS = {2, 3, 4, 5, 6};

    /** Why something can't be bought. */
    public enum Reason { OK, ALREADY_OWNED, NOT_UNLOCKED, NOT_OWNED, GROUP_LOCKED, MAX_LEVEL, NOT_ENOUGH_POINTS, CAPPED, UNKNOWN }

    private final int unlocks;
    private final int points;
    private final EnumSet<Realm> realms;
    private final Map<String, Integer> levels;
    private final int resilience;
    private final EnumMap<Realm, Integer> attunement;

    public SkillState(int unlocks, int points, EnumSet<Realm> realms, Map<String, Integer> levels, int resilience) {
        this(unlocks, points, realms, levels, resilience, Map.of());
    }

    public SkillState(int unlocks, int points, EnumSet<Realm> realms, Map<String, Integer> levels, int resilience, Map<Realm, Integer> attunement) {
        this.attunement = new EnumMap<>(Realm.class);
        attunement.forEach((realm, level) -> {
            if (level > 0) {
                this.attunement.put(realm, Math.min(level, ATTUNE_MAX));
            }
        });
        this.unlocks = unlocks;
        this.points = points;
        this.realms = realms.isEmpty() ? EnumSet.noneOf(Realm.class) : EnumSet.copyOf(realms);
        this.levels = new LinkedHashMap<>(levels);
        this.resilience = resilience;
    }

    public static SkillState empty() {
        return new SkillState(0, 0, EnumSet.noneOf(Realm.class), Map.of(), 0);
    }

    // ------------------------------------------------------------------ reading

    public int unlocks() {
        return unlocks;
    }

    public int points() {
        return points;
    }

    public int resilience() {
        return resilience;
    }

    public EnumSet<Realm> realms() {
        return EnumSet.copyOf(realms.isEmpty() ? EnumSet.noneOf(Realm.class) : realms);
    }

    public Map<String, Integer> levels() {
        return Map.copyOf(levels);
    }

    public boolean owns(Realm realm) {
        return realms.contains(realm);
    }

    public boolean hasAnyRealm() {
        return !realms.isEmpty();
    }

    /** Whether a boss has made this element choosable. */
    public boolean unlocked(Realm realm) {
        return (unlocks & realm.unlockBit()) != 0;
    }

    /** The level a player can actually use: bought levels, or 1 for a free ability of an element they own. */
    public int level(String abilityId) {
        AbilityDef def = AbilityCatalog.byId(abilityId);
        if (def == null) {
            return 0;
        }
        int bought = levels.getOrDefault(abilityId, 0);
        return def.free() && realms.contains(def.realm()) ? Math.max(bought, 1) : bought;
    }

    /** Points spent in an element's abilities; this is what opens the advanced families. */
    public int invested(Realm realm) {
        int total = 0;
        for (AbilityDef def : AbilityCatalog.forRealm(realm)) {
            if (def.group() != Group.AVATAR) {
                total += def.paidFor(level(def.id()));
            }
        }
        return total;
    }

    public boolean groupOpen(Group group) {
        for (Map.Entry<Realm, Integer> requirement : group.requirements().entrySet()) {
            if (!realms.contains(requirement.getKey()) || invested(requirement.getKey()) < requirement.getValue()) {
                return false;
            }
        }
        return true;
    }

    /**
     * The pinnacle a cast of this ability reaches: "avatar" for the Avatar State, or the element of an ability taken to its
     * highest level. Empty when the ability is not a peak of this build.
     */
    public Optional<String> pinnacleOf(String abilityId) {
        AbilityDef def = AbilityCatalog.byId(abilityId);
        if (def == null) {
            return Optional.empty();
        }
        if (def.group() == Group.AVATAR) {
            return level(abilityId) >= 1 ? Optional.of("avatar") : Optional.empty();
        }
        return level(abilityId) >= AbilityDef.MAX_LEVEL ? Optional.of(def.realm().id()) : Optional.empty();
    }

    /** Whether any ability of this build is a pinnacle. */
    public boolean hasPinnacle() {
        for (AbilityDef def : AbilityCatalog.all()) {
            if (pinnacleOf(def.id()).isPresent()) {
                return true;
            }
        }
        return false;
    }

    /** Elements this player may take as their free first pick. */
    public List<Realm> firstPickChoices() {
        List<Realm> choices = new ArrayList<>();
        for (Realm realm : Realm.values()) {
            if (unlocked(realm)) {
                choices.add(realm);
            }
        }
        return choices;
    }

    // ------------------------------------------------------------------ ley attunement

    /** Attunement level of an element: a bonus on every ability of that element, bought with Ley Crystals rather than skill points. */
    public int attunement(Realm realm) {
        return attunement.getOrDefault(realm, 0);
    }

    /** How high this element may be attuned: one level per ten points invested in it, at most five. Specialists go furthest. */
    public int attunementCap(Realm realm) {
        return realms.contains(realm) ? Math.min(ATTUNE_MAX, invested(realm) / ATTUNE_PER_INVESTED) : 0;
    }

    /** Ley Crystals for the next level of this element (0 when it is already at the top). */
    public int attunementCost(Realm realm) {
        int level = attunement(realm);
        return level >= ATTUNE_MAX ? 0 : ATTUNE_COSTS[level];
    }

    public Reason checkAttunement(Realm realm) {
        if (!realms.contains(realm)) {
            return Reason.NOT_OWNED;
        }
        int level = attunement(realm);
        if (level >= ATTUNE_MAX) {
            return Reason.MAX_LEVEL;
        }
        return level >= attunementCap(realm) ? Reason.CAPPED : Reason.OK;
    }

    public SkillState attune(Realm realm) {
        require(checkAttunement(realm), "attunement " + realm);
        EnumMap<Realm, Integer> next = new EnumMap<>(attunement);
        next.put(realm, attunement(realm) + 1);
        return new SkillState(unlocks, points, realms, levels, resilience, next);
    }

    // ------------------------------------------------------------------ costs and checks

    public int realmCost() {
        return Realm.ownCost(realms.size());
    }

    public Reason checkRealm(Realm realm) {
        if (realms.contains(realm)) {
            return Reason.ALREADY_OWNED;
        }
        if (!unlocked(realm)) {
            return Reason.NOT_UNLOCKED;
        }
        return points >= realmCost() ? Reason.OK : Reason.NOT_ENOUGH_POINTS;
    }

    /** Points for the next level of an ability. */
    public int abilityCost(String abilityId) {
        return AbilityDef.costToReach(level(abilityId) + 1);
    }

    public Reason checkAbility(String abilityId) {
        AbilityDef def = AbilityCatalog.byId(abilityId);
        if (def == null) {
            return Reason.UNKNOWN;
        }
        if (def.group() != Group.AVATAR && !realms.contains(def.realm())) {
            return Reason.NOT_OWNED;
        }
        if (!groupOpen(def.group())) {
            return Reason.GROUP_LOCKED;
        }
        if (level(abilityId) >= AbilityDef.MAX_LEVEL) {
            return Reason.MAX_LEVEL;
        }
        return points >= abilityCost(abilityId) ? Reason.OK : Reason.NOT_ENOUGH_POINTS;
    }

    public int resilienceCost() {
        return resilience + 1;
    }

    public Reason checkResilience() {
        if (resilience >= RESILIENCE_MAX) {
            return Reason.MAX_LEVEL;
        }
        return points >= resilienceCost() ? Reason.OK : Reason.NOT_ENOUGH_POINTS;
    }

    // ------------------------------------------------------------------ changes (each returns a new state)

    public SkillState buyRealm(Realm realm) {
        require(checkRealm(realm), "realm " + realm);
        EnumSet<Realm> next = realms();
        next.add(realm);
        return new SkillState(unlocks, points - realmCost(), next, levels, resilience, attunement);
    }

    public SkillState levelUp(String abilityId) {
        require(checkAbility(abilityId), "ability " + abilityId);
        Map<String, Integer> next = new LinkedHashMap<>(levels);
        int cost = abilityCost(abilityId);
        next.put(abilityId, level(abilityId) + 1);
        return new SkillState(unlocks, points - cost, realms, next, resilience, attunement);
    }

    public SkillState levelUpResilience() {
        require(checkResilience(), "resilience");
        return new SkillState(unlocks, points - resilienceCost(), realms, levels, resilience + 1, attunement);
    }

    /** A player's free first element. Ignored when they already own one. */
    public SkillState withFirstRealm(Realm realm) {
        if (!realms.isEmpty()) {
            return this;
        }
        EnumSet<Realm> next = EnumSet.of(realm);
        return new SkillState(unlocks | realm.unlockBit(), points, next, levels, resilience, attunement);
    }

    /** The same state with this element owned for free (admin grants and elements added outside the tree). */
    public SkillState withRealm(Realm realm) {
        EnumSet<Realm> next = realms.isEmpty() ? EnumSet.of(realm) : EnumSet.copyOf(realms);
        next.add(realm);
        return new SkillState(unlocks | realm.unlockBit(), points, next, levels, resilience, attunement);
    }

    /** Every element owned and every ability, Resilience and attunement at its maximum; for testing. */
    public static SkillState maxed(int unlocks, int points) {
        Map<String, Integer> all = new LinkedHashMap<>();
        for (AbilityDef def : AbilityCatalog.all()) {
            all.put(def.id(), AbilityDef.MAX_LEVEL);
        }
        Map<Realm, Integer> attuned = new EnumMap<>(Realm.class);
        for (Realm realm : Realm.values()) {
            attuned.put(realm, ATTUNE_MAX);
        }
        return new SkillState(unlocks | 15, points, EnumSet.allOf(Realm.class), all, RESILIENCE_MAX, attuned);
    }

    /** Nothing owned or bought; the boss unlocks and points stay. */
    public SkillState withoutProgress() {
        return new SkillState(unlocks, points, EnumSet.noneOf(Realm.class), Map.of(), 0);
    }

    /** The same state with this element's boss unlock recorded. */
    public SkillState withUnlock(Realm realm) {
        return new SkillState(unlocks | realm.unlockBit(), points, realms, levels, resilience, attunement);
    }

    private void require(Reason reason, String what) {
        if (reason != Reason.OK) {
            throw new IllegalStateException("Cannot buy " + what + ": " + reason);
        }
    }

    // ------------------------------------------------------------------ permissions

    /**
     * Whether Bending's permission path (the text after "bending.", such as "ability.icespike" or
     * "lava") is currently allowed. Paths this tree does not control return true.
     */
    public boolean allowsPath(String path) {
        if (path.startsWith("ability.")) {
            AbilityDef def = AbilityCatalog.byId(path.substring("ability.".length()));
            return def == null || level(def.id()) >= 1;
        }
        List<AbilityDef> dependants = AbilityCatalog.byExtraPermission(path);
        if (dependants.isEmpty()) {
            return true;
        }
        return dependants.stream().anyMatch(def -> level(def.id()) >= 1);
    }

    // ------------------------------------------------------------------ storage text

    /** Canonical text stored in MPDS: owned elements, ability levels, then resilience. */
    public String serializeTree() {
        List<String> parts = new ArrayList<>();
        for (Realm realm : Realm.values()) {
            if (realms.contains(realm)) {
                parts.add(realm.id());
            }
        }
        for (AbilityDef def : AbilityCatalog.all()) {
            int bought = levels.getOrDefault(def.id(), 0);
            if (bought > 0) {
                parts.add("a:" + def.id() + "=" + bought);
            }
        }
        if (resilience > 0) {
            parts.add("res=" + resilience);
        }
        for (Realm realm : Realm.values()) {
            if (attunement(realm) > 0) {
                parts.add("att:" + realm.id() + "=" + attunement(realm));
            }
        }
        return String.join(",", parts);
    }

    /**
     * Reads the stored text. The older format, which named whole families such as "ice" or "lava",
     * still loads: each such family becomes level 1 of all its abilities.
     */
    public static SkillState parse(int unlocks, int points, String tree) {
        EnumSet<Realm> realms = EnumSet.noneOf(Realm.class);
        Map<String, Integer> levels = new LinkedHashMap<>();
        int resilience = 0;
        Map<Realm, Integer> attunement = new EnumMap<>(Realm.class);
        for (String raw : tree == null ? new String[0] : tree.split(",")) {
            String token = raw.trim();
            if (token.isEmpty()) {
                continue;
            }
            Realm realm = Realm.fromId(token);
            if (realm != null) {
                realms.add(realm);
            } else if (token.startsWith("a:") && token.contains("=")) {
                String id = token.substring(2, token.indexOf('='));
                int level = parseInt(token.substring(token.indexOf('=') + 1));
                if (AbilityCatalog.byId(id) != null && level > 0) {
                    levels.put(id, Math.min(level, AbilityDef.MAX_LEVEL));
                }
            } else if (token.startsWith("att:") && token.contains("=")) {
                Realm attuned = Realm.fromId(token.substring(4, token.indexOf('=')));
                int level = parseInt(token.substring(token.indexOf('=') + 1));
                if (attuned != null && level > 0) {
                    attunement.put(attuned, Math.min(level, ATTUNE_MAX));
                }
            } else if (token.startsWith("res=")) {
                resilience = Math.max(0, Math.min(parseInt(token.substring(4)), RESILIENCE_MAX));
            } else {
                for (AbilityDef def : legacyFamily(token)) {
                    levels.merge(def.id(), 1, Math::max);
                }
            }
        }
        return new SkillState(unlocks, points, realms, levels, resilience, attunement);
    }

    private static int parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException invalid) {
            return 0;
        }
    }

    private static List<AbilityDef> legacyFamily(String token) {
        Group group = switch (token.toLowerCase(java.util.Locale.ROOT)) {
            case "ice" -> Group.ICE;
            case "blood" -> Group.BLOOD;
            case "healing" -> Group.HEALING;
            case "metal" -> Group.METAL;
            case "combustion" -> Group.COMBUSTION;
            case "air_mastery" -> Group.AIR_MASTERY;
            case "lava" -> Group.LAVA;
            case "lightning" -> Group.LIGHTNING;
            case "avatar" -> Group.AVATAR;
            default -> null;
        };
        if (group == null) {
            return List.of();
        }
        return AbilityCatalog.all().stream().filter(def -> def.group() == group).toList();
    }
}
