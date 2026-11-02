#!/usr/bin/env python3
"""Draws the Ascension portal: a spiky, glowing, floating oval. Run from anywhere; writes into src/main/resources/assets/gaterites.

Per element there is an oval (animated swirl + bright rim) and a spiky corona that turns behind it (animated too, so the
server never has to move anything). The surface and corona textures are animated strips. They are
grey-white so the item definition can tint them per element; the Avatar versions carry their own rainbow.
"""
import colorsys, json, math, os, struct, zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gaterites")
SIZE = 64
FRAMES = 8
CORONA_FRAMES = 12
CX = CY = (SIZE - 1) / 2
RX, RY = 26.0, 30.5          # the oval, in pixels
TINTS = {"air": 0xE3F1FF, "water": 0x3E9BFF, "earth": 0x8FD14F, "fire": 0xFF7A1A}


def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw += bytes(pixels[y][x])
    def chunk(tag, data):
        body = struct.pack(">I", len(data)) + tag + data
        return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as out:
        out.write(png)


def clamp(v, lo=0.0, hi=1.0):
    return max(lo, min(hi, v))


def rainbow(h, s=0.75, v=1.0):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def polar(x, y):
    dx, dy = x - CX, y - CY
    e = math.hypot(dx / RX, dy / RY)
    return dx, dy, e, math.atan2(dy / RY, dx / RX)


def surface_frame(k, avatar):
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            dx, dy, e, theta = polar(x, y)
            if e > 1.0:
                row.append((0, 0, 0, 0))
                continue
            arms = 0.5 + 0.5 * math.sin(3 * theta + 5.5 * e - 2 * math.pi * k / FRAMES)
            fine = 0.5 + 0.5 * math.sin(7 * theta - 9.0 * e + 2 * math.pi * k * 2 / FRAMES)
            v = clamp(0.7 * arms + 0.3 * fine)
            light = 0.30 + 0.70 * v + 0.45 * max(0.0, 1.0 - e / 0.22) ** 2
            alpha = (0.50 + 0.38 * v) * clamp((1.0 - e) / 0.10)
            if avatar:
                r, g, b = rainbow(theta / (2 * math.pi) + e * 0.6 - k / FRAMES, 0.55, clamp(light))
            else:
                grey = int(255 * clamp(light))
                r = g = b = grey
            row.append((r, g, b, int(255 * clamp(alpha))))
        pixels.append(row)
    return pixels


def ring(avatar):
    pixels = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            dx, dy, e, theta = polar(x, y)
            core = clamp(1.0 - abs(e - 0.95) / 0.07)               # the bright band
            glow_in = clamp((e - 0.70) / 0.25) * (1.0 if e <= 0.95 else 0.0) * 0.35
            glow_out = clamp(1.0 - (e - 1.0) / 0.16) * (1.0 if e > 1.0 else 0.0) * 0.55
            beads = 0.80 + 0.20 * math.cos(theta * 18)
            alpha = clamp(max(core * beads, glow_in, glow_out))
            if alpha <= 0.02:
                row.append((0, 0, 0, 0))
                continue
            if avatar:
                r, g, b = rainbow(theta / (2 * math.pi), 0.45, 1.0)
            else:
                r = g = b = 255
            row.append((r, g, b, int(255 * alpha)))
        pixels.append(row)
    return pixels


