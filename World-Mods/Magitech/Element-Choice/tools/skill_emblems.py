"""The four guardian spirits as drawn emblems: Tortoise (Water), Dragon (Air), Phoenix (Fire), Tiger (Earth).

Each emblem is painted on a 256 px canvas with a body colour, a lighter highlight and dark linework, then
scaled to wherever it is used.
"""
import math

from PIL import Image, ImageDraw, ImageFilter

from skill_art import mix, shade

N = 256


def _canvas():
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    return img, ImageDraw.Draw(img)


def _rot(points, angle, origin=(128, 128)):
    ca, sa = math.cos(angle), math.sin(angle)
    return [(origin[0] + (x - origin[0]) * ca - (y - origin[1]) * sa,
             origin[1] + (x - origin[0]) * sa + (y - origin[1]) * ca) for x, y in points]


def _ellipse_poly(cx, cy, rx, ry, angle=0.0, steps=40):
    pts = [(cx + rx * math.cos(2 * math.pi * i / steps), cy + ry * math.sin(2 * math.pi * i / steps)) for i in range(steps)]
    return _rot(pts, angle, (cx, cy))


def _shine(img, color):
    """Top-left light and bottom-right shadow inside the painted shape."""
    alpha = img.getchannel("A")
    light = Image.new("RGBA", (N, N), mix(color, (255, 255, 255), 0.5) + (0,))
    shadow = Image.new("RGBA", (N, N), shade(color, 0.35) + (0,))
    gradient = Image.linear_gradient("L").resize((N, N)).rotate(45)
    light.putalpha(ImageChops_multiply(gradient.point(lambda v: 255 - v), alpha).point(lambda v: v // 2))
    shadow.putalpha(ImageChops_multiply(gradient, alpha).point(lambda v: v // 2))
    img.alpha_composite(light)
    img.alpha_composite(shadow)


def ImageChops_multiply(a, b):
    from PIL import ImageChops
    return ImageChops.multiply(a, b)


def tortoise(color):
    img, d = _canvas()
    body, dark = color, shade(color, 0.3)
    for ang in (0.8, 2.34, 3.94, 5.48):                                   # flippers
        x = 128 + 78 * math.cos(ang - 0.0)
        y = 140 + 78 * math.sin(ang)
        d.polygon(_ellipse_poly(x, y, 34, 18, ang), fill=body + (255,), outline=dark + (255,))
    d.polygon([(128, 214), (116, 246), (140, 246)], fill=body + (255,), outline=dark + (255,))  # tail
    d.polygon(_ellipse_poly(128, 44, 26, 32), fill=body + (255,), outline=dark + (255,))       # head
    for ex in (-11, 11):
        d.ellipse([128 + ex - 4, 36, 128 + ex + 4, 44], fill=(255, 255, 255, 255))
        d.ellipse([128 + ex - 2, 38, 128 + ex + 2, 42], fill=dark + (255,))
    d.polygon(_ellipse_poly(128, 142, 72, 84), fill=shade(color, 0.7) + (255,), outline=dark + (255,))  # shell
    d.polygon(_ellipse_poly(128, 142, 62, 74), fill=body + (255,), outline=dark + (255,))
    hexa = [(128 + 26 * math.cos(math.pi / 6 + math.pi * i / 3), 142 + 26 * math.sin(math.pi / 6 + math.pi * i / 3)) for i in range(6)]
    d.polygon(hexa, fill=mix(color, (255, 255, 255), 0.35) + (255,), outline=dark + (255,))
    for i, (px, py) in enumerate(hexa):
        a = math.atan2(py - 142, px - 128)
        d.line([px, py, 128 + 70 * math.cos(a), 142 + 82 * math.sin(a)], fill=dark + (255,), width=4)
        nx, ny = hexa[(i + 1) % 6]
        mid = ((px + nx) / 2, (py + ny) / 2)
        ma = math.atan2(mid[1] - 142, mid[0] - 128)
        d.line([mid[0], mid[1], 128 + 66 * math.cos(ma), 142 + 78 * math.sin(ma)], fill=dark + (255,), width=3)
    _shine(img, color)
    return img


def dragon(color):
    img, d = _canvas()
    body, dark = color, shade(color, 0.3)
    path = []
    for i in range(0, 121):
        t = i / 120
        x = 104 + 58 * math.sin(t * math.pi * 2.6 + 0.4) * (0.45 + 0.55 * t) + 14 * (1 - t)
        y = 52 + t * 190
        path.append((x, y, 22 * (1 - t) ** 0.8 + 4))
    for x, y, r in reversed(path):                                           # tapered serpent body, tail first
        d.ellipse([x - r, y - r, x + r, y + r], fill=shade(color, 0.75) + (255,), outline=dark + (255,))
    for x, y, r in reversed(path):
        d.ellipse([x - r * 0.78, y - r * 0.78, x + r * 0.78, y + r * 0.78], fill=body + (255,))
    for i in range(6, 112, 9):                                               # belly scales and spines
        x, y, r = path[i]
        d.arc([x - r, y - r * 0.4, x + r, y + r * 0.9], 20, 160, fill=mix(color, (255, 255, 255), 0.5) + (255,), width=3)
        nx, ny, _ = path[i + 3]
        ang = math.atan2(ny - y, nx - x) - math.pi / 2
        d.polygon([(x + r * math.cos(ang), y + r * math.sin(ang)),
                   (x + (r + 14) * math.cos(ang - 0.35), y + (r + 14) * math.sin(ang - 0.35)),
                   (x + (r * 0.4) * math.cos(ang + 0.9), y + (r * 0.4) * math.sin(ang + 0.9))], fill=dark + (255,))
    for i in (34, 62):                                                       # legs with claws
        x, y, r = path[i]
        for side in (-1, 1):
            d.polygon([(x + side * r * 0.6, y), (x + side * (r + 30), y + 24), (x + side * (r + 36), y + 40),
                       (x + side * (r + 24), y + 34), (x + side * (r + 16), y + 44), (x + side * (r + 10), y + 26)],
                      fill=shade(color, 0.8) + (255,), outline=dark + (255,))
    hx, hy, hr = path[0]
    d.polygon([(hx - 30, hy + 4), (hx - 8, hy - 34), (hx + 24, hy - 38), (hx + 52, hy - 12), (hx + 40, hy + 8),
               (hx + 14, hy + 22), (hx - 12, hy + 26)], fill=body + (255,), outline=dark + (255,))   # head
    d.polygon([(hx + 40, hy + 8), (hx + 62, hy + 14), (hx + 44, hy + 22), (hx + 14, hy + 22)], fill=shade(color, 0.8) + (255,), outline=dark + (255,))
    for hxo, ang in ((-16, -0.9), (4, -0.6)):                                # horns
        d.polygon([(hx + hxo, hy - 30), (hx + hxo - 24, hy - 70 + ang * 10), (hx + hxo + 8, hy - 36)], fill=mix(color, (255, 255, 255), 0.5) + (255,), outline=dark + (255,))
    d.ellipse([hx + 14, hy - 18, hx + 28, hy - 4], fill=(255, 245, 190, 255), outline=dark + (255,))
    d.ellipse([hx + 19, hy - 14, hx + 25, hy - 6], fill=dark + (255,))
    for s in (0, 1):                                                         # whiskers
        pts = [(hx + 52, hy + 8 + s * 8), (hx + 76, hy + 20 + s * 12), (hx + 90, hy + 8 + s * 24), (hx + 102, hy + 18 + s * 22)]
        d.line(pts, fill=mix(color, (255, 255, 255), 0.6) + (255,), width=4, joint="curve")
    _shine(img, color)
    return img


def phoenix(color):
    img, d = _canvas()
    body, dark = color, shade(color, 0.3)
    hot = mix(color, (255, 230, 120), 0.7)
    for side in (-1, 1):                                                     # wings: fans of feathers
        for i in range(7):
            ang = math.radians(-8 - i * 12.5)
            length = 112 - i * 6
            ox, oy = 128 + side * 10, 112
            tip = (ox + side * length * math.cos(ang), oy + length * math.sin(ang))
            nx, ny = -math.sin(ang), math.cos(ang)
            w = 15 - i * 0.6
            pts = [(ox, oy), (ox + side * (length * 0.5 * math.cos(ang)) + nx * w * side, oy + length * 0.5 * math.sin(ang) + ny * w),
                   tip, (ox + side * (length * 0.5 * math.cos(ang)) - nx * w * side, oy + length * 0.5 * math.sin(ang) - ny * w)]
            d.polygon(pts, fill=mix(body, hot, i / 7) + (255,), outline=dark + (255,))
    for k, ang in enumerate((-16, 0, 16)):                                   # tail plumes
        a = math.radians(90 + ang)
        pts = [(128, 150), (128 + 18 * math.cos(a + 1.4), 150 + 18 * math.sin(a + 1.4)),
               (128 + 108 * math.cos(a), 150 + 108 * math.sin(a)), (128 + 18 * math.cos(a - 1.4), 150 + 18 * math.sin(a - 1.4))]
        d.polygon(pts, fill=mix(body, hot, 0.3 + 0.3 * k) + (255,), outline=dark + (255,))
    d.polygon(_ellipse_poly(128, 124, 22, 44), fill=body + (255,), outline=dark + (255,))   # body
    d.polygon(_ellipse_poly(128, 124, 12, 30), fill=hot + (255,))
    d.polygon(_ellipse_poly(128, 76, 20, 22), fill=body + (255,), outline=dark + (255,))    # head
    d.polygon([(124, 78), (132, 78), (128, 100)], fill=(255, 220, 90, 255), outline=dark + (255,))  # beak
    for ex in (-8, 8):
        d.ellipse([128 + ex - 3, 68, 128 + ex + 3, 74], fill=(255, 255, 255, 255))
    for ang, ln in ((-30, 46), (0, 56), (30, 46)):                           # crest
        a = math.radians(-90 + ang)
        d.polygon([(128 - 5, 62), (128 + 5, 62), (128 + ln * math.cos(a), 62 + ln * math.sin(a))], fill=hot + (255,), outline=dark + (255,))
    _shine(img, color)
    return img


def tiger(color):
    img, d = _canvas()
    body, dark = color, shade(color, 0.22)
    light = mix(color, (255, 255, 255), 0.7)
    for sx in (-1, 1):                                                       # ears
        d.polygon(_ellipse_poly(128 + sx * 70, 70, 28, 30), fill=body + (255,), outline=dark + (255,))
        d.polygon(_ellipse_poly(128 + sx * 70, 74, 15, 17), fill=dark + (255,))
    d.polygon(_ellipse_poly(128, 142, 98, 86), fill=body + (255,), outline=dark + (255,))   # head
    for sx in (-1, 1):                                                       # cheek fur
        d.polygon([(128 + sx * 90, 150), (128 + sx * 116, 168), (128 + sx * 92, 176), (128 + sx * 112, 198), (128 + sx * 78, 190)],
                  fill=body + (255,), outline=dark + (255,))
    for sx in (-1, 1):                                                       # forehead and cheek stripes
        for k in range(3):
            y = 84 + k * 14
            d.polygon([(128 + sx * 8, y), (128 + sx * (34 + k * 6), y - 6), (128 + sx * (26 + k * 6), y + 8)], fill=dark + (255,))
            d.polygon([(128 + sx * 92, 130 + k * 16), (128 + sx * 52, 138 + k * 16), (128 + sx * 90, 142 + k * 16)], fill=dark + (255,))
    d.polygon(_ellipse_poly(128, 186, 52, 34), fill=light + (255,), outline=dark + (255,))    # muzzle
    d.polygon([(128, 160), (114, 178), (142, 178)], fill=(236, 140, 150, 255), outline=dark + (255,))  # nose
    d.line([128, 178, 128, 196], fill=dark + (255,), width=4)
    d.arc([96, 186, 130, 214], 0, 110, fill=dark + (255,), width=4)
    d.arc([126, 186, 160, 214], 70, 180, fill=dark + (255,), width=4)
    for sx in (-1, 1):                                                       # eyes
        d.polygon([(128 + sx * 22, 136), (128 + sx * 62, 122), (128 + sx * 54, 148)], fill=(255, 230, 110, 255), outline=dark + (255,))
        d.ellipse([128 + sx * 42 - 5, 128, 128 + sx * 42 + 5, 142], fill=dark + (255,))
    _shine(img, color)
    return img


EMBLEMS = {"water": tortoise, "air": dragon, "fire": phoenix, "earth": tiger}


def emblem(realm, color, size):
    """The emblem scaled to size px with a soft outer glow."""
    art = EMBLEMS[realm](color)
    # a dark rim so the silhouette stays readable at icon size
    rim = art.getchannel("A").filter(ImageFilter.MaxFilter(9))
    backing = Image.new("RGBA", art.size, (8, 6, 16, 0))
    backing.putalpha(rim)
    flat = Image.new("RGBA", art.size, (0, 0, 0, 0))
    flat.alpha_composite(backing)
    flat.alpha_composite(art)
    art = flat
    halo = Image.new("RGBA", art.size, color + (0,))
    halo.putalpha(art.getchannel("A").filter(ImageFilter.GaussianBlur(10)).point(lambda v: min(255, v * 2)))
    out = Image.new("RGBA", art.size, (0, 0, 0, 0))
    out.alpha_composite(halo)
    out.alpha_composite(art)
    return out.resize((size, size), Image.LANCZOS)
