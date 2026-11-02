"""Paints the Gamemaster's 64x64 player skin: a trickster god in a long purple-and-gold robe, pale face, gold eyes,
a gold circlet with a white die stone, and a die (five pips) on his chest.

Usage: python gamemaster_skin.py <out.png>
"""
import random
import sys

from PIL import Image

W = 64
img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
px = img.load()
rng = random.Random(7)

SKIN = (236, 214, 196)
SKIN_SH = (214, 188, 170)
HAIR = (36, 24, 44)
EYE = (255, 200, 40)
EYE_GLOW = (255, 236, 150)
ROBE = (74, 30, 110)
ROBE_DK = (52, 20, 80)
ROBE_LT = (96, 44, 138)
GOLD = (222, 178, 52)
GOLD_DK = (170, 128, 30)
WHITE = (240, 238, 232)
PIP = (40, 20, 60)
MOUTH = (170, 120, 120)


def jitter(c, n=6):
    return tuple(max(0, min(255, v + rng.randint(-n, n))) for v in c) + (255,)


def rect(x, y, w, h, color, n=6):
    for i in range(x, x + w):
        for j in range(y, y + h):
            px[i, j] = jitter(color, n)


def box(ox, oy, w, h, d, color, n=6):
    """A body part's six faces at its UV origin (classic skin layout)."""
    rect(ox + d, oy, w, d, color, n)              # top
    rect(ox + d + w, oy, w, d, color, n)          # bottom
    rect(ox, oy + d, d, h, color, n)              # right
    rect(ox + d, oy + d, w, h, color, n)          # front
    rect(ox + d + w, oy + d, d, h, color, n)      # left
    rect(ox + d + w + d, oy + d, w, h, color, n)  # back


# Head (8x8x8 at 0,0): pale face, dark hair on top/sides/back.
box(0, 0, 8, 8, 8, SKIN, 4)
rect(8, 0, 8, 8, HAIR)                    # top
rect(0, 8, 8, 8, HAIR); rect(16, 8, 8, 8, HAIR); rect(24, 8, 8, 8, HAIR)
rect(0, 12, 8, 4, SKIN_SH, 3); rect(16, 12, 8, 4, SKIN_SH, 3)  # cheeks/jaw on the sides
rect(8, 8, 8, 2, HAIR)                    # fringe on the face
# Face (front at 8,8): gold eyes with a glow, a thin knowing smile.
for (x, y), c in {(9, 12): EYE_GLOW, (10, 12): EYE, (13, 12): EYE, (14, 12): EYE_GLOW}.items():
    px[x, y] = c + (255,)
px[9, 11] = HAIR + (255,); px[10, 11] = HAIR + (255,); px[13, 11] = HAIR + (255,); px[14, 11] = HAIR + (255,)
px[11, 13] = SKIN_SH + (255,); px[12, 13] = SKIN_SH + (255,)
for x in range(10, 14):
    px[x, 14] = MOUTH + (255,)
px[14, 13] = MOUTH + (255,)               # the smile tilts up on one side

# Hat layer (8x8x8 at 32,0): a gold circlet with a white die stone at the front.
for face_x in (32, 40, 48, 56):           # right, front, left, back
    for x in range(face_x, face_x + 8):
        px[x, 9] = jitter(GOLD, 8)
        px[x, 10] = jitter(GOLD_DK, 6)
px[43, 8] = WHITE + (255,); px[44, 8] = WHITE + (255,)
px[43, 9] = WHITE + (255,); px[44, 9] = PIP + (255,)

# Body (8x12x4 at 16,16): robe with a gold collar and a die on the chest.
box(16, 16, 8, 12, 4, ROBE)
rect(20, 20, 8, 1, GOLD)                  # collar front
rect(32, 20, 8, 1, GOLD)                  # collar back
for x in range(20, 28):                   # gold sash
    px[x, 28] = jitter(GOLD_DK, 6)
    px[x, 29] = jitter(GOLD, 6)
rect(21, 21, 5, 5, WHITE, 3)              # the die: a five, quincunx on a 5x5 face
for x, y in ((22, 22), (24, 22), (23, 23), (22, 24), (24, 24)):
    px[x, y] = PIP + (255,)
for y in range(26, 28):                   # robe opening below the die
    px[23, y] = jitter(ROBE_DK)

# Arms (4x12x4): wide robe sleeves with gold cuffs and pale hands.
for ox, oy in ((40, 16), (32, 48)):
    box(ox, oy, 4, 12, 4, ROBE_LT)
    for face in range(4):                 # right, front, left, back strips along the arm
        fx = ox + (0, 4, 8, 12)[face]
        rect(fx, oy + 4 + 9, 4, 1, GOLD)
        rect(fx, oy + 4 + 10, 4, 2, SKIN, 3)
    rect(ox + 8, oy, 4, 4, SKIN, 3)       # bottom of the hand

# Legs (4x12x4): the long robe all the way down, a gold hem, dark shoes.
for ox, oy in ((0, 16), (16, 48)):
    box(ox, oy, 4, 12, 4, ROBE)
    for face in range(4):
        fx = ox + (0, 4, 8, 12)[face]
        rect(fx, oy + 4 + 9, 4, 1, GOLD)
        rect(fx, oy + 4 + 10, 4, 2, ROBE_DK)
    rect(ox + 8, oy, 4, 4, HAIR)

# Jacket layer (16,32): a slightly flared robe edge so the silhouette reads as a robe, not trousers.
for x in range(20, 28):
    px[x, 47] = jitter(GOLD, 6)

img.save(sys.argv[1])
print("wrote", sys.argv[1])
