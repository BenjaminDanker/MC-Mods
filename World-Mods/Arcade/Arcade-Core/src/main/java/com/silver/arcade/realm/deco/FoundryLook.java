package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * The Rising Floor: the Great Thermometer of the Gamemaster's foundry. The glass shaft (walls at x, z = +-5, up to
 * top + 3) with its ledges stays untouched and its inside stays clear. Outside it becomes a thermometer: a scale of
 * red and white marks up the walls, a glass bulb glowing under the floor, a gold cap. Round it a steampunk foundry
 * floor: copper boilers with gauges, pipes, iron gears, brick smokestacks puffing smoke, catwalk rings round the
 * shaft, and the Gamemaster as the stoker-engineer in goggles and top hat. Players start inside at the bottom.
 */
public final class FoundryLook {
    private FoundryLook() {
    }

    public static void build(Arena arena, int tier, int steps) {
        Deco d = new Deco(arena, 34343L);
        int top = steps + 1, wallTop = top + 3;
        d.keepOut(-5, 0, -5, 5, wallTop + 8, 5);
        // ---- foundry floor round the shaft ----
        for (int x = -30; x <= 30; x++) {
            for (int z = -30; z <= 30; z++) {
                double r = Math.hypot(x, z);
                if (r > 30 || (Math.abs(x) <= 5 && Math.abs(z) <= 5)) {
                    continue;
                }
                d.set(x, 0, z, Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0 ? "cut_copper" : "polished_deepslate");
                d.set(x, -1, z, "deepslate_bricks");
                if (r > 27) {
                    d.set(x, 1, z, "polished_blackstone_bricks");
                    d.set(x, 2, z, Math.floorMod(x + z, 6) == 0 ? "lantern[hanging=false]" : "air");
                }
            }
        }
        // ---- the bulb under the floor, glowing through a glass ring ----
        d.ellipsoid(0.5, -6.5, 0.5, 8, 7, 8, 1.2, "red_stained_glass");
        d.ellipsoid(0.5, -6.5, 0.5, 6.4, 5.4, 6.4, 0, "shroomlight|magma_block|shroomlight");
        for (int x = -7; x <= 7; x++) {
            for (int z = -7; z <= 7; z++) {
                double r = Math.hypot(x, z);
                if (r > 5.6 && r <= 7.5 && (Math.abs(x) > 5 || Math.abs(z) > 5)) {
                    d.set(x, 0, z, "red_stained_glass");
                    d.set(x, -1, z, "shroomlight");
                }
            }
        }
        // ---- the scale: ticks up the outside of the walls, long ones every 6, gold every 12 ----
        for (int y = 2; y <= wallTop; y += 2) {
            boolean big = y % 6 == 0;
            String s = y % 12 == 0 ? "gold_block" : (big ? "red_concrete" : "white_concrete");
            for (int w = -1; w <= 1; w++) {
                for (int out = 6; out <= (big ? 7 : 6); out++) {
                    d.set(out, y, w, s);
                    d.set(-out, y, w, s);
                    d.set(w, y, out, s);
                    d.set(w, y, -out, s);
                }
            }
        }
        // the gold cap and a dial on top
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                if (Math.abs(x) == 6 || Math.abs(z) == 6) {
                    d.set(x, wallTop + 1, z, "gold_block");
                }
            }
        }
        // ---- catwalk rings round the shaft every 12 ----
        for (int y = 12; y <= wallTop - 2; y += 12) {
            for (int x = -8; x <= 8; x++) {
                for (int z = -8; z <= 8; z++) {
                    int m = Math.max(Math.abs(x), Math.abs(z));
                    if (m >= 7 && m <= 8) {
                        d.set(x, y, z, m == 8 ? "waxed_copper_block" : "waxed_cut_copper_slab[type=top]");
                    }
                }
            }
            for (int[] c : new int[][] {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
                for (int yy = 1; yy < y; yy++) {
                    d.set(c[0], yy, c[1], "iron_chain[axis=y]");
                }
                d.set(c[0], y + 1, c[1], "lantern[hanging=false]");
            }
        }
        // ---- boilers, pipes, gears, smokestacks ----
        int[][] boilers = {{-17, -8}, {17, -6}, {-15, 14}, {16, 15}};
        for (int i = 0; i < boilers.length; i++) {
            int bx = boilers[i][0], bz = boilers[i][1];
            for (int y = 1; y <= 9; y++) {
                d.cyl(bx + 0.5, bz + 0.5, 3.6, y, y, y % 4 == 1 ? "waxed_cut_copper" : (i % 2 == 0 ? "waxed_copper_block" : "waxed_exposed_copper"));
            }
            d.ellipsoid(bx + 0.5, 9.5, bz + 0.5, 3.6, 2.2, 3.6, 0, "waxed_copper_block");
            // gauge facing the shaft
            int gx = bx + (bx < 0 ? 4 : -4);
            d.set(gx, 5, bz, "white_concrete");
            d.set(gx, 6, bz, "gold_block");
            // pipe from the boiler to the shaft wall, then up it
            int px = bx < 0 ? -6 : 6;
            for (int x = Math.min(bx, px); x <= Math.max(bx, px); x++) {
                d.set(x, 7, bz < 0 ? -3 : 3, "waxed_copper_grate");
            }
        }
        int[][] stacks = {{-22, 2}, {22, 3}, {0, -22}, {2, 22}};
        for (int[] s : stacks) {
            for (int y = 1; y <= 30; y++) {
                d.cyl(s[0] + 0.5, s[1] + 0.5, y < 4 ? 2.6 : 1.8, y, y, y % 8 == 0 ? "gold_block" : "bricks");
            }
            d.set(s[0], 31, s[1], "campfire[lit=true,signal_fire=true,facing=north,waterlogged=false]");
        }
        for (int[] g : new int[][] {{-12, 6, -14}, {13, 9, 12}, {-24, 8, -10}}) {
            gear(d, g[0], g[1], g[2], 4.5);
        }
        // ---- the engineer Gamemaster in goggles and top hat, facing the shaft ----
        int sx = 0, sz = -16;
        d.fill(sx - 3, 1, sz - 3, sx + 3, 1, sz + 3, "waxed_cut_copper");
        Props.statue(d, sx, 1, sz, 0, 1, 20, "brown_concrete", "waxed_copper_block");
        double k = 20 / 16.0;
        int eyeY = 2 + (int) Math.round(13.2 * k);
        d.set(sx - 1, eyeY, sz + 2, "orange_stained_glass");
        d.set(sx + 1, eyeY, sz + 2, "orange_stained_glass");
        int hy = 2 + (int) Math.round(15.0 * k);
        d.cyl(sx + 0.5, sz + 0.5, 3.0, hy, hy, "black_wool");
        d.cyl(sx + 0.5, sz + 0.5, 1.9, hy + 1, hy + 4, "black_wool");
        d.cyl(sx + 0.5, sz + 0.5, 1.9, hy + 1, hy + 1, "red_wool");
        // ---- light ----
        for (int x = -28; x <= 28; x += 6) {
            for (int z = -28; z <= 28; z += 6) {
                if (Math.hypot(x, z) < 27 && (Math.abs(x) > 6 || Math.abs(z) > 6)) {
                    d.set(x, 4, z, "light[level=15]");
                }
            }
        }
        d.commit();
    }

    /** An iron gear on its side in the x-y plane at z. */
    private static void gear(Deco d, int cx, int cy, int cz, double r) {
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                double rr = Math.hypot(x, y);
                double a = Math.atan2(y, x);
                boolean tooth = rr <= r + 1.2 && rr > r && Math.floorMod((int) Math.floor(a / (Math.PI / 5)), 2) == 0;
                if (tooth || (rr <= r && rr > r - 1.2) || rr <= 1.2 || (rr <= r && (Math.abs(x) < 0.6 || Math.abs(y) < 0.6))) {
                    d.set(cx + x, cy + y, cz, "iron_block");
                }
            }
        }
    }
}
