#!/usr/bin/env python3
"""Draws every Tech Arsenal icon (see sprites.py) and writes textures plus, with --preview, a contact sheet.

Held weapons are drawn on the up-right diagonal because the 'handheld' model turns them to point forward in the hand.
usage: icons.py [--preview /path/sheet.png]
"""
import os
import sys

from sprites import Canvas, contact_sheet, hexc, write_png

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "src", "main", "resources", "assets", "arsenal", "textures", "item")

DRAW = {}


def icon(fn):
    DRAW[fn.__name__] = fn
    return fn


# ------------------------------------------------------------------ melee
@icon
def vibro_blade():
    c = Canvas()
    c.line(6, 9, 12, 3, "steel", width=2)
    c.line(7, 8, 12, 3, "cyan", tone=2)
    c.p(13, 2, "white", 3)
    c.line(4, 8, 7, 11, "brass")
    c.line(4, 11, 2, 13, "grip")
    c.p(2, 13, "brass")
    return c


@icon
def combat_knife():
    c = Canvas()
    c.line(7, 8, 12, 3, "steel", width=2)
    c.line(8, 8, 12, 4, "white", tone=2)
    c.p(13, 2, "white", 3)
    c.line(5, 8, 8, 11, "dark")
    c.line(5, 11, 3, 13, "grip", width=1)
    c.line(6, 10, 4, 12, "grip")
    c.p(3, 13, "brass")
    return c


@icon
def chain_cleaver():
    c = Canvas()
    c.line(3, 12, 8, 7, "wood")
    c.rect(8, 2, 12, 7, "steel")
    c.rect(13, 2, 13, 6, "white", 2)
    c.rect(8, 2, 8, 7, "dark")
    c.p(10, 4, "dark", 0)
    c.p(10, 3, "white", 3)
    for i, (x, y) in enumerate([(5, 13), (6, 12), (7, 12), (8, 11), (9, 10)]):
        c.p(x, y, "white" if i % 2 else "dark", 2)
    c.p(2, 13, "brass")
    return c


@icon
def pipe_hammer():
    c = Canvas()
    c.line(3, 12, 9, 6, "dark")
    c.p(5, 10, "copper", 2)
    c.p(7, 8, "copper", 2)
    c.rect(8, 2, 13, 6, "steel")
    c.rect(8, 2, 8, 6, "copper")
    c.rect(13, 2, 13, 6, "copper")
    c.line(9, 4, 12, 4, "dark")
    c.p(10, 3, "white", 3)
    c.p(2, 13, "grip")
    return c


@icon
def shock_baton():
    c = Canvas()
    c.line(3, 12, 8, 7, "grip", width=1)
    c.p(5, 10, "yellow", 2)
    c.p(6, 9, "yellow", 2)
    c.line(9, 6, 12, 3, "steel", width=1)
    c.p(13, 2, "yellow", 3)
    c.p(12, 2, "yellow", 2)
    c.p(14, 4, "yellow", 2)
    c.p(11, 1, "yellow", 1)
    c.p(2, 13, "brass")
    return c


@icon
def arc_saber():
    c = Canvas()
    c.line(3, 12, 5, 10, "copper")
    c.line(4, 8, 7, 11, "brass")
    c.line(6, 9, 12, 3, "yellow", tone=2, width=2)
    c.line(7, 8, 12, 3, "white", tone=3)
    c.p(13, 2, "white", 3)
    c.p(10, 2, "yellow", 2)
    c.p(13, 6, "yellow", 2)
    c.p(8, 4, "yellow", 1)
    c.p(2, 13, "brass")
    return c


@icon
def piston_gauntlets():
    c = Canvas()
    c.rect(4, 6, 10, 11, "steel")
    c.rect(4, 8, 10, 8, "dark")
    c.rect(4, 10, 10, 10, "dark")
    c.rect(2, 7, 3, 10, "grip")
    for y in (6, 8, 10):
        c.p(11, y, "copper", 2)
    c.rect(5, 4, 10, 5, "copper")
    c.rect(11, 4, 13, 4, "steel")
    c.p(6, 7, "white", 3)
    return c


