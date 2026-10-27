#!/usr/bin/env python3
"""Rewrites the Cold Foundry story (advancements, chapter functions, check loop) from the table below.
Edit CHAPTERS / HOURS to tune length; then run:  python3 generate.py   (from this folder)

Each chapter needs (1) every listed PolyFactory advancement, which the player earns by building and running
the machines themselves, and (2) the previous chapter. The chapters themselves can only be granted by this loop,
so there is no final item anyone can hand over."""
import json, os

PF = "polyfactory:main/"
# id, icon, title, description, PolyFactory advancements required, hours of play time on the server, story line
CHAPTERS = [
    ("crank", "minecraft:lever", "A Hand on the Crank", "Turn the first crank. Every machine here began as muscle.", ["base/hand_crank"], 0,
     "The crank is stiff, and then it turns. Somewhere under the floor, something answers."),
    ("steel", "minecraft:iron_ingot", "The Age of Steel", "Forge steel. The foundry was built from it, and so will you rebuild it.", ["base/steel_ingot", "base/press"], 0,
     "Steel, bright and heavy. The old engineers never trusted anything softer."),
    ("steam", "minecraft:blast_furnace", "Pressure and Steam", "Fire a steam engine and let heat do the lifting.", ["base/steam_engine"], 0,
     "The engine shudders, and the whole workshop hums back. It remembers being alive."),
    ("miner", "minecraft:iron_pickaxe", "Let the Machines Dig", "Put a miner to work. The old foundry fed on ore.", ["base/miner"], 0,
     "Ore keeps coming up out of the dark. The foundry was always hungry."),
    ("smelting", "minecraft:furnace", "Fire in the Belly", "Bring the industrial smeltery to life.", ["smeltery/primitive_smeltery", "smeltery/smeltery"], 0,
     "Metal runs, red and slow, the way it did before anyone forgot how."),
    ("casting", "minecraft:anvil", "Poured and Set", "Make molds and cast with a casting table.", ["smeltery/casting_table", "smeltery/mold"], 0,
     "Every part is a shape first. Cast it, and it is real."),
    ("logistics", "minecraft:chest", "Everything in Its Place", "Pack and move items without touching them.", ["base/item_packer", "base/container"], 0,
     "Crates stack themselves and belts hum. Nothing gets carried by hand any more."),
    ("fluids", "minecraft:bucket", "Pipes in the Walls", "Store and move liquids through tanks, drains and spouts.", ["base/fluid_tank", "base/drain", "base/mechanical_spout"], 0,
     "Liquid finds its way through iron the way it always wanted to."),
    ("brewing", "minecraft:brewing_stand", "Patient Chemistry", "Ferment, and make biodiesel from the leavings.", ["base/fermenters", "base/biodiesel"], 0,
     "The vats bubble. Nothing here is wasted, only waiting to become something else."),
    ("logic", "minecraft:repeater", "Machines That Think", "Give a machine memory, so it can decide for itself.", ["base/data_memory", "base/data_comparator", "base/arithmetic_operator"], 0,
     "A counter ticks, a comparator clicks. The foundry is learning to keep its own books."),
    ("signals", "minecraft:redstone_torch", "Words Without Wires", "Send signals across the foundry without a cable between.", ["base/wireless_redstone", "base/redstone"], 0,
     "A signal crosses an empty room and a door opens on the far side."),
    ("automation", "minecraft:crafter", "Hands of Their Own", "Build a crafter that feeds another crafter.", ["base/crafter", "base/crafter2"], 0,
     "Parts assemble parts. You stop being the one who builds, and start being the one who designs."),
    ("projection", "minecraft:sea_lantern", "Light From Nothing", "Project a hologram and wire up nixie tubes.", ["base/hologram_projector", "base/nixie_tube"], 0,
     "The old plans hang in the air, glowing, every line exactly where it was."),
    ("vault", "minecraft:ender_chest", "The Deep Vault", "Build deep storage. Nothing the foundry makes should ever be lost again.", ["base/deep_storage_container", "base/container"], 0,
     "The last door seals itself. All that is left is to light the fire."),
]
FINAL_HOURS = 0
# Foundry Arms recipes (tech-arms-datapack) shown in the recipe book when a chapter opens; what they cost is the real gate.
ARMS = {"steel": "Steel", "logic": "Reinforced Steel", "vault": "Industrial Steel"}
ARMS_TIER = {"steel": "steel", "logic": "reinforced", "vault": "industrial"}
ARMS_PIECES = ["sword", "helmet", "chestplate", "leggings", "boots"]
TICKS_PER_HOUR = 72000
HERE = os.path.dirname(os.path.abspath(__file__))
ADV = os.path.join(HERE, "data/techstory/advancement")
FUN = os.path.join(HERE, "data/techstory/function")

