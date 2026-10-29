package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Two Booths: the Abyssal Laboratory. The two sealed stone booths (x -9..-3 and 3..9, z -3..5, y 0..5) become brass
 * diving bells with portholes painted on, hanging from a gantry by chains over a coral reef on the sea floor; a
 * pipe of speaking tubes arcs between them (decorative, solid: the booths stay sealed), a sunken galleon's bow, kelp
 * towers of prismarine, coral gardens in concrete and terracotta, glowing anglerfish lures and the Gamemaster as a
 * deep-sea diver loom in the gloom. Inside each booth only the corners get brass fittings and copper bulbs.
 */
public final class AbyssLook {
    private AbyssLook() {
    }

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 19191L);
        // inside the booths: only the corners (keep the lines between players, lamps and pads open)
        for (int cx : new int[] {-6, 6}) {
            d.keepOut(cx - 2, 1, -2, cx + 2, 4, 4);
        }
        // ---- the bells' cladding: copper bands and rivets around each booth (outside) ----
        for (int cx : new int[] {-6, 6}) {
            for (int y = 0; y <= 6; y++) {
                for (int x = cx - 4; x <= cx + 4; x++) {
                    for (int z = -4; z <= 6; z++) {
                        boolean shell = x == cx - 4 || x == cx + 4 || z == -4 || z == 6;
                        if (!shell) {
                            continue;
                        }
                        String s = (y == 0 || y == 3 || y == 6) ? "waxed_copper_block" : "waxed_exposed_cut_copper";
                        if (y == 3 && ((x + z) & 1) == 0) {
                            s = "waxed_copper_bulb[lit=true]";
                        }
                        d.set(x, y, z, s);
                    }
                }
            }
            // a domed crown and the hook
            d.fill(cx - 3, 6, -3, cx + 3, 6, 5, "waxed_copper_block");
            d.fill(cx - 2, 7, -2, cx + 2, 7, 4, "waxed_exposed_copper");
            d.fill(cx - 1, 8, 0, cx + 1, 8, 2, "gold_block");
            for (int y = 9; y <= 20; y++) {
                d.set(cx, y, 1, "iron_chain[axis=y]");
            }
            // portholes (gold rims with blue glass) painted on the outer side
            int ox = cx < 0 ? cx - 5 : cx + 5;
            for (int z : new int[] {-1, 3}) {
                d.set(ox, 2, z, "light_blue_stained_glass");
                d.set(ox, 3, z, "light_blue_stained_glass");
                d.set(ox, 1, z, "gold_block");
                d.set(ox, 4, z, "gold_block");
            }
            // skirt and ballast weights
            d.fill(cx - 5, -1, -5, cx + 5, -1, 7, "waxed_oxidized_cut_copper");
            for (int[] c : new int[][] {{-5, -5}, {5, -5}, {-5, 7}, {5, 7}}) {
                d.set(cx + c[0], -2, c[1], "heavy_core");
            }
        }
        // ---- the gantry the bells hang from, and the speaking tube between them ----
        d.fill(-14, 21, 1, 14, 21, 1, "waxed_cut_copper");
        for (int x : new int[] {-14, 14}) {
            for (int y = -12; y <= 21; y++) {
                d.set(x, y, 1, y % 5 == 0 ? "gold_block" : "waxed_copper_block");
            }
        }
        for (int x = -3; x <= 3; x++) {
            int y = 8 + (int) Math.round(3 * Math.cos(x * Math.PI / 6));
            d.set(x, y, 1, "waxed_copper_grate");
        }
        d.set(0, 12, 1, "gold_block");
        d.set(0, 13, 1, "waxed_copper_bulb[lit=true]");
        // ---- inside: brass corner fittings and bulbs above head height ----
        for (int cx : new int[] {-6, 6}) {
            for (int[] c : new int[][] {{-2, -2}, {2, -2}}) {
                d.set(cx + c[0], 1, c[1], "barrel[facing=up]");
                d.set(cx + c[0], 2, c[1], "waxed_copper_bars");
            }
        }
        // ---- the sea floor: sand-coloured stone, coral gardens, kelp towers ----
        int floor = -14;
        String[] coral = {"pink_concrete", "magenta_concrete", "orange_concrete", "yellow_concrete", "red_terracotta",
            "purple_concrete", "lime_terracotta", "light_blue_concrete"};
        for (int x = -64; x <= 64; x++) {
            for (int z = -64; z <= 64; z++) {
                if (Math.hypot(x, z) > 64) {
                    continue;
                }
                int h = (int) Math.round(1.5 * Math.sin(x * 0.13) + 1.2 * Math.cos(z * 0.17));
                d.set(x, floor + h, z, Math.floorMod(x * 3 + z * 7, 23) == 0 ? "sea_lantern" : "smooth_sandstone|sandstone|smooth_sandstone");
                d.set(x, floor + h - 1, z, "sandstone");
                int k = Math.floorMod(x * 31 + z * 17, 41);
                if (Math.hypot(x, z) > 14 && k < 3) {
                    int ch = 2 + Math.floorMod(x * z, 4);
                    for (int i = 1; i <= ch; i++) {
                        d.set(x, floor + h + i, z, coral[Math.floorMod(x + z, coral.length)]);
                    }
                    d.set(x + 1, floor + h + ch - 1, z, coral[Math.floorMod(x + z + 3, coral.length)]);
                }
            }
        }
        for (int i = 0; i < 30; i++) {
            double a = d.rnd.nextDouble() * Math.PI * 2, r = 18 + d.rnd.nextDouble() * 40;
            int x = (int) (r * Math.cos(a)), z = (int) (r * Math.sin(a));
            int h = 14 + d.rnd.nextInt(18);
            for (int y = floor; y < floor + h; y++) {
                int sway = (int) Math.round(1.5 * Math.sin(y * 0.3 + i));
                d.set(x + sway, y, z, y % 4 == 0 ? "dark_prismarine" : "prismarine");
                if (y % 3 == 0) {
                    d.set(x + sway + 1, y, z, "warped_wart_block");
                }
            }
            d.set(x, floor + h, z, "sea_lantern");
        }
        // ---- a sunken galleon bow, rising out of the sand ----
        for (int u = 0; u < 22; u++) {
            int half = Math.max(1, 6 - u / 4);
            for (int v = -half; v <= half; v++) {
                for (int y = floor; y <= floor + 2 + u / 2; y++) {
                    if (Math.abs(v) == half || y == floor + 2 + u / 2) {
                        d.set(-36 + v, y, 30 - u, "dark_oak_planks|dark_oak_planks|stripped_dark_oak_log");
                    }
                }
            }
        }
        for (int y = floor; y <= floor + 22; y++) {
            d.set(-36, y, 22, "stripped_dark_oak_log");
        }
        d.fill(-40, floor + 18, 22, -32, floor + 18, 22, "stripped_spruce_log[axis=x]");
        d.fill(-39, floor + 12, 22, -33, floor + 17, 22, "white_wool");
        // ---- anglerfish lures: hanging glowing bulbs on long stems in the dark ----
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + 0.4;
            int x = (int) (32 * Math.cos(a)), z = (int) (32 * Math.sin(a));
            int y = 10 + (i % 3) * 6;
            d.line(x + 0.5, y + 8.5, z + 0.5, x + 3.5, y + 0.5, z + 0.5, "iron_chain[axis=y]");
            d.set(x + 3, y, z, "ochre_froglight");
            d.set(x, y + 9, z, "black_concrete");
            d.set(x - 1, y + 9, z, "black_concrete");
        }
        // ---- the Gamemaster as a deep-sea diver: brass helmet over the statue's head ----
        int sz = 44;
        Props.statue(d, 0, floor, sz, 0, -1, 30, "waxed_copper_block", "gold_block");
        double k = 30 / 16.0;
        d.ellipsoid(0.5, floor + 1 + 13.3 * k, sz + 0.5, 2.6 * k, 2.4 * k, 2.6 * k, 0.9, "waxed_copper_block");
        for (int y = (int) (floor + 1 + 12.4 * k); y <= floor + 1 + 14.2 * k; y++) {
            for (int x = -2; x <= 2; x++) {
                d.set(x, y, (int) Math.floor(sz + 0.5 - 2.6 * k), "light_blue_stained_glass");
            }
        }
        // bubbles: white stained glass columns rising from the bells and the diver
        for (int[] b : new int[][] {{-6, -5}, {6, 7}, {2, sz - 4}}) {
            for (int y = 10; y < 34; y += 3) {
                d.set(b[0] + (y % 2), y, b[1], "white_stained_glass");
            }
        }
        d.commit();
        for (int x = -12; x <= 12; x += 12) {
            for (int z = -10; z <= 12; z += 11) {
                arena.setLater(x, 12, z, Deco.st("light[level=15]"));
            }
        }
    }
}