@icon
def monowire_whip():
    c = Canvas()
    c.line(3, 12, 6, 9, "grip")
    c.p(2, 13, "brass")
    c.p(7, 8, "brass")
    path = [(8, 7), (9, 6), (10, 6), (11, 5), (12, 4), (12, 3), (11, 2), (10, 2), (10, 3), (11, 3)]
    for x, y in path:
        c.p(x, y, "cyan", 2)
    c.p(11, 3, "cyan", 3)
    c.p(10, 3, "white", 3)
    return c


@icon
def thermal_lance():
    c = Canvas()
    c.line(2, 13, 10, 5, "steel")
    c.p(4, 11, "dark", 1)
    c.p(6, 9, "orange", 2)
    c.p(8, 7, "orange", 2)
    c.line(10, 5, 12, 3, "orange", tone=2, width=2)
    c.p(13, 2, "yellow", 3)
    c.p(12, 2, "orange", 3)
    c.p(13, 3, "orange", 2)
    return c


@icon
def rotary_cutter():
    c = Canvas()
    c.rect(3, 8, 7, 12, "red")
    c.rect(2, 11, 3, 13, "grip")
    c.line(7, 9, 13, 3, "steel", width=2)
    for x, y in [(8, 7), (10, 5), (12, 3)]:
        c.p(x, y, "dark", 1)
    c.p(4, 9, "orange", 2)
    c.p(5, 9, "yellow", 2)
    c.rect(4, 7, 5, 7, "dark")
    return c


# ------------------------------------------------------------------ guns
# Drawn pointing up-right with the grip hanging down-right of the body, so the handheld model carries them like a gun.
@icon
def rivet_pistol():
    c = Canvas()
    c.line(4, 9, 11, 2, "steel", width=2)          # slide
    c.line(5, 11, 9, 7, "dark")                       # frame under the slide
    c.line(12, 1, 13, 0, "dark")
    c.line(7, 10, 9, 12, "wood", width=2)             # grip
    c.line(10, 9, 11, 10, "dark")                     # trigger guard
    c.p(8, 5, "copper", 2)                            # rivet feed
    c.p(9, 4, "copper", 2)
    c.p(6, 7, "white", 3)
    return c


@icon
def scatter_cannon():
    c = Canvas()
    c.line(3, 10, 11, 2, "steel", width=2)            # upper barrel
    c.line(4, 12, 12, 4, "dark", width=2)             # lower barrel
    c.rect(12, 1, 13, 2, "dark")
    c.line(2, 11, 1, 12, "wood", width=1)
    c.line(5, 12, 3, 14, "wood", width=2)             # stock
    c.line(7, 11, 8, 12, "copper", width=1)           # pump
    c.p(5, 8, "white", 3)
    return c


@icon
def flare_gun():
    c = Canvas()
    c.line(4, 9, 10, 3, "red", width=2)
    c.rect(11, 1, 13, 3, "dark")
    c.p(12, 2, "orange", 3)
    c.p(13, 0, "yellow", 3)
    c.p(14, 1, "orange", 2)
    c.line(6, 10, 8, 12, "wood", width=2)
    c.line(9, 9, 10, 10, "dark")
    c.p(6, 6, "white", 3)
    return c


@icon
def coil_rifle():
    c = Canvas()
    c.line(3, 11, 12, 2, "steel", width=1)
    c.line(4, 12, 13, 3, "dark", width=1)
    for x, y in [(6, 8), (8, 6), (10, 4)]:
        c.p(x, y, "copper", 2)
        c.p(x + 1, y + 1, "copper", 1)
    c.line(2, 12, 0, 14, "wood", width=2)             # stock
    c.line(7, 10, 8, 11, "grip", width=2)             # grip
    c.rect(9, 8, 10, 10, "dark", 2)                   # magazine
    c.p(13, 1, "cyan", 3)
    c.p(7, 7, "cyan", 3)
    return c


@icon
def arc_caster():
    c = Canvas()
    c.line(4, 9, 9, 4, "blue", width=2)
    c.p(10, 3, "steel", 2)
    c.p(11, 2, "steel", 2)
    c.p(12, 1, "steel", 2)
    c.p(12, 4, "steel", 2)
    c.p(13, 3, "steel", 2)
    c.p(12, 2, "yellow", 3)
    c.p(13, 2, "yellow", 2)
    c.p(14, 1, "yellow", 2)
    c.line(6, 10, 8, 12, "wood", width=2)
    c.p(6, 7, "cyan", 3)
    c.p(7, 6, "cyan", 2)
    return c