def corona(avatar, k):
    """One frame of the spinning corona; CORONA_FRAMES frames turn it by two spikes, which looks exactly like the first."""
    spikes = 14
    pixels = []
    shift = (k / CORONA_FRAMES) * 2 * (2 * math.pi / spikes)
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            dx, dy = x - CX, y - CY
            r = math.hypot(dx, dy)
            phi = math.atan2(dy, dx) + math.pi / 2 - shift
            pitch = 2 * math.pi / spikes
            index = round(phi / pitch)
            t = abs(phi - index * pitch) / (pitch / 2)         # 0 on a spike's axis, 1 between spikes
            tip = 31.4 if index % 2 == 0 else 25.0              # long and short spikes alternate
            base = 21.0
            reach = base + (tip - base) * (1.0 - t) ** 1.5
            if r > reach or r < base - 3:
                row.append((0, 0, 0, 0))
                continue
            along = clamp((r - (base - 3)) / (tip - base + 3))
            light = 1.0 - 0.45 * along
            alpha = 0.95 - 0.35 * along
            if avatar:
                cr, cg, cb = rainbow(phi / (2 * math.pi) + along * 0.15, 0.7, light)
            else:
                cr = cg = cb = int(255 * light)
            row.append((cr, cg, cb, int(255 * alpha)))
        pixels.append(row)
    return pixels


def textures():
    out = os.path.join(ROOT, "textures", "item")
    for avatar, suffix in ((False, ""), (True, "_avatar")):
        frames = [surface_frame(k, avatar) for k in range(FRAMES)]
        strip = [row for frame in frames for row in frame]
        write_png(os.path.join(out, f"portal_surface{suffix}.png"), SIZE, SIZE * FRAMES, strip)
        with open(os.path.join(out, f"portal_surface{suffix}.png.mcmeta"), "w") as meta:
            json.dump({"animation": {"frametime": 3}}, meta)
        write_png(os.path.join(out, f"portal_ring{suffix}.png"), SIZE, SIZE, ring(avatar))
        spin = [row for k in range(CORONA_FRAMES) for row in corona(avatar, k)]
        write_png(os.path.join(out, f"portal_corona{suffix}.png"), SIZE, SIZE * CORONA_FRAMES, spin)
        with open(os.path.join(out, f"portal_corona{suffix}.png.mcmeta"), "w") as meta:
            json.dump({"animation": {"frametime": 3}}, meta)


def plane(texture, z):
    face = {"uv": [0, 0, 16, 16], "texture": "#0", "tintindex": 0}
    return {"textures": {"0": texture, "particle": texture},
            "elements": [{"from": [0, 0, z], "to": [16, 16, z],
                          "faces": {"north": face, "south": face}}]}


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as out:
        json.dump(data, out, indent=1)
        out.write("\n")


def models():
    models_dir = os.path.join(ROOT, "models", "item")
    for suffix in ("", "_avatar"):
        write_json(os.path.join(models_dir, f"portal_surface{suffix}.json"), plane(f"gaterites:item/portal_surface{suffix}", 8.0))
        write_json(os.path.join(models_dir, f"portal_ring{suffix}.json"), plane(f"gaterites:item/portal_ring{suffix}", 8.3))
        write_json(os.path.join(models_dir, f"portal_corona{suffix}.json"), plane(f"gaterites:item/portal_corona{suffix}", 8.0))


def items():
    items_dir = os.path.join(ROOT, "items")
    def model(name, tint=None):
        entry = {"type": "minecraft:model", "model": f"gaterites:item/{name}"}
        if tint is not None:
            entry["tints"] = [{"type": "minecraft:constant", "value": tint}]
        return entry
    for realm, tint in TINTS.items():
        write_json(os.path.join(items_dir, f"portal_{realm}_oval.json"),
                   {"model": {"type": "minecraft:composite", "models": [model("portal_surface", tint), model("portal_ring", tint)]}})
        write_json(os.path.join(items_dir, f"portal_{realm}_corona.json"), {"model": model("portal_corona", tint)})
    # untinted faces of the avatar models still ask for tint 0, so give them white
    write_json(os.path.join(items_dir, "portal_avatar_oval.json"),
               {"model": {"type": "minecraft:composite", "models": [model("portal_surface_avatar", 0xFFFFFF), model("portal_ring_avatar", 0xFFFFFF)]}})
    write_json(os.path.join(items_dir, "portal_avatar_corona.json"), {"model": model("portal_corona_avatar", 0xFFFFFF)})


if __name__ == "__main__":
    textures()
    models()
    items()
    print("portal art written to", os.path.normpath(ROOT))