def hours_text(h):
    if h == 0:
        return ""
    return f" Needs {h:g} hour{'s' if h != 1 else ''} of work in the foundry (play time on this server)."

def write(path, obj):
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")

def main():
    for name in os.listdir(FUN):
        if name.startswith("chapter_"):
            os.remove(os.path.join(FUN, name))
    previous = "root"
    check = ["# Generated by generate.py. Runs once a second.",
             "# A chapter opens when its machines are built and run and the previous chapter is done."]
    for i, (cid, icon, title, desc, gates, hours, story) in enumerate(CHAPTERS):
        write(os.path.join(ADV, f"{cid}.json"), {
            "display": {"icon": {"id": icon}, "title": {"text": title}, "description": {"text": desc + hours_text(hours)},
                        "frame": "task", "show_toast": True, "announce_to_chat": True, "hidden": False},
            "parent": f"techstory:{previous}",
            "criteria": {"granted": {"trigger": "minecraft:impossible"}},
            "requirements": [["granted"]],
            "rewards": {"function": f"techstory:chapter_{cid}",
                        **({"recipes": [f"techarms:{ARMS_TIER[cid]}_{piece}" for piece in ARMS_PIECES]} if cid in ARMS else {})},
        })
        nxt = CHAPTERS[i + 1] if i + 1 < len(CHAPTERS) else None
        hint = f"Next: {nxt[2]}." if nxt else "Last step: light the foundry."
        with open(os.path.join(FUN, f"chapter_{cid}.mcfunction"), "w") as f:
            f.write(f'tellraw @s {{text:"{story}",color:"gray",italic:true}}\n')
            f.write(f'tellraw @s {{text:"{hint}",color:"dark_gray"}}\n')
            if cid in ARMS:
                f.write(f'tellraw @s {{text:"New recipes: {ARMS[cid]} sword and armor.",color:"gold"}}\n')
        conds = ",".join([f"techstory:{previous}=true"] + [f"{PF}{g}=true" for g in gates]) if previous != "root" \
            else ",".join(f"{PF}{g}=true" for g in gates)
        time = ""
        check.append(f"execute as @a[advancements={{{conds}}}] unless entity @s[advancements={{techstory:{cid}=true}}]{time} run advancement grant @s only techstory:{cid}")
        previous = cid
    write(os.path.join(ADV, "foundry_lit.json"), {
        "display": {"icon": {"id": "minecraft:beacon"}, "title": {"text": "The Foundry Lit"},
                    "description": {"text": "Every furnace is burning, every wire is live. The Cold Foundry is a foundry again." + hours_text(FINAL_HOURS)},
                    "frame": "challenge", "show_toast": True, "announce_to_chat": True, "hidden": False},
        "parent": f"techstory:{previous}",
        "criteria": {"granted": {"trigger": "minecraft:impossible"}}, "requirements": [["granted"]],
        "rewards": {"function": "techstory:complete", "recipes": ["gaterites:gate_core"]},
    })
    check.append(f"execute as @a[advancements={{techstory:{previous}=true}}] unless entity @s[advancements={{techstory:foundry_lit=true}}] run advancement grant @s only techstory:foundry_lit")
    check.append("schedule function techstory:check 20t")
    with open(os.path.join(FUN, "check.mcfunction"), "w") as f:
        f.write("\n".join(check) + "\n")
    with open(os.path.join(FUN, "load.mcfunction"), "w") as f:
        f.write("schedule function techstory:check 20t\n")
    root = json.load(open(os.path.join(ADV, "root.json")))
    total = sum(1 for _ in CHAPTERS) + 1
    root["display"]["description"] = {"text": f"A foundry sleeps under this world, cold and rusted. Rebuild it chapter by chapter: {total} chapters, and every machine built and run by your own hands."}
    write(os.path.join(ADV, "root.json"), root)

if __name__ == "__main__":
    main()
