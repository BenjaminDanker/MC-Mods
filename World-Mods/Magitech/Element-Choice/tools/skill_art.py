"""Drawing helpers for the skill screens: palette, backdrops, animal emblems, group symbols and ability sigils.

Everything is drawn at SS times the final size and shrunk, so edges are smooth. Requires Pillow (pip install pillow).
"""
import math
import random

from PIL import Image, ImageChops, ImageDraw, ImageFilter, ImageFont

SS = 4
W, H = 176, 222
COLORS = {
    "water": (63, 169, 245),
    "air": (176, 218, 255),
    "earth": (141, 196, 68),
    "fire": (255, 106, 31),
    "gold": (222, 178, 76),
}
INK = (8, 6, 16)
SERIF = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
SANS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def cx(col):
    """Pixel centre of a container slot column (slots are 18 px apart, items 16 px wide)."""
    return 16 + 18 * col


def cy(row):
    return 26 + 18 * row


def mix(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def shade(color, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in color)


def layer(size=None):
    return Image.new("RGBA", size or (W * SS, H * SS), (0, 0, 0, 0))


def glow(image, radius, strength=1.0):
    blurred = image.filter(ImageFilter.GaussianBlur(radius * SS))
    if strength != 1.0:
        alpha = blurred.getchannel("A").point(lambda v: min(255, int(v * strength)))
        blurred.putalpha(alpha)
    return blurred


def paste(base, top):
    base.alpha_composite(top)


def radial(size, center, radius, inner, outer, power=1.0):
    """A radial gradient image (RGBA) from inner to outer colour, each (r, g, b, a)."""
    w, h = size
    img = Image.new("RGBA", size)
    px = img.load()
    for y in range(h):
        for x in range(w):
            d = min(1.0, math.hypot(x - center[0], y - center[1]) / radius) ** power
            px[x, y] = tuple(int(inner[i] + (outer[i] - inner[i]) * d) for i in range(4))
    return img


# ---------------------------------------------------------------- backdrop

def starfield(seed, tint_blobs):
    rnd = random.Random(seed)
    base = Image.new("RGBA", (W * SS, H * SS), INK + (255,))
    # vertical depth
    grad = Image.new("RGBA", (1, H * SS))
    for y in range(H * SS):
        t = y / (H * SS)
        grad.putpixel((0, y), mix((14, 11, 30), (5, 4, 12), t) + (255,))
    base.alpha_composite(grad.resize((W * SS, H * SS)))
    # soft coloured clouds
    clouds = layer()
    d = ImageDraw.Draw(clouds)
    for (x, y, r, color, alpha) in tint_blobs:
        d.ellipse([(x - r) * SS, (y - r) * SS, (x + r) * SS, (y + r) * SS], fill=color + (alpha,))
    base.alpha_composite(glow(clouds, 16))
    # stars
    stars = layer()
    d = ImageDraw.Draw(stars)
    for _ in range(230):
        x, y = rnd.uniform(2, W - 2), rnd.uniform(2, 128)
        b = rnd.choice((70, 90, 120, 160, 220))
        size = rnd.choice((0.5, 0.5, 0.7, 1.0))
        tint = rnd.choice(((255, 255, 255), (200, 220, 255), (255, 235, 200)))
        d.ellipse([(x - size) * SS, (y - size) * SS, (x + size) * SS, (y + size) * SS], fill=tint + (b,))
    for _ in range(7):
        x, y = rnd.uniform(6, W - 6), rnd.uniform(20, 120)
        d.line([(x - 3) * SS, y * SS, (x + 3) * SS, y * SS], fill=(255, 240, 210, 150), width=SS // 2)
        d.line([x * SS, (y - 3) * SS, x * SS, (y + 3) * SS], fill=(255, 240, 210, 150), width=SS // 2)
    base.alpha_composite(stars)
    return base


def ornate_frame(base, accent):
    """Gold border with corner ornaments around the whole screen."""
    d = ImageDraw.Draw(base)
    gold = COLORS["gold"]
    for inset, alpha, width in ((0.5, 255, 1.2), (3, 150, 0.7)):
        d.rectangle([inset * SS, inset * SS, (W - inset) * SS - 1, (H - inset) * SS - 1],
                    outline=gold + (alpha,), width=int(width * SS))
    for x, y, sx, sy in ((3, 3, 1, 1), (W - 3, 3, -1, 1), (3, H - 3, 1, -1), (W - 3, H - 3, -1, -1)):
        pts = [(x, y), (x + 10 * sx, y), (x + 10 * sx, y + 1 * sy), (x + 1 * sx, y + 1 * sy), (x + 1 * sx, y + 10 * sy), (x, y + 10 * sy)]
        d.polygon([(px * SS, py * SS) for px, py in pts], fill=gold + (255,))
        d.ellipse([(x + 4 * sx - 1.5) * SS, (y + 4 * sy - 1.5) * SS, (x + 4 * sx + 1.5) * SS, (y + 4 * sy + 1.5) * SS],
                  fill=mix(gold, accent, 0.5) + (255,))


def header(base, title, accent):
    d = ImageDraw.Draw(base)
    d.rectangle([0, 0, W * SS, 16 * SS], fill=(6, 5, 14, 235))
    d.line([6 * SS, 15.5 * SS, (W - 6) * SS, 15.5 * SS], fill=COLORS["gold"] + (200,), width=SS)
    d.line([6 * SS, 16.8 * SS, (W - 6) * SS, 16.8 * SS], fill=accent + (110,), width=SS // 2)
    font = ImageFont.truetype(SERIF, 8 * SS)
    box = d.textbbox((0, 0), title, font=font)
    x = (W * SS - (box[2] - box[0])) / 2
    shadow = layer()
    ImageDraw.Draw(shadow).text((x, 3.2 * SS), title, font=font, fill=accent + (255,))
    base.alpha_composite(glow(shadow, 2, 1.4))
    d.text((x, 3.2 * SS), title, font=font, fill=mix(COLORS["gold"], (255, 255, 255), 0.35) + (255,))
    # diamonds either side of the title
    for sx in (-1, 1):
        px = W * SS / 2 + sx * ((box[2] - box[0]) / 2 + 9 * SS)
        d.polygon([(px, 5.5 * SS), (px + 2.5 * SS, 8 * SS), (px, 10.5 * SS), (px - 2.5 * SS, 8 * SS)], fill=COLORS["gold"] + (255,))


def inventory_panel(base):
    """Dark panel with a frame for each player-inventory slot, so the player's own items sit in the art."""
    d = ImageDraw.Draw(base)
    d.rectangle([2 * SS, 127 * SS, (W - 2) * SS, (H - 2) * SS], fill=(9, 8, 18, 255))
    d.line([6 * SS, 128 * SS, (W - 6) * SS, 128 * SS], fill=COLORS["gold"] + (120,), width=SS // 2)
    for r in range(3):
        for c in range(9):
            slot(d, 7 + 18 * c, 139 + 18 * r)
    for c in range(9):
        slot(d, 7 + 18 * c, 197)


def slot(d, x, y):
    d.rectangle([x * SS, y * SS, (x + 18) * SS - 1, (y + 18) * SS - 1], fill=(20, 17, 36, 255))
    d.rectangle([x * SS, y * SS, (x + 18) * SS - 1, (y + 18) * SS - 1], outline=(52, 45, 84, 255), width=SS // 2)
    d.line([x * SS, (y + 18) * SS - 1, (x + 18) * SS, (y + 18) * SS - 1], fill=(4, 3, 9, 255), width=SS // 2)


def finish(base):
    return base.resize((W, H), Image.LANCZOS).convert("RGBA")


# ---------------------------------------------------------------- lines

def glow_line(base, points, color, width=1.4, glow_radius=2.2, alpha=230, smooth=True):
    pts = [(x * SS, y * SS) for x, y in points]
    if smooth and len(pts) >= 3:
        pts = chaikin(pts, 3)
    halo = layer()
    ImageDraw.Draw(halo).line(pts, fill=color + (200,), width=int(width * 2.4 * SS), joint="curve")
    base.alpha_composite(glow(halo, glow_radius, 1.3))
    core = layer()
    d = ImageDraw.Draw(core)
    d.line(pts, fill=shade(color, 1.0) + (alpha,), width=int(width * SS), joint="curve")
    d.line(pts, fill=mix(color, (255, 255, 255), 0.55) + (alpha,), width=max(1, int(width * SS * 0.4)), joint="curve")
    base.alpha_composite(core)


def chaikin(points, rounds):
    for _ in range(rounds):
        out = [points[0]]
        for a, b in zip(points, points[1:]):
            out.append((a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25))
            out.append((a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75))
        out.append(points[-1])
        points = out
    return points


def ring(base, center, radius, color, width=0.7, alpha=120, dashes=None):
    d = ImageDraw.Draw(base)
    x, y = center
    if dashes is None:
        d.ellipse([(x - radius) * SS, (y - radius) * SS, (x + radius) * SS, (y + radius) * SS],
                  outline=color + (alpha,), width=max(1, int(width * SS)))
    else:
        for i in range(dashes):
            a0 = 360 * i / dashes
            d.arc([(x - radius) * SS, (y - radius) * SS, (x + radius) * SS, (y + radius) * SS], a0, a0 + 180 / dashes,
                  fill=color + (alpha,), width=max(1, int(width * SS)))


# ---------------------------------------------------------------- medallions

def medallion(base, x, y, radius, color, draw_symbol, bright=1.0):
    """A round frame with a dark glassy centre; draw_symbol(d, cx, cy, r, color) paints the symbol inside."""
    big = layer()
    d = ImageDraw.Draw(big)
    r = radius * SS
    px, py = x * SS, y * SS
    d.ellipse([px - r * 1.35, py - r * 1.35, px + r * 1.35, py + r * 1.35], fill=color + (70,))
    base.alpha_composite(glow(big, radius * 0.45, 1.1))
    d = ImageDraw.Draw(base)
    d.ellipse([px - r, py - r, px + r, py + r], fill=(10, 8, 22, 255))
    inner = radial((int(2 * r) + 2, int(2 * r) + 2), (r, r), r, shade(color, 0.45 * bright) + (255,), (10, 8, 22, 255), 1.4)
    mask = Image.new("L", inner.size, 0)
    ImageDraw.Draw(mask).ellipse([0, 0, inner.size[0] - 1, inner.size[1] - 1], fill=255)
    base.paste(inner, (int(px - r), int(py - r)), mask)
    d = ImageDraw.Draw(base)
    d.ellipse([px - r, py - r, px + r, py + r], outline=shade(color, 1.0) + (255,), width=max(1, int(0.9 * SS)))
    d.ellipse([px - r * 0.86, py - r * 0.86, px + r * 0.86, py + r * 0.86], outline=COLORS["gold"] + (160,), width=max(1, int(0.4 * SS)))
    draw_symbol(d, px, py, r * 0.62, mix(color, (255, 255, 255), 0.6))


def poly(d, cx_, cy_, r, n, rot, fill=None, outline=None, width=1):
    pts = [(cx_ + r * math.cos(rot + 2 * math.pi * i / n), cy_ + r * math.sin(rot + 2 * math.pi * i / n)) for i in range(n)]
    d.polygon(pts, fill=fill, outline=outline, width=width)
    return pts


def star(d, cx_, cy_, r_out, r_in, n, rot, fill):
    pts = []
    for i in range(n * 2):
        r = r_out if i % 2 == 0 else r_in
        a = rot + math.pi * i / n
        pts.append((cx_ + r * math.cos(a), cy_ + r * math.sin(a)))
    d.polygon(pts, fill=fill)


# group / node symbols: (d, px, py, r, color)
def sym_ice(d, px, py, r, color):
    for i in range(6):
        a = math.pi * i / 3
        ex, ey = px + r * math.cos(a), py + r * math.sin(a)
        d.line([px, py, ex, ey], fill=color + (255,), width=max(1, int(r * 0.16)))
        for s in (-0.5, 0.5):
            bx, by = px + r * 0.6 * math.cos(a), py + r * 0.6 * math.sin(a)
            d.line([bx, by, bx + r * 0.32 * math.cos(a + s * 1.4), by + r * 0.32 * math.sin(a + s * 1.4)],
                   fill=color + (255,), width=max(1, int(r * 0.12)))


def sym_heal(d, px, py, r, color):
    w = r * 0.38
    d.rectangle([px - w, py - r, px + w, py + r], fill=color + (255,))
    d.rectangle([px - r, py - w, px + r, py + w], fill=color + (255,))


def sym_blood(d, px, py, r, color):
    d.polygon([(px, py - r), (px + r * 0.75, py + r * 0.25), (px, py + r), (px - r * 0.75, py + r * 0.25)], fill=color + (255,))
    d.ellipse([px - r * 0.78, py - r * 0.35, px + r * 0.78, py + r * 1.05], fill=color + (255,))
    d.ellipse([px - r * 0.35, py - r * 0.1, px - r * 0.1, py + r * 0.3], fill=(255, 255, 255, 210))


def sym_metal(d, px, py, r, color):
    pts = poly(d, px, py, r, 6, math.pi / 6, fill=color + (255,))
    poly(d, px, py, r * 0.45, 6, math.pi / 6, fill=(12, 10, 24, 255))


def sym_combustion(d, px, py, r, color):
    star(d, px, py, r, r * 0.38, 8, math.pi / 8, color + (255,))
    d.ellipse([px - r * 0.28, py - r * 0.28, px + r * 0.28, py + r * 0.28], fill=(255, 255, 255, 255))


def sym_wind(d, px, py, r, color):
    pts = []
    for i in range(0, 90):
        a = i / 90 * 4.6 * math.pi
        rr = r * (0.12 + 0.88 * i / 90)
        pts.append((px + rr * math.cos(a), py + rr * math.sin(a)))
    d.line(pts, fill=color + (255,), width=max(1, int(r * 0.2)), joint="curve")


def sym_lava(d, px, py, r, color):
    d.polygon([(px - r, py + r * 0.8), (px - r * 0.35, py - r * 0.5), (px + r * 0.35, py - r * 0.5), (px + r, py + r * 0.8)], fill=color + (255,))
    for i, x in enumerate((-0.35, 0.0, 0.35)):
        d.ellipse([px + r * x - r * 0.12, py - r * (0.95 + 0.2 * (i % 2)) - r * 0.12, px + r * x + r * 0.12, py - r * (0.95 + 0.2 * (i % 2)) + r * 0.12],
                  fill=color + (255,))


def sym_lightning(d, px, py, r, color):
    d.polygon([(px + r * 0.15, py - r), (px - r * 0.55, py + r * 0.12), (px - r * 0.02, py + r * 0.12),
               (px - r * 0.2, py + r), (px + r * 0.6, py - r * 0.18), (px + r * 0.05, py - r * 0.18)], fill=color + (255,))


def sym_ascendant(d, px, py, r, color):
    star(d, px, py, r * 1.05, r * 0.3, 8, -math.pi / 2, color + (255,))
    d.ellipse([px - r * 0.3, py - r * 0.3, px + r * 0.3, py + r * 0.3], fill=(12, 10, 24, 255))
    d.ellipse([px - r * 0.14, py - r * 0.14, px + r * 0.14, py + r * 0.14], fill=(255, 255, 255, 255))


def sym_points(d, px, py, r, color):
    star(d, px, py, r * 1.05, r * 0.45, 5, -math.pi / 2, color + (255,))


def sym_shield(d, px, py, r, color):
    d.polygon([(px - r * 0.85, py - r * 0.8), (px + r * 0.85, py - r * 0.8), (px + r * 0.85, py + r * 0.1),
               (px, py + r), (px - r * 0.85, py + r * 0.1)], fill=color + (255,))
    d.polygon([(px - r * 0.45, py - r * 0.45), (px + r * 0.45, py - r * 0.45), (px + r * 0.45, py + r * 0.05),
               (px, py + r * 0.55), (px - r * 0.45, py + r * 0.05)], fill=(12, 10, 24, 255))


def sym_back(d, px, py, r, color):
    d.polygon([(px - r, py), (px + r * 0.1, py - r * 0.9), (px + r * 0.1, py - r * 0.35), (px + r, py - r * 0.35),
               (px + r, py + r * 0.35), (px + r * 0.1, py + r * 0.35), (px + r * 0.1, py + r * 0.9)], fill=color + (255,))


def sym_root(d, px, py, r, color):
    sym_ascendant(d, px, py, r, color)


def sigil(seed):
    """A runic symbol that is the same for the same ability id, so each ability is recognisable at a glance."""
    rnd = random.Random(seed)
    n = rnd.choice((3, 4, 5, 6, 7))
    rot = rnd.uniform(0, math.tau)
    kind = rnd.choice(("poly", "star", "cross", "tri"))
    dot = rnd.random() < 0.6

    def draw(d, px, py, r, color):
        if kind == "poly":
            poly(d, px, py, r, n, rot, outline=color + (255,), width=max(1, int(r * 0.2)))
        elif kind == "star":
            star(d, px, py, r, r * 0.45, n, rot, color + (255,))
        elif kind == "cross":
            for i in range(n):
                a = rot + math.pi * i / n
                d.line([px - r * math.cos(a), py - r * math.sin(a), px + r * math.cos(a), py + r * math.sin(a)],
                       fill=color + (255,), width=max(1, int(r * 0.2)))
        else:
            poly(d, px, py, r, 3, rot, fill=color + (255,))
            poly(d, px, py, r * 0.5, 3, rot + math.pi, fill=(12, 10, 24, 255))
        if dot:
            d.ellipse([px - r * 0.18, py - r * 0.18, px + r * 0.18, py + r * 0.18], fill=(255, 255, 255, 255))
    return draw


GROUP_SYMBOLS = {
    "ICE": sym_ice, "HEALING": sym_heal, "BLOOD": sym_blood, "METAL": sym_metal, "COMBUSTION": sym_combustion,
    "AIR_MASTERY": sym_wind, "LAVA": sym_lava, "LIGHTNING": sym_lightning, "AVATAR": sym_ascendant,
}