@icon
def taser():
    c = Canvas()
    c.line(5, 9, 9, 5, "brass", width=2)
    c.p(10, 4, "dark", 1)
    c.p(11, 3, "steel", 2)
    c.p(12, 2, "steel", 2)
    c.p(12, 5, "steel", 2)
    c.p(13, 4, "steel", 2)
    c.p(12, 3, "yellow", 3)
    c.p(13, 3, "yellow", 2)
    c.line(6, 10, 8, 12, "wood", width=2)
    c.p(7, 7, "white", 3)
    return c


@icon
def flame_projector():
    c = Canvas()
    c.rect(3, 8, 7, 13, "red")                        # fuel tank under the body
    c.rect(4, 7, 6, 7, "dark")
    c.line(7, 9, 11, 5, "steel", width=2)
    c.rect(12, 3, 13, 4, "dark")
    c.p(14, 2, "orange", 2)
    c.p(14, 3, "yellow", 3)
    c.p(13, 1, "orange", 3)
    c.p(14, 0, "yellow", 2)
    c.line(8, 11, 9, 12, "grip", width=1)
    c.p(4, 9, "white", 3)
    return c


@icon
def grenade_launcher():
    c = Canvas()
    c.line(3, 10, 10, 3, "steel", width=2)
    c.line(4, 11, 11, 4, "dark", width=2)
    c.rect(11, 1, 13, 3, "dark")
    c.rect(6, 8, 8, 10, "dark")                       # drum
    c.p(7, 9, "brass", 3)
    c.line(4, 12, 2, 14, "wood", width=2)             # stock
    c.line(8, 11, 9, 12, "grip", width=1)
    c.p(5, 7, "white", 3)
    return c


@icon
def harpoon_gun():
    c = Canvas()
    c.line(4, 10, 9, 5, "steel", width=2)
    c.p(10, 4, "steel", 2)
    c.p(11, 3, "white", 2)
    c.p(12, 2, "white", 3)
    c.p(13, 1, "white", 3)
    c.p(11, 2, "steel", 1)
    c.p(12, 3, "steel", 1)
    c.rect(6, 10, 8, 12, "copper")                    # line reel
    c.p(7, 11, "brass", 3)
    c.line(4, 11, 3, 12, "grip", width=1)
    c.p(8, 7, "cyan", 3)
    c.p(6, 7, "white", 3)
    return c


@icon
def rail_lance():
    c = Canvas()
    c.line(3, 11, 13, 1, "dark", width=1)
    c.line(4, 12, 14, 2, "steel", width=1)
    c.line(5, 10, 12, 3, "magenta", tone=2)
    c.p(13, 0, "magenta", 3)
    c.p(14, 1, "white", 3)
    c.line(2, 12, 1, 13, "wood", width=1)
    c.line(6, 11, 8, 13, "wood", width=2)
    c.p(9, 8, "cyan", 2)
    return c


@icon
def rocket_launcher():
    c = Canvas()
    c.line(3, 11, 11, 3, "green", width=2)
    c.line(4, 12, 12, 4, "dark", width=2)
    c.rect(12, 1, 13, 3, "dark")
    c.rect(1, 11, 2, 13, "dark")
    c.p(7, 6, "cyan", 3)
    c.p(8, 5, "white", 3)
    c.line(6, 11, 8, 13, "wood", width=2)
    c.p(5, 8, "white", 3)
    return c


# ------------------------------------------------------------------ gadgets
@icon
def grapple_gun():
    c = Canvas()
    c.line(4, 9, 10, 3, "steel", width=2)
    c.line(5, 11, 9, 7, "dark")
    c.line(6, 10, 8, 12, "wood", width=2)
    c.line(9, 9, 10, 10, "dark")
    c.p(11, 2, "copper", 2)
    c.p(12, 1, "copper", 3)
    c.p(13, 2, "copper", 2)
    c.p(13, 3, "copper", 1)
    c.p(12, 3, "copper", 1)
    c.p(7, 5, "white", 3)
    c.p(8, 9, "white", 2)
    return c


