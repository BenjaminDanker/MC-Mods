#!/usr/bin/env python3
"""The Tech Arsenal's single table. Run it to (re)write everything derived from it:

  src/main/resources/data/arsenal/arms.json       what the mod reads (numbers, behaviours, tooltips)
  src/main/resources/data/arsenal/recipe/*.json   how each item is crafted
  src/main/resources/assets/arsenal/lang/en_us.json
  src/main/resources/assets/arsenal/items/*.json  and models/item/*.json (textures come from icons.py)

Damage is measured against Bending (numbers in config/bending/bending.conf are already x2.5): a mage's best single ability does
about 3.3-3.8 damage per second at long range (Fire Blast 5 every 1.5s, Earth Shot 7.5 every 2s over 48 blocks), and a full
rotation of four or five abilities about 8-10, plus shields, mobility and crowd control that tech has no answer to. So a single
tech weapon is tuned to roughly 4-9 damage per second at its own best range, paying for it with ammunition and aim.
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "src", "main", "resources")

SYM = {
    # PolyFactory materials
    "P": "polyfactory:steel_plate", "G": "polyfactory:steel_gear", "g": "polyfactory:large_steel_gear",
    "N": "polyfactory:steel_nugget", "I": "polyfactory:steel_ingot", "M": "polyfactory:string_mesh",
    "C": "polyfactory:integrated_circuit", "R": "polyfactory:redstone_chip", "K": "polyfactory:copper_plate",
    "X": "polyfactory:generic_machine_part", "U": "polyfactory:sulfur_dust", "D": "polyfactory:coal_dust",
    "E": "polyfactory:ender_dust",
    # vanilla
    "s": "minecraft:stick", "p": "minecraft:gunpowder", "r": "minecraft:redstone", "t": "minecraft:string",
    "b": "minecraft:blaze_powder", "d": "minecraft:diamond", "h": "minecraft:hopper", "o": "minecraft:paper",
    "m": "minecraft:glistering_melon_slice", "y": "magitech:ley_crystal",
}

ARMS = []


def arm(id, name, tier, blurb="", *, rarity="common", stack=1, temper=False, melee=None, use=None, hit=None, ammo=None, cell=None, needs=(), recipe=None):
    ARMS.append(dict(id=id, name=name, tier=tier, blurb=blurb, rarity=rarity, stack=stack, temper=temper,
                     melee=melee, use=use, hit=hit, ammo=ammo, cell=cell, needs=list(needs), recipe=recipe))


def shaped(rows, count=1):
    return dict(type="shaped", rows=rows, count=count)


def shapeless(items, count=1):
    return dict(type="shapeless", items=items, count=count)


# ---------------------------------------------------------------- ammunition
arm("rivets", "Rivets", 1, "Pistol and harpoon ammunition.", stack=64, recipe=shapeless("NN", 24))
arm("shells", "Shotgun Shells", 1, "Scatter cannon ammunition.", stack=64, recipe=shapeless("ppKo", 6))
arm("coil_slug", "Coil Slug", 2, "Coil rifle ammunition.", stack=64, recipe=shapeless("IRK", 8))
arm("rail_slug", "Rail Slug", 3, "Rail lance ammunition.", stack=64, rarity="uncommon", recipe=shapeless("IERR", 4))
arm("flare", "Flare", 1, "Flare gun ammunition.", stack=64, recipe=shapeless("bop", 4))
arm("grenade_round", "Grenade Round", 2, "Grenade launcher ammunition.", stack=64, recipe=shapeless("PppU", 3))
arm("rocket", "Rocket", 3, "Rocket launcher ammunition.", stack=16, rarity="uncommon", recipe=shapeless("PPppUU", 2))
arm("power_cell", "Power Cell", 2, "Charges energy weapons: twelve charges.", stack=16, rarity="uncommon", cell=dict(charges=12),
    recipe=shapeless("KKRr", 1))
arm("ley_cell", "Ley-Charged Cell", 3, "A power cell with a Ley Crystal in it: twenty-four charges, twice as many.", stack=16, rarity="rare",
    cell=dict(charges=24), needs=["magitech"], recipe=shapeless(["@power_cell", "y"], 1))
arm("fuel_cell", "Fuel Cell", 2, "One burst for the flame projector.", stack=16, recipe=shapeless("DDDbN", 2))
arm("harpoon_bolt", "Harpoon Bolt", 2, "Harpoon gun ammunition.", stack=16, recipe=shapeless("INN", 4))

# ---------------------------------------------------------------- melee
arm("vibro_blade", "Vibro Blade", 1, "Every fourth hit lands half again as hard.", temper=True,
    melee=dict(damage=5.0, speed=1.9), hit=dict(kind="surge", every=4, bonus=0.5),
    recipe=shaped([" P ", " P ", "RGs"]))
arm("combat_knife", "Combat Knife", 1, "Fast, and far worse for whoever has their back turned.", temper=True,
    melee=dict(damage=2.8, speed=3.0), hit=dict(kind="backstab", mult=2.2),
    recipe=shaped(["  P", " PN", "sG "]))
arm("chain_cleaver", "Chain Cleaver", 1, "Heavy blows leave the target bleeding.", temper=True,
    melee=dict(damage=8.0, speed=0.9), hit=dict(kind="bleed", damage=1.0, pulses=4, every=20),
    recipe=shaped(["PP ", "PGs", " s "]))
arm("pipe_hammer", "Pipe Hammer", 1, "Right-click to leap and slam the ground.", temper=True,
    melee=dict(damage=9.0, speed=0.8), use=dict(kind="slam", cooldown=180, radius=3.6, damage=6.0, leap=0.95, knock=0.9),
    recipe=shaped(["III", "IGI", " s "]))
arm("shock_baton", "Shock Baton", 2, "Every third hit stuns.", temper=True,
    melee=dict(damage=4.0, speed=1.6), hit=dict(kind="stun", every=3, slow_ticks=30, slow_amp=2, weak_ticks=40),
    recipe=shaped(["  K", " sC", "s  "]))
arm("arc_saber", "Arc Saber", 2, "Each hit arcs to two enemies nearby.", rarity="uncommon", temper=True,
    melee=dict(damage=5.5, speed=1.7), hit=dict(kind="arc", targets=2, radius=4.5, frac=0.4),
    recipe=shapeless(["@vibro_blade", "C", "C", "K", "K"]))
arm("piston_gauntlets", "Piston Gauntlets", 2, "Every fifth punch hits like a piston.", rarity="uncommon", temper=True,
    melee=dict(damage=3.2, speed=3.0), hit=dict(kind="piston", every=5, bonus=3.0, knock=1.6),
    recipe=shaped(["PGP", "XCX", " P "]))
arm("monowire_whip", "Monowire Whip", 2, "Right-click to lash a target and drag it in.", rarity="uncommon", temper=True,
    melee=dict(damage=3.0, speed=1.2), use=dict(kind="lash", cooldown=60, range=7.5, damage=4.0, pull=1.0),
    recipe=shaped(["  K", " MC", "sM "]))
arm("thermal_lance", "Thermal Lance", 3, "Right-click to lunge forward through everything in the way.", rarity="rare", temper=True,
    melee=dict(damage=6.5, speed=1.1), use=dict(kind="lunge", cooldown=140, distance=7.0, damage=9.0, ignite=60, radius=1.5),
    recipe=shaped(["  g", " Ib", "s  "]))
arm("rotary_cutter", "Rotary Cutter", 3, "Right-click to rev: for five seconds every hit is stronger and bleeds.", rarity="rare", temper=True,
    melee=dict(damage=6.0, speed=1.4), use=dict(kind="rev", cooldown=240, ticks=100),
    hit=dict(kind="rev", bonus=0.25, bleed=1.0, pulses=3, every=20),
    recipe=shapeless(["@chain_cleaver", "g", "g", "C", "C"]))

# ---------------------------------------------------------------- guns
arm("rivet_pistol", "Rivet Pistol", 1, "A reliable sidearm.", temper=True,
    use=dict(kind="hitscan", damage=2.2, cooldown=8, range=32, spread=0.8, color="FFB347", sound="pistol"),
    ammo=dict(item="rivets", per=1), recipe=shaped(["PPP", "  G", " sR"]))
arm("scatter_cannon", "Scatter Cannon", 1, "Devastating up close, a nuisance beyond a dozen blocks.", temper=True,
    use=dict(kind="hitscan", damage=1.5, pellets=7, spread=6.0, range=18, falloff_start=4, falloff_end=14, falloff_min=0.25,
             knock=0.7, cooldown=24, color="C8C8C8", sound="shotgun"),
    ammo=dict(item="shells", per=1), recipe=shaped(["PPP", "GII", "  s"]))
arm("flare_gun", "Flare Gun", 1, "Sets a target alight and makes it glow for everyone to see.", temper=True,
    use=dict(kind="bolt", projectile="flare", speed=1.7, gravity=0.4, spread=1.0, cooldown=30, impact="flare",
             flare=dict(damage=2.5, ignite=100, glow=160, radius=2.5), trail="flame"),
    ammo=dict(item="flare", per=1), recipe=shaped(["PP ", "bPG", " s "]))
arm("coil_rifle", "Coil Rifle", 2, "Accurate to sixty-four blocks; the slug passes through a second target.", rarity="uncommon", temper=True,
    use=dict(kind="hitscan", damage=8.5, cooldown=34, range=64, pierce=1, spread=0.15, color="59E7FF", sound="rifle"),
    ammo=dict(item="coil_slug", per=1), recipe=shaped(["PPK", "CXI", " s "]))
arm("arc_caster", "Arc Caster", 2, "A bolt of lightning that jumps from target to target.", rarity="uncommon", temper=True,
    use=dict(kind="hitscan", damage=3.5, cooldown=20, range=20, spread=0.3, color="A8E6FF", sound="arc",
             chain=dict(jumps=3, radius=5.0, mult=0.7)),
    ammo=dict(charge=1, cell="power_cell"), recipe=shaped(["KRK", "CXC", " s "]))
arm("taser", "Taser", 2, "Short range. Slows and weakens whatever it hits.", rarity="uncommon", temper=True,
    use=dict(kind="hitscan", damage=1.5, cooldown=90, range=9, spread=0.2, color="FFFF66", sound="taser",
             effects=[dict(effect="slowness", ticks=40, amp=2), dict(effect="weakness", ticks=80, amp=0)]),
    ammo=dict(charge=4, cell="power_cell"), recipe=shaped(["KK ", "CRs", " s "]))
arm("flame_projector", "Flame Projector", 2, "A three-second burst of fire in a cone ahead of you.", rarity="uncommon", temper=True,
    use=dict(kind="stream", cooldown=90, duration=36, pulse=3, damage=1.1, range=7.0, half_angle=24.0, ignite=60),
    ammo=dict(item="fuel_cell", per=1), recipe=shaped(["PPb", "hXD", "  s"]))
arm("grenade_launcher", "Grenade Launcher", 2, "Lobs a grenade that bursts on impact.", rarity="uncommon", temper=True,
    use=dict(kind="bolt", projectile="grenade_round", speed=1.25, gravity=1.0, spread=1.5, cooldown=50, impact="blast",
             blast=dict(radius=3.2, damage=7.0, knock=0.8), trail="smoke"),
    ammo=dict(item="grenade_round", per=1), recipe=shaped(["PPP", "GXh", "  s"]))
arm("harpoon_gun", "Harpoon Gun", 2, "Spears a target and reels it in to you.", rarity="uncommon", temper=True,
    use=dict(kind="bolt", projectile="harpoon_bolt", speed=2.4, gravity=0.1, spread=0.3, cooldown=110, impact="harpoon",
             harpoon=dict(damage=5.0, pull=1.0), trail="crit"),
    ammo=dict(item="harpoon_bolt", per=1), recipe=shaped(["PIP", "GXt", "  s"]))
arm("rail_lance", "Rail Lance", 3, "One enormous shot through up to four targets.", rarity="rare", temper=True,
    use=dict(kind="hitscan", damage=16.0, cooldown=80, range=110, pierce=3, spread=0.0, knock=1.0, color="E05CFF", sound="rail"),
    ammo=dict(item="rail_slug", per=1), recipe=shapeless(["@coil_rifle", "g", "g", "E", "E", "d"]))
arm("rocket_launcher", "Rocket Launcher", 3, "A slow rocket with a very large bang.", rarity="rare", temper=True,
    use=dict(kind="bolt", projectile="rocket", speed=1.2, gravity=0.0, spread=0.5, cooldown=120, impact="blast",
             blast=dict(radius=4.2, damage=13.0, knock=1.1), trail="flame"),
    ammo=dict(item="rocket", per=1), recipe=shapeless(["@grenade_launcher", "g", "g", "X", "X"]))

# ---------------------------------------------------------------- gadgets
arm("grapple_gun", "Grapple Gun", 1, "Fires a line to the block you aim at and hauls you across.",
    use=dict(kind="grapple", cooldown=45, range=42, speed=1.1, max_ticks=30),
    recipe=shaped(["PPN", "GCt", "  s"]))
arm("smoke_grenade", "Smoke Grenade", 1, "Blinds and slows everyone in the cloud but you.", stack=8,
    use=dict(kind="bolt", projectile="smoke_grenade", speed=0.9, gravity=1.0, spread=1.0, cooldown=40, consume=True, impact="smoke",
             smoke=dict(radius=5.0, ticks=160)),
    recipe=shapeless("pDDUo", 2))
arm("emp_grenade", "EMP Grenade", 2, "Knocks every bender it reaches off their abilities for six seconds.", rarity="uncommon", stack=4,
    use=dict(kind="bolt", projectile="emp_grenade", speed=0.9, gravity=1.0, spread=1.0, cooldown=60, consume=True, impact="emp",
             emp=dict(radius=6.5, bender_ms=6000, slow_ticks=60)),
    recipe=shapeless("PCEKr", 1))
arm("stim_pack", "Stim Pack", 1, "Heals at once and keeps healing for a few seconds.", stack=4,
    use=dict(kind="stim", cooldown=40, heal=6.0, regen_ticks=100, regen_amp=1, consume=True),
    recipe=shapeless("KKmN", 2))
arm("proximity_mine", "Proximity Mine", 2, "Set it down; it arms after a moment and goes off for whoever comes near.", rarity="uncommon", stack=4,
    use=dict(kind="mine", cooldown=20, arm_ticks=30, trigger_radius=2.2, max=3, life=3600, consume=True,
             blast=dict(radius=3.4, damage=9.0, knock=0.9)),
    recipe=shapeless("PPCpp", 2))
arm("barrier_emitter", "Barrier Emitter", 2, "A burst of absorption and resistance, drawn from a power cell.", rarity="uncommon", temper=False,
    use=dict(kind="barrier", cooldown=800, absorption_amp=1, absorption_ticks=240, resistance_ticks=120),
    ammo=dict(charge=5, cell="power_cell"), recipe=shaped(["PCP", "KEK", " G "]))
arm("sentry_turret", "Sentry Turret", 3, "Deploys a turret that shoots anything hostile near it for twenty-five seconds.", rarity="rare",
    use=dict(kind="turret", cooldown=900, ticks=500, range=14.0, every=8, damage=1.6, max=1),
    ammo=dict(charge=6, cell="power_cell"), recipe=shaped(["PgP", "CXC", "PGP"]))

TEMPER_COST = {1: 2, 2: 3, 3: 4}


def strip_none(d):
    return {k: v for k, v in d.items() if v is not None}


def ingredient(symbol):
    if symbol.startswith("@"):
        return "arsenal:" + symbol[1:]
    return SYM[symbol]


def recipe_json(arm_, tempered=False):
    conditions = ["polyfactory"] + [n for n in arm_.get("needs", []) if n not in ("polyfactory",)]
    if tempered:
        if "magitech" not in conditions:
            conditions.append("magitech")
        items = ["arsenal:" + arm_["id"]] + ["magitech:deepsteel_ingot"] * TEMPER_COST[arm_["tier"]]
        body = dict(type="minecraft:crafting_shapeless", category="equipment", ingredients=items, result=dict(id="arsenal:" + arm_["id"] + "_tempered"))
    else:
        r = arm_["recipe"]
        result = dict(id="arsenal:" + arm_["id"])
        if r["count"] != 1:
            result["count"] = r["count"]
        if r["type"] == "shaped":
            used = sorted(set("".join(r["rows"])) - {" "})
            body = dict(type="minecraft:crafting_shaped", category="equipment", pattern=r["rows"],
                        key={k: ingredient(k) for k in used}, result=result)
        else:
            items = r["items"] if isinstance(r["items"], list) else list(r["items"])
            body = dict(type="minecraft:crafting_shapeless", category="equipment", ingredients=[ingredient(s) for s in items], result=result)
    out = {"fabric:load_conditions": [dict(condition="fabric:all_mods_loaded", values=conditions)]}
    out.update(body)
    return out


def validate():
    ids = [a["id"] for a in ARMS]
    assert len(ids) == len(set(ids)), "duplicate ids"
    patterns = {}
    for a in ARMS:
        r = a["recipe"]
        assert r is not None, a["id"]
        if r["type"] == "shaped":
            rows = r["rows"]
            assert 1 <= len(rows) <= 3 and all(len(row) == 3 for row in rows), (a["id"], rows)
            key = tuple(rows)
            assert key not in patterns, (a["id"], "same pattern as", patterns[key])
            patterns[key] = a["id"]
            for s in set("".join(rows)) - {" "}:
                assert s in SYM, (a["id"], s)
        else:
            items = list(r["items"]) if not isinstance(r["items"], str) else list(r["items"])
            assert 1 <= len(items) <= 9, a["id"]
            for s in items:
                assert s.startswith("@") and s[1:] in ids or s in SYM, (a["id"], s)
        for section in ("use", "hit", "ammo"):
            pass
        if a["ammo"] and "item" in a["ammo"]:
            assert a["ammo"]["item"] in ids, (a["id"], "ammo item unknown")
        if a["ammo"] and "cell" in a["ammo"]:
            assert a["ammo"]["cell"] in ids
        use = a["use"] or {}
        if use.get("kind") == "bolt":
            assert use["projectile"] in ids, (a["id"], "projectile item unknown")
    # shapeless recipes of different items must not be the same multiset (the game would pick one arbitrarily)
    seen = {}
    for a in ARMS:
        r = a["recipe"]
        if r["type"] == "shapeless":
            key = tuple(sorted(r["items"]))
            assert key not in seen, (a["id"], "same ingredients as", seen[key])
            seen[key] = a["id"]


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        json.dump(data, out, indent=2)
        out.write("\n")


def main():
    validate()
    arms_json = [strip_none(a) for a in ARMS]
    for a in arms_json:
        a.pop("needs", None)
        a["base"] = "minecraft:stick"
        a["recipe"] = None
        a.pop("recipe")
    write_json(os.path.join(RES, "data", "arsenal", "arms.json"), {"arms": arms_json})
    recipe_dir = os.path.join(RES, "data", "arsenal", "recipe")
    os.makedirs(recipe_dir, exist_ok=True)
    for name in os.listdir(recipe_dir):
        os.remove(os.path.join(recipe_dir, name))
    lang = {}
    for a in ARMS:
        write_json(os.path.join(recipe_dir, a["id"] + ".json"), recipe_json(a))
        lang["item.arsenal." + a["id"]] = a["name"]
        ids = [a["id"]]
        if a["temper"]:
            write_json(os.path.join(recipe_dir, a["id"] + "_tempered.json"), recipe_json(a, tempered=True))
            lang["item.arsenal." + a["id"] + "_tempered"] = "Tempered " + a["name"]
            ids.append(a["id"] + "_tempered")
        for item_id in ids:
            write_json(os.path.join(RES, "assets", "arsenal", "items", item_id + ".json"),
                       {"model": {"type": "minecraft:model", "model": "arsenal:item/" + item_id}})
            parent = "minecraft:item/handheld" if a["melee"] else "minecraft:item/generated"
            write_json(os.path.join(RES, "assets", "arsenal", "models", "item", item_id + ".json"),
                       {"parent": parent, "textures": {"layer0": "arsenal:item/" + item_id}})
    write_json(os.path.join(RES, "assets", "arsenal", "lang", "en_us.json"), lang)
    print(f"{len(ARMS)} arms, {sum(1 for a in ARMS if a['temper'])} with tempered versions")


if __name__ == "__main__":
    main()
