#!/usr/bin/env python3
"""Writes the Foundry Arms recipes: steel swords and armor in three tiers, made from PolyFactory materials.
Tiers are gated by what they cost (steel plate -> integrated circuits -> netherite and large gears), which in turn needs
the machines of the tech story, so nobody skips ahead. Tune the numbers below, then run: python3 generate.py"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "data/techarms/recipe")
PF = "polyfactory:"

# tier: (display name, colour, sword base item, sword attack damage bonus, sword durability,
#        armor base prefix, armor points per piece (head, chest, legs, feet), toughness per piece, knockback resistance per piece,
#        durability multiplier)
TIERS = {
    "steel": ("Steel", "gray", "iron", 6.5, 1000, "iron", (3, 6, 5, 2), 1.0, 0.0, 22),
    "reinforced": ("Reinforced Steel", "aqua", "diamond", 9.0, 1700, "diamond", (4, 7, 6, 3), 2.0, 0.0, 38),
    "industrial": ("Industrial Steel", "gold", "netherite", 12.0, 2800, "netherite", (5, 8, 7, 4), 3.0, 0.1, 50),
}
ARMOR = [("helmet", "head", 11), ("chestplate", "chest", 16), ("leggings", "legs", 15), ("boots", "feet", 13)]

def name(text, colour):
    return {"text": text, "color": colour, "italic": False}

def sword(tier):
    label, colour, base, damage, durability, *_ = TIERS[tier]
    return {"id": f"minecraft:{base}_sword", "count": 1, "components": {
        "minecraft:custom_name": name(f"{label} Sword", colour),
        "minecraft:max_damage": durability,
        "minecraft:attribute_modifiers": [
            {"type": "minecraft:attack_damage", "id": f"techarms:{tier}_sword_damage", "amount": damage, "operation": "add_value", "slot": "mainhand"},
            {"type": "minecraft:attack_speed", "id": f"techarms:{tier}_sword_speed", "amount": -2.4, "operation": "add_value", "slot": "mainhand"}],
        "minecraft:custom_data": {"id": f"techarms_{tier}_sword"}}}

def armor(tier, piece, slot, base_durability):
    label, colour, _, _, _, prefix, points, toughness, knockback, multiplier = TIERS[tier]
    index = [p[0] for p in ARMOR].index(piece)
    modifiers = [
        {"type": "minecraft:armor", "id": f"techarms:{tier}_{piece}_armor", "amount": points[index], "operation": "add_value", "slot": slot},
        {"type": "minecraft:armor_toughness", "id": f"techarms:{tier}_{piece}_toughness", "amount": toughness, "operation": "add_value", "slot": slot}]
    if knockback:
        modifiers.append({"type": "minecraft:knockback_resistance", "id": f"techarms:{tier}_{piece}_knockback", "amount": knockback, "operation": "add_value", "slot": slot})
    return {"id": f"minecraft:{prefix}_{piece}", "count": 1, "components": {
        "minecraft:custom_name": name(f"{label} {piece.capitalize()}", colour),
        "minecraft:max_damage": base_durability * multiplier,
        "minecraft:attribute_modifiers": modifiers,
        "minecraft:custom_data": {"id": f"techarms_{tier}_{piece}"}}}

# Shapes: P steel plate, C integrated circuit, D diamond, N netherite ingot, G large steel gear, T stick
SWORD_SHAPES = {
    "steel": (["P", "P", "T"], {"P": PF + "steel_plate", "T": "minecraft:stick"}),
    "reinforced": ([" D ", "PCP", " T "], {"P": PF + "steel_plate", "C": PF + "integrated_circuit", "D": "minecraft:diamond", "T": "minecraft:stick"}),
    "industrial": ([" N ", "GCG", " T "], {"N": "minecraft:netherite_ingot", "G": PF + "large_steel_gear", "C": PF + "integrated_circuit", "T": "minecraft:stick"}),
}
ARMOR_SHAPES = {
    "steel": {"helmet": ["PPP", "P P"], "chestplate": ["P P", "PPP", "PPP"], "leggings": ["PPP", "P P", "P P"], "boots": ["P P", "P P"]},
    "reinforced": {"helmet": ["PPP", "PCP"], "chestplate": ["PCP", "PPP", "PPP"], "leggings": ["PPP", "PCP", "P P"], "boots": ["PCP", "P P"]},
    "industrial": {"helmet": ["NNN", "PCP"], "chestplate": ["NCN", "PNP", "PPP"], "leggings": ["NNN", "PCP", "P P"], "boots": ["NCN", "P P"]},
}
ARMOR_KEYS = {
    "steel": {"P": PF + "steel_plate"},
    "reinforced": {"P": PF + "steel_plate", "C": PF + "integrated_circuit"},
    "industrial": {"P": PF + "steel_plate", "C": PF + "integrated_circuit", "N": "minecraft:netherite_ingot"},
}

def write(file, recipe):
    with open(os.path.join(OUT, file + ".json"), "w") as f:
        json.dump(recipe, f, indent=2)
        f.write("\n")

def main():
    os.makedirs(OUT, exist_ok=True)
    for old in os.listdir(OUT):
        os.remove(os.path.join(OUT, old))
    for tier in TIERS:
        pattern, key = SWORD_SHAPES[tier]
        write(f"{tier}_sword", {"type": "minecraft:crafting_shaped", "category": "equipment", "pattern": pattern, "key": key, "result": sword(tier)})
        for piece, slot, durability in ARMOR:
            write(f"{tier}_{piece}", {"type": "minecraft:crafting_shaped", "category": "equipment",
                                      "pattern": ARMOR_SHAPES[tier][piece], "key": ARMOR_KEYS[tier],
                                      "result": armor(tier, piece, slot, durability)})
    with open(os.path.join(HERE, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"pack_format": 48, "min_format": 48, "max_format": [107, 1], "supported_formats": [48, 107],
                            "description": "Foundry Arms: steel weapons and armor for the Tech server"}}, f, indent=2)

if __name__ == "__main__":
    main()
