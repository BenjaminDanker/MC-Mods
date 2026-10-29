package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * The Maze: the Gamemaster's Topiary Garden. The maze (x, z 0..size-1) sits in a lawn with gravel walks and flower
 * beds, ringed by a tall clipped hedge with gold-capped pillars and lanterns; four great lantern trees stand at the
 * corners and two topiary colossi of him (moss robes, gold trim) watch over the hedges from the middle of two sides.
 * Everything is outside the maze footprint; only the tall pieces show over the walls (through the glass-clear roof).
 */
public final class GardenLook {
    private GardenLook() {
    }

    public static void build(Arena arena, int tier, int size) {
        Deco d = new Deco(arena, 6061L + size);
        d.keepOut(0, 0, 0, size - 1, 4, size - 1);
        int m = 3 + size / 10;          // lawn margin before the walk
        int hedge = m + 6;               // hedge distance from the maze
        int lo = -hedge, hi = size - 1 + hedge;
        boolean dark = tier >= 5;
        // ---- lawn, walk, flower beds ----
        for (int x = lo; x <= hi; x++) {
            for (int z = lo; z <= hi; z++) {
                int dist = Math.max(Math.max(-x, x - (size - 1)), Math.max(-z, z - (size - 1))); // ring distance from the maze
                if (dist <= 0) {
                    continue;
                }
                String s;
                if (dist == 1) {
                    s = "polished_andesite";
                } else if (dist >= m && dist <= m + 2) {
                    s = (dist == m + 1) ? "gravel" : "smooth_stone";
                } else if (dist >= hedge) {
                    s = "grass_block";
                } else {
                    s = "grass_block";
                }
                d.set(x, 0, z, s);
                if (dist >= 2 && dist < m && s.equals("grass_block")) {
                    int k = Math.floorMod(x * 7 + z * 13, 9);
                    if (k == 0) {
                        d.set(x, 1, z, "allium|azure_bluet|red_tulip|white_tulip|pink_tulip|oxeye_daisy|cornflower|lily_of_the_valley");
                    } else if (k == 4) {
                        d.set(x, 1, z, "short_grass");
                    }
                }
                if (dist == m + 1 && Math.floorMod(x + z, 8) == 0) {
                    d.set(x, 0, z, dark ? "verdant_froglight" : "pearlescent_froglight");
                }
                d.set(x, -1, z, "dirt");
            }
        }
        // low box hedges on the inner edge of the walk with lanterns at the corners of the maze
        for (int x = -m + 1; x <= size - 1 + m - 1; x++) {
            for (int z : new int[] {-m + 1, size - 1 + m - 1}) {
                d.set(x, 1, z, "oak_leaves[persistent=true]");
                d.set(z, 1, x, "oak_leaves[persistent=true]");
            }
        }
        for (int cx : new int[] {-m + 1, size - 1 + m - 1}) {
            for (int cz : new int[] {-m + 1, size - 1 + m - 1}) {
                d.set(cx, 1, cz, "gold_block");
                Props.post(d, cx, 2, cz, 2, "polished_blackstone_wall", "lantern");
            }
        }
        // ---- the great hedge with gold-capped pillars and lanterns ----
        int hh = 6;
        for (int i = lo; i <= hi; i++) {
            for (int y = 1; y <= hh; y++) {
                for (int[] p : new int[][] {{i, lo}, {i, hi}, {lo, i}, {hi, i}}) {
                    boolean pillar = Math.floorMod(i - lo, 7) == 0;
                    d.set(p[0], y, p[1], pillar ? (y == hh ? "gold_block" : "stone_bricks") : "dark_oak_leaves[persistent=true]");
                    if (pillar && y == hh) {
                        d.set(p[0], hh + 1, p[1], "gold_block");
                        d.set(p[0], hh + 2, p[1], "lantern");
                    }
                }
            }
        }
        // inner face of the hedge: lanterns hung in it at eye level between pillars
        for (int i = lo + 3; i <= hi - 3; i += 7) {
            d.set(i, 3, lo + 1, "lantern");
            d.set(i, 3, hi - 1, "lantern");
            d.set(lo + 1, 3, i, "lantern");
            d.set(hi - 1, 3, i, "lantern");
            d.set(i, 2, lo + 1, "stone_brick_wall");
            d.set(i, 2, hi - 1, "stone_brick_wall");
            d.set(lo + 1, 2, i, "stone_brick_wall");
            d.set(hi - 1, 2, i, "stone_brick_wall");
        }
        // ---- four lantern trees at the corners ----
        int t = hedge - 3;
        for (int cx : new int[] {-t, size - 1 + t}) {
            for (int cz : new int[] {-t, size - 1 + t}) {
                tree(d, cx, cz, 12 + size / 8);
            }
        }
        // ---- two topiary colossi of the Gamemaster on the far sides (+x and +z middles) ----
        double h = Math.min(30, 14 + size * 0.35);
        int far = hedge + 4;
        Props.statue(d, size / 2, 0, size - 1 + far, 0, -1, h, "moss_block", "gold_block");
        Props.statue(d, size - 1 + far, 0, size / 2, -1, 0, h, "moss_block", "gold_block");
        for (int[] b : new int[][] {{size / 2, size - 1 + far}, {size - 1 + far, size / 2}}) {
            int r = (int) Math.ceil(h / 16.0 * 4.0);
            d.fill(b[0] - r, 0, b[1] - r, b[0] + r, 0, b[1] + r, "gold_block");
            d.fill(b[0] - r - 1, -1, b[1] - r - 1, b[0] + r + 1, -1, b[1] + r + 1, "chiseled_stone_bricks");
            for (int[] c : new int[][] {{-r - 1, -r - 1}, {r + 1, -r - 1}, {-r - 1, r + 1}, {r + 1, r + 1}}) {
                Props.post(d, b[0] + c[0], 0, b[1] + c[1], 4, "gold_block", "lantern");
            }
        }
        // giant dice tumbled in the corners of the lawn by the start
        Props.die(d, -m - 3.5, 3.0, size / 3.0, 5, 20, 15, 0, "white_concrete", "black_concrete");
        Props.die(d, size / 3.0, 2.5, -m - 3.5, 4, -30, 0, 0, "white_concrete", "red_concrete");
        d.commit();
    }

    /** A rounded lantern tree: a dark oak trunk with branches, a big leaf crown and lanterns hanging under it. */
    static void tree(Deco d, int x, int z, int height) {
        d.fill(x, 0, z, x + 1, height, z + 1, "dark_oak_log");
        for (int[] b : new int[][] {{-3, 0}, {4, 1}, {0, -3}, {1, 4}}) {
            d.line(x + 0.5, height - 3.5, z + 0.5, x + 0.5 + b[0], height - 0.5, z + 0.5 + b[1], "dark_oak_log");
        }
        double r = 4.5 + height / 6.0;
        d.ellipsoid(x + 1, height + 2, z + 1, r, r * 0.65, r, 0, "azalea_leaves[persistent=true]|flowering_azalea_leaves[persistent=true]|azalea_leaves[persistent=true]");
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            int lx = x + (int) Math.round(r * 0.75 * Math.cos(a)), lz = z + (int) Math.round(r * 0.75 * Math.sin(a));
            int bottom = (int) Math.floor(height + 2 - r * 0.65 * Math.sqrt(Math.max(0, 1 - 0.56)));
            d.set(lx, bottom - 1, lz, "iron_chain[axis=y]");
            d.set(lx, bottom - 2, lz, "lantern[hanging=true]");
        }
        // roots / ring bench
        d.tube(x + 1, z + 1, 3.2, 2.2, 0, 0, "podzol");
    }
}
