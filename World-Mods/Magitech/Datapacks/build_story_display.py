"""Builds Branch-Story-Display: the magic and tech story advancements as display-only copies for Magitech.

Player Sync shares advancements; Magitech shows a player's branch story (magicstory, techstory) once it has the
advancements defined. These copies keep each advancement's id, title, icon, parent and criterion names, but every
criterion becomes minecraft:impossible (nothing on Magitech can earn them, only the shared record fills them in), the
rewards are gone (no functions run there), and no toast or chat line shows.

    python3 build_story_display.py      (run from this folder after changing Magic-Story or Tech-Story)
"""
import json
import os
import shutil

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCES = {"magicstory": "Magic-Story", "techstory": "Tech-Story"}
OUT = os.path.join(HERE, "Branch-Story-Display")


def main():
    if os.path.isdir(OUT):
        shutil.rmtree(OUT)
    count = 0
    for namespace, pack in SOURCES.items():
        src = os.path.join(HERE, pack, "data", namespace, "advancement")
        for root, _dirs, files in os.walk(src):
            for name in sorted(files):
                if not name.endswith(".json"):
                    continue
                with open(os.path.join(root, name)) as f:
                    adv = json.load(f)
                adv.pop("rewards", None)
                adv["criteria"] = {key: {"trigger": "minecraft:impossible"} for key in adv.get("criteria", {})}
                display = adv.get("display")
                if display is not None:
                    display["show_toast"] = False
                    display["announce_to_chat"] = False
                target = os.path.join(OUT, "data", namespace, "advancement", os.path.relpath(root, src), name)
                os.makedirs(os.path.dirname(target), exist_ok=True)
                with open(target, "w") as f:
                    json.dump(adv, f, indent=2)
                    f.write("\n")
                count += 1
    with open(os.path.join(HERE, "Magic-Story", "pack.mcmeta")) as f:
        meta = json.load(f)
    meta["pack"]["description"] = "The magic and tech stories, shown on Magitech (display only, filled in by Player Sync)"
    with open(os.path.join(OUT, "pack.mcmeta"), "w") as f:
        json.dump(meta, f, indent=2)
        f.write("\n")
    print(f"Branch-Story-Display: {count} advancements")


if __name__ == "__main__":
    main()
