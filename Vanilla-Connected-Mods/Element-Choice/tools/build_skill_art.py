#!/usr/bin/env python3
"""Builds the skill-tree screens: backgrounds, node overlays, font, tooltip frame and the layout the mod reads.

usage: build_skill_art.py            (needs Pillow: pip install pillow)
Writes src/main/resources/assets/skilltree/** and src/main/resources/skill-layout.json next to this tool.
The screens are 9x6 chest menus. Their background is a bitmap glyph drawn through the menu title (font skilltree:gui),
so vanilla clients show it with nothing installed but the resource pack. Slot (row, col) is centred at pixel
(16 + 18 col, 26 + 18 row); the player's inventory is drawn into the art too.
"""
import json
import math
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from PIL import Image, ImageDraw, ImageFilter

from skill_art import *  # noqa: F401,F403
from skill_emblems import emblem

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "skilltree")
CATALOG = os.path.join(ROOT, "..", "java", "com", "silver", "elementchoice", "skills", "AbilityCatalog.java")
SCREEN_GLYPH = {"map": "", "air": "", "water": "", "earth": "", "fire": ""}
REALMS = ("air", "water", "earth", "fire")
TITLES = {"map": "THE PATH OF ASCENSION", "air": "THE DRAGON  -  AIR", "water": "THE TORTOISE  -  WATER",
          "earth": "THE TIGER  -  EARTH", "fire": "THE PHOENIX  -  FIRE"}
GROUP_COLOR = {"ICE": "water", "HEALING": "water", "BLOOD": "water", "METAL": "earth", "COMBUSTION": "fire",
               "AIR_MASTERY": "air", "LAVA": "fire", "LIGHTNING": "air", "AVATAR": "gold"}
GROUP_NAME = {"ICE": "Ice", "HEALING": "Healing", "BLOOD": "Blood", "METAL": "Metal", "COMBUSTION": "Combustion",
              "AIR_MASTERY": "Air Mastery", "LAVA": "Lava", "LIGHTNING": "Lightning", "AVATAR": "Ascendant"}
GROUP_REQ = {"ICE": {"water": 10}, "HEALING": {"water": 10}, "BLOOD": {"water": 15}, "METAL": {"earth": 10},
             "COMBUSTION": {"fire": 15}, "AIR_MASTERY": {"air": 10}, "LAVA": {"earth": 12, "fire": 8},
             "LIGHTNING": {"fire": 10, "air": 10}, "AVATAR": {"air": 15, "water": 15, "earth": 15, "fire": 15}}


