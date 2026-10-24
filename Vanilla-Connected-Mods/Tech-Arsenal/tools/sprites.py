"""A tiny 16x16 sprite toolkit: draw with materials, and shading plus a dark outline are added automatically.

Light comes from the top left. A pixel with nothing above or to its left (inside the sprite) is lightened, one with nothing below
or to its right is darkened, and every transparent pixel touching the sprite becomes the outline. So drawings fit in 14x14.
"""
import struct
import zlib

SIZE = 16
OUTLINE = (11, 13, 18, 255)


def hexc(s, a=255):
    return (int(s[1:3], 16), int(s[3:5], 16), int(s[5:7], 16), a)


# dark -> light, then a specular tone
RAMPS = {
    "steel":   ["#2a3040", "#4f5b70", "#8795ab", "#cdd8e8"],
    "dark":    ["#14171f", "#262c38", "#3b4455", "#566175"],
    "copper":  ["#6b3a1c", "#a8602c", "#d98b45", "#f6c27a"],
    "wood":    ["#3a2415", "#5a3a22", "#7d5233", "#a06f47"],
    "grip":    ["#1c1e28", "#2e3240", "#474d60", "#656c82"],
    "red":     ["#5a1414", "#a02a24", "#d9473a", "#ff8a70"],
    "brass":   ["#5b4a14", "#9a7d22", "#d4b13a", "#f5e08a"],
    "white":   ["#8a93a3", "#c9d1de", "#eef3fa", "#ffffff"],
    "green":   ["#1a5a2a", "#2f9a45", "#62d27a", "#b5f5c0"],
    "blue":    ["#1b2f6b", "#2f55b8", "#5f8ae8", "#a9c4ff"],
    # glowing materials: not shaded by edges
    "orange":  ["#a33d0a", "#e8731a", "#ffb13d", "#fff0a0"],
    "cyan":    ["#0f6c8a", "#2bb7de", "#7ee8ff", "#e6fdff"],
    "yellow":  ["#a07a08", "#e6b81a", "#ffe45c", "#fffbd0"],
    "magenta": ["#7a1d8a", "#c04ae0", "#e88cff", "#fbe0ff"],
}
GLOWING = {"orange", "cyan", "yellow", "magenta"}


class Canvas:
    def __init__(self):
        self.px = [[None] * SIZE for _ in range(SIZE)]

    def p(self, x, y, mat, tone=1):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[y][x] = (mat, tone)
        return self

    def rect(self, x0, y0, x1, y1, mat, tone=1):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.p(x, y, mat, tone)
        return self

    def line(self, x0, y0, x1, y1, mat, tone=1, width=1):
        """Bresenham; width 2 doubles each step to the right and below, which suits the up-right diagonals."""
        dx, dy = abs(x1 - x0), -abs(y1 - y0)
        sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
        err = dx + dy
        x, y = x0, y0
        while True:
            self.p(x, y, mat, tone)
            if width >= 2:
                self.p(x + 1, y, mat, tone)
                self.p(x, y + 1, mat, tone)
            if (x, y) == (x1, y1):
                break
            e2 = 2 * err
            if e2 >= dy:
                err += dy
                x += sx
            if e2 <= dx:
                err += dx
                y += sy
        return self

    def erase(self, x, y):
        if 0 <= x < SIZE and 0 <= y < SIZE:
            self.px[y][x] = None
        return self

    def filled(self, x, y):
        return 0 <= x < SIZE and 0 <= y < SIZE and self.px[y][x] is not None

    def render(self, outline=True, shade=True):
        out = [[(0, 0, 0, 0)] * SIZE for _ in range(SIZE)]
        for y in range(SIZE):
            for x in range(SIZE):
                cell = self.px[y][x]
                if cell is None:
                    continue
                mat, tone = cell
                if shade and mat not in GLOWING:
                    if not self.filled(x, y - 1) or not self.filled(x - 1, y):
                        tone = min(3, tone + 1)
                    if not self.filled(x, y + 1) or not self.filled(x + 1, y):
                        tone = max(0, tone - 1)
                out[y][x] = hexc(RAMPS[mat][tone])
        if outline:
            for y in range(SIZE):
                for x in range(SIZE):
                    if self.px[y][x] is None and any(self.filled(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                        out[y][x] = OUTLINE
        return out


def write_png(path, pixels):
    h, w = len(pixels), len(pixels[0])
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw += bytes((r, g, b, a))

    def chunk(tag, data):
        body = struct.pack(">I", len(data)) + tag + data
        return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as out:
        out.write(png)


def contact_sheet(sprites, names, path, columns=8, scale=6, bg=(34, 38, 48)):
    rows = (len(sprites) + columns - 1) // columns
    cell = SIZE * scale + 8
    width, height = columns * cell, rows * (cell + 12)
    img = [[bg] * width for _ in range(height)]
    for i, sprite in enumerate(sprites):
        ox, oy = (i % columns) * cell + 4, (i // columns) * (cell + 12) + 4
        for y in range(SIZE):
            for x in range(SIZE):
                r, g, b, a = sprite[y][x]
                if a == 0:
                    continue
                for dy in range(scale):
                    for dx in range(scale):
                        img[oy + y * scale + dy][ox + x * scale + dx] = (r, g, b)
    raw = bytearray()
    for row in img:
        raw.append(0)
        for r, g, b in row:
            raw += bytes((r, g, b))

    def chunk(tag, data):
        body = struct.pack(">I", len(data)) + tag + data
        return body + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) \
        + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    with open(path, "wb") as out:
        out.write(png)