@icon
def smoke_grenade():
    c = Canvas()
    c.rect(5, 5, 10, 12, "steel")
    c.rect(5, 8, 10, 8, "white", 2)
    c.rect(5, 9, 10, 9, "dark")
    c.rect(6, 3, 9, 4, "dark")
    c.p(11, 2, "steel", 2)
    c.p(10, 3, "steel", 2)
    c.p(12, 3, "brass", 2)
    c.p(7, 6, "white", 3)
    return c


@icon
def emp_grenade():
    c = Canvas()
    c.rect(5, 5, 10, 11, "blue")
    c.rect(6, 4, 9, 4, "blue")
    c.rect(6, 12, 9, 12, "blue")
    c.rect(6, 2, 9, 3, "dark")
    for x, y in [(8, 6), (7, 7), (8, 8), (9, 9), (8, 10)]:
        c.p(x, y, "cyan", 3)
    c.p(6, 6, "white", 3)
    return c


@icon
def stim_pack():
    c = Canvas()
    c.line(4, 11, 10, 5, "white", width=2)
    c.line(5, 11, 9, 7, "green", tone=2)
    c.line(11, 4, 13, 2, "steel", width=1)
    c.p(3, 12, "red", 2)
    c.p(4, 13, "red", 2)
    c.p(2, 12, "red", 1)
    c.line(5, 9, 7, 11, "white", tone=1)
    return c


@icon
def proximity_mine():
    c = Canvas()
    c.rect(3, 9, 12, 12, "dark")
    c.rect(4, 7, 11, 8, "dark")
    c.rect(5, 6, 10, 6, "dark")
    c.rect(4, 11, 11, 11, "steel", 1)
    c.rect(7, 8, 8, 9, "red", 2)
    c.p(7, 8, "red", 3)
    c.p(3, 9, "copper", 2)
    c.p(12, 9, "copper", 2)
    c.p(5, 7, "white", 3)
    return c


@icon
def barrier_emitter():
    c = Canvas()
    c.rect(5, 5, 11, 11, "steel")
    c.rect(6, 6, 10, 10, "dark")
    c.rect(7, 7, 9, 9, "cyan", 2)
    c.p(8, 8, "white", 3)
    c.rect(3, 11, 4, 13, "grip")
    c.p(5, 5, "white", 3)
    return c


@icon
def sentry_turret():
    c = Canvas()
    c.line(4, 13, 7, 9, "steel")
    c.line(10, 13, 8, 9, "steel")
    c.p(7, 13, "steel", 2)
    c.rect(5, 6, 10, 9, "dark")
    c.rect(6, 4, 9, 5, "steel")
    c.line(10, 5, 13, 5, "steel", width=1)
    c.rect(13, 4, 14, 6, "dark")
    c.p(7, 7, "yellow", 3)
    c.p(6, 5, "white", 3)
    return c


# ------------------------------------------------------------------ ammunition (drawn upright, centred)
@icon
def rivets():
    c = Canvas()
    for x, y in [(4, 9), (8, 10), (6, 6), (10, 6)]:
        c.rect(x, y, x + 2, y + 2, "copper")
        c.p(x, y, "brass", 3)
        c.p(x + 1, y + 1, "copper", 3)
    return c


@icon
def shells():
    c = Canvas()
    for x in (4, 8):
        c.rect(x, 4, x + 2, 9, "red")
        c.rect(x, 10, x + 2, 12, "brass")
        c.rect(x, 4, x + 2, 4, "red", 3)
    return c


@icon
def coil_slug():
    c = Canvas()
    c.rect(6, 7, 9, 12, "copper")
    c.rect(7, 4, 8, 6, "cyan", 2)
    c.p(7, 3, "cyan", 3)
    c.rect(6, 12, 9, 12, "brass")
    c.p(7, 8, "copper", 3)
    return c


@icon
def rail_slug():
    c = Canvas()
    c.rect(7, 2, 8, 13, "steel")
    c.rect(7, 2, 8, 4, "magenta", 2)
    c.p(7, 2, "white", 3)
    c.rect(6, 11, 9, 13, "dark")
    return c


@icon
def flare():
    c = Canvas()
    c.rect(5, 6, 10, 12, "red")
    c.rect(5, 5, 10, 5, "orange", 1)
    c.rect(7, 2, 8, 4, "yellow", 2)
    c.p(7, 1, "orange", 3)
    c.rect(5, 12, 10, 12, "dark")
    c.p(6, 7, "red", 3)
    return c