def out(path, image):
    full = os.path.join(ASSETS, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    image.save(full)


def write_json(path, data, base=ASSETS):
    full = os.path.join(base, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as handle:
        json.dump(data, handle, indent=2)


def read_catalog():
    pattern = re.compile(r'(free|defense|base|grouped)\("(\w+)", "([^"]+)", Realm\.(\w+)(?:, Group\.(\w+))?')
    abilities = []
    with open(CATALOG) as source:
        for line in source:
            match = pattern.search(line)
            if match:
                kind, ident, display, realm, group = match.groups()
                abilities.append({"id": ident, "name": display, "realm": realm.lower(), "kind": kind, "group": group or "BASE"})
    return abilities


def shown_on(abilities, realm):
    out_list = []
    for a in abilities:
        req = GROUP_REQ.get(a["group"], {})
        if a["realm"] == realm or (len(req) == 2 and realm in req):
            out_list.append(a)
    return out_list


# ---------------------------------------------------------------- layouts

def realm_layout(abilities, realm):
    items = shown_on(abilities, realm)
    free = [a for a in items if a["kind"] in ("free", "defense")]
    base = [a for a in items if a["kind"] == "base"]
    grouped = [a for a in items if a["kind"] == "grouped"]
    nodes, links = [], []

    def put(a, row, col, kind="ability", color=None):
        node = {"id": a["id"], "kind": kind, "row": row, "col": col,
                "color": color or (GROUP_COLOR.get(a["group"], realm) if a["group"] != "BASE" else realm)}
        if a["group"] != "BASE":
            node["group"] = a["group"]
        nodes.append(node)

    nodes.append({"id": "root", "kind": "root", "row": 5, "col": 4, "color": realm})
    nodes.append({"id": "back", "kind": "back", "row": 5, "col": 0, "color": "gold"})
    nodes.append({"id": "points", "kind": "points", "row": 5, "col": 8, "color": "gold"})

    def row_cols(count, lo=1, hi=7):
        mid = 4
        cols = sorted(range(lo, hi + 1), key=lambda c: (abs(c - mid), c))[:count]
        return sorted(cols)

    for a, col in zip(free, row_cols(len(free), 2, 6)):
        put(a, 4, col)
        links.append(["root", a["id"]])
    placed = {}
    rows = [3, 2]
    index = 0
    for row in rows:
        take = base[index:index + 7]
        for a, col in zip(take, row_cols(len(take))):
            put(a, row, col)
            placed[a["id"]] = (row, col)
        index += 7
    by_pos = {(n["row"], n["col"]): n["id"] for n in nodes if n["kind"] == "ability"}

    def nearest_below(row, col):
        for r in range(row + 1, 5):
            cands = [(abs(c - col), c) for (rr, c) in by_pos if rr == r]
            if cands:
                _, c = min(cands)
                return by_pos[(r, c)]
        return "root"

    for a in base:
        r, c = placed[a["id"]]
        links.append([nearest_below(r, c), a["id"]])
    # groups fill rows 1 then 0, each group contiguous
    slots = [(1, c) for c in range(1, 8)] + [(0, c) for c in range(1, 8)]
    cursor = 0
    anchor = {}
    for a in grouped:
        row, col = slots[cursor]
        cursor += 1
        put(a, row, col)
        by_pos[(row, col)] = a["id"]
        first = anchor.get(a["group"])
        if first is None:
            anchor[a["group"]] = a["id"]
            links.append([nearest_below(row, col), a["id"]])
        else:
            links.append([first, a["id"]])
    return {"nodes": nodes, "links": links}


MAP_NODES = [
    {"id": "water", "kind": "realm", "row": 0, "col": 4, "color": "water"},
    {"id": "air", "kind": "realm", "row": 2, "col": 8, "color": "air"},
    {"id": "fire", "kind": "realm", "row": 5, "col": 4, "color": "fire"},
    {"id": "earth", "kind": "realm", "row": 2, "col": 0, "color": "earth"},
    {"id": "ascendant", "kind": "group", "group": "AVATAR", "row": 2, "col": 4, "color": "gold"},
    {"id": "g:blood", "kind": "group", "group": "BLOOD", "row": 1, "col": 4, "color": "water"},
    {"id": "g:ice", "kind": "group", "group": "ICE", "row": 1, "col": 3, "color": "water"},
    {"id": "g:healing", "kind": "group", "group": "HEALING", "row": 1, "col": 5, "color": "water"},
    {"id": "g:metal", "kind": "group", "group": "METAL", "row": 2, "col": 2, "color": "earth"},
    {"id": "g:air_mastery", "kind": "group", "group": "AIR_MASTERY", "row": 2, "col": 6, "color": "air"},
    {"id": "g:combustion", "kind": "group", "group": "COMBUSTION", "row": 4, "col": 4, "color": "fire"},
    {"id": "g:lava", "kind": "group", "group": "LAVA", "row": 4, "col": 2, "color": "fire"},
    {"id": "g:lightning", "kind": "group", "group": "LIGHTNING", "row": 4, "col": 6, "color": "air"},
    {"id": "points", "kind": "points", "row": 0, "col": 0, "color": "gold"},
    {"id": "resilience", "kind": "resilience", "row": 0, "col": 8, "color": "gold"},
]


# ---------------------------------------------------------------- screens

def center(node):
    return (cx(node["col"]), cy(node["row"]))


def draw_nodes(base, nodes, abilities_by_id):
    for node in nodes:
        x, y = center(node)
        color = COLORS[node["color"]]
        kind = node["kind"]
        if kind == "realm":
            base.alpha_composite(emblem(node["id"], color, 30 * SS), (int((x - 15) * SS), int((y - 15) * SS)))
        elif kind == "group":
            medallion(base, x, y, 8.4, color, GROUP_SYMBOLS[node["group"]])
        elif kind == "points":
            medallion(base, x, y, 8.4, color, sym_points)
        elif kind == "resilience":
            medallion(base, x, y, 8.4, color, sym_shield)
        elif kind == "back":
            medallion(base, x, y, 8.4, color, sym_back)
        elif kind == "root":
            base.alpha_composite(emblem(node["color"], color, 30 * SS), (int((x - 15) * SS), int((y - 15) * SS)))
        else:
            medallion(base, x, y, 8.4, color, sigil(node["id"]))


def map_screen():
    blobs = [(cx(4), cy(0), 34, COLORS["water"], 90), (cx(8), cy(2), 34, COLORS["air"], 70),
             (cx(4), cy(5), 34, COLORS["fire"], 90), (cx(0), cy(2), 34, COLORS["earth"], 90),
             (cx(4), cy(2), 26, COLORS["gold"], 70)]
    base = starfield(7, blobs)
    centre = (cx(4), cy(2))
    for radius, alpha in ((20, 150), (38, 110), (55, 80)):
        ring(base, centre, radius, COLORS["gold"], 0.6, alpha)
    ring(base, centre, 62, COLORS["gold"], 0.5, 90, dashes=48)
    for i in range(24):
        a = math.tau * i / 24
        r0, r1 = 55, 58 if i % 2 else 60
        ImageDraw.Draw(base).line([(centre[0] + r0 * math.cos(a)) * SS, (centre[1] + r0 * math.sin(a)) * SS,
                                   (centre[0] + r1 * math.cos(a)) * SS, (centre[1] + r1 * math.sin(a)) * SS],
                                  fill=COLORS["gold"] + (120,), width=SS // 2)
    node = {n["id"]: n for n in MAP_NODES}
    spokes = {"water": ["g:blood", "water"], "air": ["g:air_mastery", "air"], "earth": ["g:metal", "earth"],
              "fire": ["g:combustion", "fire"]}
    for realm, chain in spokes.items():
        pts = [centre] + [center(node[i]) for i in chain]
        glow_line(base, pts, COLORS[realm], 1.5, 2.4, smooth=False)
    glow_line(base, [center(node["earth"]), center(node["g:lava"]), center(node["fire"])], mix(COLORS["earth"], COLORS["fire"], 0.5), 1.0, 2.0)
    glow_line(base, [center(node["fire"]), center(node["g:lightning"]), center(node["air"])], mix(COLORS["fire"], COLORS["air"], 0.5), 1.0, 2.0)
    glow_line(base, [center(node["g:blood"]), center(node["g:ice"])], COLORS["water"], 0.9, 1.6, smooth=False)
    glow_line(base, [center(node["g:blood"]), center(node["g:healing"])], COLORS["water"], 0.9, 1.6, smooth=False)
    header(base, TITLES["map"], COLORS["gold"])
    inventory_panel(base)
    draw_nodes(base, MAP_NODES, {})
    ornate_frame(base, COLORS["gold"])
    return finish(base), {"nodes": MAP_NODES, "links": []}


def group_labels(base, nodes, realm_color):
    """A small caption above each family of abilities."""
    font = ImageFont.truetype(SANS, 6 * SS)
    d = ImageDraw.Draw(base)
    seen = {}
    for node in nodes:
        group = node.get("group")
        if group and group not in seen:
            seen[group] = [n for n in nodes if n.get("group") == group]
    for group, members in seen.items():
        xs = [cx(n["col"]) for n in members]
        row = members[0]["row"]
        label = GROUP_NAME[group].upper()
        box = d.textbbox((0, 0), label, font=font)
        x = (min(xs) + max(xs)) / 2 * SS - (box[2] - box[0]) / 2
        y = (cy(row) - 14.5) * SS
        d.text((x, y), label, font=font, fill=mix(COLORS[GROUP_COLOR[group]], (255, 255, 255), 0.3) + (230,))


def realm_screen(abilities, realm):
    color = COLORS[realm]
    layout = realm_layout(abilities, realm)
    blobs = [(cx(4), cy(5), 40, color, 110), (cx(4), cy(2), 50, color, 40), (cx(1), cy(0), 24, shade(color, 0.7), 40),
             (cx(7), cy(0), 24, shade(color, 0.7), 40)]
    base = starfield(hash(realm) % 1000, blobs)
    pos = {n["id"]: center(n) for n in layout["nodes"]}
    mark = emblem(realm, color, 118 * SS)
    mark.putalpha(mark.getchannel("A").point(lambda v: v * 28 // 100))
    base.alpha_composite(mark, (int((88 - 59) * SS), int((66 - 59) * SS)))
    group_labels(base, layout["nodes"], color)
    for a, b in layout["links"]:
        glow_line(base, [pos[a], pos[b]], color if a == "root" else mix(color, COLORS["gold"], 0.25), 1.2, 1.8, smooth=False)
    ring(base, pos["root"], 15, color, 0.7, 150)
    ring(base, pos["root"], 19, COLORS["gold"], 0.5, 90, dashes=24)
    header(base, TITLES[realm], color)
    inventory_panel(base)
    draw_nodes(base, layout["nodes"], {})
    ornate_frame(base, color)
    return finish(base), layout


# ---------------------------------------------------------------- overlays, font, tooltip

def overlays():
    """16x16 item textures laid over each baked node: a dark veil, a thin ring (can buy) and a bright ring (owned)."""
    size = 16
    for name, color in COLORS.items():
        for state in ("open", "owned"):
            img = Image.new("RGBA", (size * 8, size * 8), (0, 0, 0, 0))
            d = ImageDraw.Draw(img)
            r = size * 8 / 2
            if state == "owned":
                glow_img = Image.new("RGBA", img.size, (0, 0, 0, 0))
                ImageDraw.Draw(glow_img).ellipse([8, 8, size * 8 - 9, size * 8 - 9], outline=color + (255,), width=18)
                img.alpha_composite(glow_img.filter(ImageFilter.GaussianBlur(10)))
                d.ellipse([10, 10, size * 8 - 11, size * 8 - 11], outline=mix(color, (255, 255, 255), 0.35) + (255,), width=11)
                d.ellipse([22, 22, size * 8 - 23, size * 8 - 23], outline=COLORS["gold"] + (255,), width=4)
            else:
                d.ellipse([10, 10, size * 8 - 11, size * 8 - 11], outline=mix(color, (255, 255, 255), 0.2) + (230,), width=6)
            out(f"textures/item/ring_{name}_{state}.png", img.resize((size, size), Image.LANCZOS))
    veil = Image.new("RGBA", (size * 8, size * 8), (0, 0, 0, 0))
    ImageDraw.Draw(veil).ellipse([6, 6, size * 8 - 7, size * 8 - 7], fill=(6, 4, 14, 175))
    out("textures/item/veil.png", veil.resize((size, size), Image.LANCZOS))
    names = [f"ring_{c}_{s}" for c in COLORS for s in ("open", "owned")] + ["veil"]
    for name in names:
        write_json(f"models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"skilltree:item/{name}"}})
        write_json(f"items/{name}.json", {"model": {"type": "minecraft:model", "model": f"skilltree:item/{name}"}})


def font(screens):
    providers = [{"type": "space", "advances": {"": -8, "": -176}}]
    for name, glyph in SCREEN_GLYPH.items():
        out(f"textures/font/{name}.png", screens[name])
        providers.append({"type": "bitmap", "file": f"skilltree:font/{name}.png", "ascent": 13, "height": 222, "chars": [glyph]})
    write_json("font/gui.json", {"providers": providers})


def tooltip():
    size = 16
    bg = Image.new("RGBA", (size, size), (12, 9, 24, 245))
    d = ImageDraw.Draw(bg)
    d.rectangle([0, 0, size - 1, size - 1], outline=(30, 24, 56, 255))
    out("textures/gui/sprites/tooltip/skill_background.png", bg)
    frame = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rectangle([0, 0, size - 1, size - 1], outline=COLORS["gold"] + (255,))
    d.rectangle([2, 2, size - 3, size - 3], outline=shade(COLORS["gold"], 0.45) + (255,))
    out("textures/gui/sprites/tooltip/skill_frame.png", frame)
    for name in ("skill_background", "skill_frame"):
        write_json(f"textures/gui/sprites/tooltip/{name}.png.mcmeta",
                   {"gui": {"scaling": {"type": "nine_slice", "width": size, "height": size, "border": 4}}})


def main():
    abilities = read_catalog()
    screens, layouts = {}, {}
    screens["map"], layouts["map"] = map_screen()
    for realm in REALMS:
        screens[realm], layouts[realm] = realm_screen(abilities, realm)
    overlays()
    font(screens)
    tooltip()
    write_json("../../skill-layout.json", {"glyphs": SCREEN_GLYPH, "screens": layouts,
                                           "groups": {k: {"name": GROUP_NAME[k], "needs": GROUP_REQ[k]} for k in GROUP_NAME}}, ASSETS)
    # a preview sheet for a quick look (not shipped)
    preview = Image.new("RGBA", (W * 3 + 8, H * 2 + 4), (0, 0, 0, 255))
    for i, name in enumerate(("map", "air", "water", "earth", "fire")):
        preview.paste(screens[name], ((i % 3) * (W + 4), (i // 3) * (H + 4)))
    preview.save(os.path.join(os.path.dirname(os.path.abspath(__file__)), "preview.png"))
    print("skill art written:", len(abilities), "abilities")


if __name__ == "__main__":
    main()
