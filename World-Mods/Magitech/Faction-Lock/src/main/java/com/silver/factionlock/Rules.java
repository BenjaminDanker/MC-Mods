package com.silver.factionlock;

import java.util.Map;

/** Which equipment belongs to which faction. Pure, so it is tested without a server. */
public final class Rules {
    /** Item and block namespaces that belong to a faction. */
    private static final Map<String, Faction> NAMESPACES = Map.of("polyfactory", Faction.TECH, "arsenal", Faction.TECH);

    private Rules() {
    }

    /** Who owns an item or block: by its namespace, else by a {@code faction} entry in its custom data (null when neutral). */
    public static Faction owner(String namespace, String taggedFaction) {
        Faction byNamespace = NAMESPACES.get(namespace);
        if (byNamespace != null) {
            return byNamespace;
        }
        Faction tagged = Faction.fromId(taggedFaction);
        return tagged == Faction.NONE ? null : tagged;
    }

    /** True when {@code user} may not use equipment owned by {@code owner}. Neutral equipment is for everyone. */
    public static boolean blocked(Faction user, Faction owner) {
        return owner != null && user != owner;
    }
}