@icon
def grenade_round():
    c = Canvas()
    c.rect(5, 6, 10, 11, "green")
    c.rect(6, 4, 9, 5, "green")
    c.rect(7, 3, 8, 3, "green", 2)
    c.rect(5, 12, 10, 13, "brass")
    c.rect(5, 8, 10, 8, "yellow", 1)
    c.p(6, 6, "green", 3)
    return c


@icon
def rocket():
    c = Canvas()
    c.rect(6, 5, 9, 11, "steel")
    c.rect(7, 1, 8, 4, "red", 2)
    c.rect(6, 4, 9, 4, "red", 1)
    c.rect(4, 10, 5, 12, "dark")
    c.rect(10, 10, 11, 12, "dark")
    c.rect(7, 12, 8, 14, "orange", 2)
    c.p(7, 13, "yellow", 3)
    c.p(6, 6, "white", 3)
    return c


@icon
def power_cell():
    c = Canvas()
    c.rect(4, 4, 11, 13, "dark")
    c.rect(6, 2, 9, 3, "copper")
    for x, y in [(8, 6), (7, 7), (8, 8), (7, 9), (8, 10)]:
        c.p(x, y, "yellow", 3)
    c.p(5, 5, "white", 3)
    return c


@icon
def ley_cell():
    c = Canvas()
    c.rect(4, 4, 11, 13, "dark")
    c.rect(6, 2, 9, 3, "copper")
    for x, y in [(8, 6), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8), (9, 8), (8, 9)]:
        c.p(x, y, "magenta", 2)
    c.p(7, 7, "magenta", 3)
    c.p(8, 6, "white", 3)
    c.p(8, 11, "cyan", 2)
    c.p(5, 5, "white", 3)
    return c


@icon
def fuel_cell():
    c = Canvas()
    c.rect(5, 5, 10, 13, "orange", 1)
    c.rect(5, 5, 10, 6, "dark")
    c.rect(5, 12, 10, 13, "dark")
    c.rect(7, 2, 8, 4, "steel")
    c.p(6, 8, "yellow", 3)
    c.p(6, 7, "yellow", 2)
    return c


@icon
def harpoon_bolt():
    c = Canvas()
    c.line(3, 12, 11, 4, "steel")
    c.line(11, 4, 13, 2, "white", width=2)
    c.p(10, 2, "steel", 2)
    c.p(13, 5, "steel", 2)
    c.p(3, 12, "cyan", 3)
    c.p(4, 11, "cyan", 2)
    c.p(2, 13, "cyan", 1)
    return c


def temper(pixels):
    """The deepsteel version of an icon: steel tones become blue-black deepsteel, glows turn to embers."""
    ramp = [hexc("#0e1018"), hexc("#2b3350"), hexc("#46547d"), hexc("#98abd8")]
    ember = hexc("#ff8a2a")
    out = []
    for row in pixels:
        new = []
        for r, g, b, a in row:
            if a == 0:
                new.append((r, g, b, a))
                continue
            if (r, g, b, a) == (11, 13, 18, 255):
                new.append((r, g, b, a))
                continue
            lum = (0.3 * r + 0.59 * g + 0.11 * b) / 255.0
            sat = (max(r, g, b) - min(r, g, b)) / max(1, max(r, g, b))
            if sat > 0.5 and lum > 0.45:
                new.append(ember)
                continue
            t = min(0.999, lum * 1.15) * (len(ramp) - 1)
            i = int(t)
            f = t - i
            lo, hi = ramp[i], ramp[min(i + 1, len(ramp) - 1)]
            new.append(tuple(int(lo[k] + (hi[k] - lo[k]) * f) for k in range(3)) + (255,))
        out.append(new)
    return out


def box(frm, to, face):
    uv = {"dark": [0, 0, 8, 8], "steel": [8, 0, 16, 8], "copper": [0, 8, 8, 16], "glow": [8, 8, 16, 16]}[face]
    return {"from": frm, "to": to, "faces": {side: {"uv": uv, "texture": "#0"} for side in ("north", "south", "east", "west", "up", "down")}}


