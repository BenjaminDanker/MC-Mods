package com.silver.arsenal;

import java.util.List;
import java.util.Map;

/**
 * One weapon, gadget or ammunition as {@code arms.json} describes it. The file is produced by tools/arms.py, which also writes
 * the recipes, language file and item definitions from the same table, so the three never drift apart.
 */
final class Spec {
    String id;
    String name;
    /** The vanilla item a client is told this is (it never shows: the resource pack's model replaces it). */
    String base = "minecraft:stick";
    String rarity = "common";
    int tier = 1;
    int stack = 1;
    /** True when a deepsteel-tempered version exists (id + "_tempered"), crafted on Magitech. */
    boolean temper;
    /** Short line for the tooltip that says what the item is for. */
    String blurb = "";
    /** Attribute-based melee: {damage, speed}. */
    Map<String, Object> melee;
    /** Right-click behaviour: {kind, ...numbers}. */
    Map<String, Object> use;
    /** On-melee-hit behaviour: {kind, ...numbers}. */
    Map<String, Object> hit;
    /** What each use costs: {item, per} or {charge}. */
    Map<String, Object> ammo;
    /** Set on a power cell: {charges}, how much charge one cell gives an energy weapon. */
    Map<String, Object> cell;
    /** Used only by the generator. */
    Object recipe;
    List<String> tags;

    boolean isAmmoOnly() {
        return melee == null && use == null && hit == null;
    }
}
