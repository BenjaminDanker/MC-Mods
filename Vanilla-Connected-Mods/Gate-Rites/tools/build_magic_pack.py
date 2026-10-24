#!/usr/bin/env python3
"""Builds the Magic resource pack: the pack that is already there (Ruins and Towers items) plus the Ascension portal.

usage: build_magic_pack.py BASE_PACK.zip OUT.zip
The result is deterministic (fixed timestamps, sorted names), so its hash only changes when its content does.
"""
import json, os, sys, zipfile

BASE, OUT = sys.argv[1], sys.argv[2]
ASSETS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "gaterites")
STAMP = (2026, 1, 1, 0, 0, 0)

files = {}
with zipfile.ZipFile(BASE) as base:
    for name in base.namelist():
        if not name.endswith("/"):
            files[name] = base.read(name)

meta = json.loads(files["pack.mcmeta"])
meta["pack"]["description"] = "Magic server resources"
files["pack.mcmeta"] = json.dumps(meta).encode()

for folder in ("items", "models/item", "textures/item"):
    root = os.path.join(ASSETS, folder)
    for name in sorted(os.listdir(root)):
        if name.startswith("portal_"):
            with open(os.path.join(root, name), "rb") as source:
                files[f"assets/gaterites/{folder}/{name}"] = source.read()

# The skill screens' artwork: backgrounds, node rings, font and tooltip frame (built by Element-Choice/tools/build_skill_art.py).
SKILL = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "Element-Choice", "src", "main", "resources", "assets", "skilltree")
for folder, _, names in os.walk(SKILL):
    for name in sorted(names):
        path = os.path.join(folder, name)
        relative = os.path.relpath(path, SKILL).replace(os.sep, "/")
        with open(path, "rb") as source:
            files["assets/skilltree/" + relative] = source.read()

with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as out:
    for name in sorted(files):
        info = zipfile.ZipInfo(name, STAMP)
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        out.writestr(info, files[name])
print(f"{OUT}: {len(files)} files")