def models():
    """Two small 3D models for things that stand in the world. The barrel points south (+z), the way a display faces at yaw 0."""
    import json
    base = os.path.join(HERE, "..", "src", "main", "resources", "assets", "arsenal")
    palette = Canvas()
    quadrants = [((0, 0), "dark", 1), ((8, 0), "steel", 2), ((0, 8), "copper", 2), ((8, 8), "yellow", 3)]
    pixels = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    for (ox, oy), mat, tone in quadrants:
        color = hexc(__import__("sprites").RAMPS[mat][tone])
        for y in range(8):
            for x in range(8):
                pixels[oy + y][ox + x] = color
    write_png(os.path.join(TEX, "palette.png"), pixels)
    turret = [
        box([3, 0, 3], [13, 1, 13], "dark"),
        box([7, 1, 7], [9, 6, 9], "steel"),
        box([4, 6, 4], [12, 11, 12], "dark"),
        box([4, 6, 4], [12, 7, 12], "steel"),
        box([3, 7, 5], [4, 10, 11], "steel"),
        box([12, 7, 5], [13, 10, 11], "steel"),
        box([7, 8, 12], [9, 10, 19], "steel"),
        box([7.5, 8.5, 19], [8.5, 9.5, 20], "dark"),
        box([10, 9, 11.9], [11.5, 10.5, 12.1], "glow"),
        box([5, 11, 6], [11, 12, 9], "copper"),
    ]
    mine = [
        box([4, 0, 5], [12, 1, 11], "dark"),
        box([5, 0, 4], [11, 1, 12], "dark"),
        box([5, 1, 6], [11, 2, 10], "steel"),
        box([6, 2, 7], [10, 3, 9], "dark"),
        box([7.5, 3, 7.5], [8.5, 3.6, 8.5], "glow"),
        box([3, 0, 7.5], [4, 1, 8.5], "copper"),
        box([12, 0, 7.5], [13, 1, 8.5], "copper"),
        box([7.5, 0, 3], [8.5, 1, 4], "copper"),
        box([7.5, 0, 12], [8.5, 1, 13], "copper"),
    ]
    for name, elements in (("sentry_turret_deployed", turret), ("proximity_mine_placed", mine)):
        os.makedirs(os.path.join(base, "models", "item"), exist_ok=True)
        with open(os.path.join(base, "models", "item", name + ".json"), "w") as out:
            json.dump({"textures": {"0": "arsenal:item/palette", "particle": "arsenal:item/palette"}, "elements": elements}, out, indent=1)
            out.write("\n")
        os.makedirs(os.path.join(base, "items"), exist_ok=True)
        with open(os.path.join(base, "items", name + ".json"), "w") as out:
            json.dump({"model": {"type": "minecraft:model", "model": "arsenal:item/" + name}}, out, indent=1)
            out.write("\n")


def build(selected=None):
    sprites = {}
    for name, fn in DRAW.items():
        if selected and name not in selected:
            continue
        sprites[name] = fn().render()
    return sprites


if __name__ == "__main__":
    args = sys.argv[1:]
    preview = None
    if "--preview" in args:
        preview = args[args.index("--preview") + 1]
    sprites = build()
    if preview:
        import json
        arms = {a["id"]: a for a in json.load(open(os.path.join(HERE, "..", "src", "main", "resources", "data", "arsenal", "arms.json")))["arms"]}
        shown = list(sprites.values()) + [temper(sprites[n]) for n in sprites if arms[n].get("temper")]
        contact_sheet(shown, [], preview, columns=12, scale=8)
        print(len(sprites), "icons +", len(shown) - len(sprites), "tempered")
    else:
        os.makedirs(TEX, exist_ok=True)
        import json
        arms = {a["id"]: a for a in json.load(open(os.path.join(HERE, "..", "src", "main", "resources", "data", "arsenal", "arms.json")))["arms"]}
        missing = [i for i in arms if i not in sprites]
        extra = [n for n in sprites if n not in arms]
        assert not missing, "no icon drawn for " + ", ".join(missing)
        assert not extra, "icon without an arm: " + ", ".join(extra)
        count = 0
        for name, pixels in sprites.items():
            write_png(os.path.join(TEX, name + ".png"), pixels)
            count += 1
            if arms[name].get("temper"):
                write_png(os.path.join(TEX, name + "_tempered.png"), temper(pixels))
                count += 1
        models()
        print(count, "icons written, plus the turret and mine models")
