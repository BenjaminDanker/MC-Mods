#!/usr/bin/env python3
"""Writes one 'find <structure>' advancement per Ruins and Towers structure, so Element-Choice pays skill points for
exploring them (the mod itself only has two find advancements). Uses the same location+structures check the mod uses.
Run from this folder: python3 generate.py"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "data/rntexplore/advancement")
# id, icon, title, frame
STRUCTURES = [
    ("archeology_site", "minecraft:brush", "Buried Past", "task"),
    ("badlands_temple", "minecraft:terracotta", "Temple in the Badlands", "task"),
    ("calcite_obelisc", "minecraft:calcite", "The White Obelisk", "goal"),
    ("chilling_challenge_tower", "minecraft:blue_ice", "The Chilling Tower", "goal"),
    ("creeper_shrine", "minecraft:creeper_head", "Creeper Shrine", "task"),
    ("dark_obelisk", "minecraft:crying_obsidian", "The Dark Obelisk", "goal"),
    ("dry_challenge_tower", "minecraft:sandstone", "The Dry Tower", "goal"),
    ("generic_stone_ruins", "minecraft:cobblestone", "Old Stone Ruins", "task"),
    ("pale_outpost", "minecraft:pale_oak_log", "Pale Outpost", "goal"),
    ("pale_trench", "minecraft:pale_moss_block", "The Pale Trench", "goal"),
    ("prismarine_cage", "minecraft:prismarine", "Prismarine Cage", "task"),
    ("scott_cabin", "minecraft:oak_log", "A Lonely Cabin", "task"),
    ("sculk_chimney", "minecraft:sculk", "The Sculk Chimney", "task"),
    ("stonehenge", "minecraft:stone", "Standing Stones", "goal"),
    ("stony_obelisk", "minecraft:stone_bricks", "The Stony Obelisk", "task"),
    ("swamp_challenge_tower", "minecraft:mud_bricks", "The Swamp Tower", "goal"),
]

def write(name, obj):
    with open(os.path.join(OUT, name + ".json"), "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")

os.makedirs(OUT, exist_ok=True)
write("root", {"display": {"icon": {"id": "minecraft:chiseled_stone_bricks"}, "title": {"text": "Ruins and Towers"},
                           "description": {"text": "Find the forgotten places of the world."},
                           "background": "minecraft:gui/advancements/backgrounds/stone", "frame": "task",
                           "show_toast": False, "announce_to_chat": False, "hidden": False},
               "criteria": {"tick": {"trigger": "minecraft:tick"}}, "requirements": [["tick"]]})
for sid, icon, title, frame in STRUCTURES:
    write(sid, {"display": {"icon": {"id": icon}, "title": {"text": title},
                            "description": {"text": f"Find {title}."},
                            "frame": frame, "show_toast": True, "announce_to_chat": False, "hidden": False},
                "parent": "rntexplore:root",
                "criteria": {"found": {"trigger": "minecraft:location", "conditions": {"player": [{
                    "condition": "minecraft:entity_properties", "entity": "this",
                    "predicate": {"location": {"structures": f"rnt:{sid}"}}}]}}},
                "requirements": [["found"]]})
with open(os.path.join(HERE, "pack.mcmeta"), "w") as f:
    json.dump({"pack": {"pack_format": 48, "min_format": 48, "max_format": [107, 1], "supported_formats": [48, 107],
                        "description": "Find-the-structure advancements for Ruins and Towers"}}, f, indent=2)
