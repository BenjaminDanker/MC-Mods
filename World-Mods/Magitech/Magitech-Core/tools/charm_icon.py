#!/usr/bin/env python3
"""Draws the Deepsteel Charm icon (a deepsteel medallion with an ember core) using Tech-Arsenal's sprite toolkit."""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "..", "Tech-Arsenal", "tools"))
from sprites import RAMPS, Canvas, write_png  # noqa: E402

RAMPS["deepsteel"] = ["#0e1018", "#2b3350", "#46547d", "#98abd8"]
RAMPS["ember"] = ["#a33d0a", "#ff7a1f", "#ffb13d", "#fff0a0"]

c = Canvas()
# the loop it hangs from
c.rect(7, 1, 8, 1, "deepsteel", 2)
c.p(6, 2, "deepsteel", 2)
c.p(9, 2, "deepsteel", 2)
c.p(6, 3, "deepsteel", 1)
c.p(9, 3, "deepsteel", 1)
# the medallion: a disc with a raised rim
for y, (x0, x1) in enumerate([(6, 9), (4, 11), (3, 12), (3, 12), (2, 13), (2, 13), (2, 13), (2, 13), (3, 12), (3, 12), (4, 11), (6, 9)], start=3):
    c.rect(x0, y, x1, y, "deepsteel", 1)
# inner plate
c.rect(5, 6, 10, 11, "deepsteel", 2)
# the ember core
c.rect(7, 7, 8, 10, "ember", 2)
c.rect(6, 8, 9, 9, "ember", 2)
c.p(7, 8, "ember", 3)
c.p(8, 8, "ember", 3)
# rivets on the rim
for x, y in [(3, 6), (12, 6), (3, 9), (12, 9)]:
    c.p(x, y, "deepsteel", 3)

out = os.path.join(HERE, "..", "src", "main", "resources", "assets", "magitech", "textures", "item", "deepsteel_charm.png")
write_png(out, c.render())
print("wrote", os.path.normpath(out))
