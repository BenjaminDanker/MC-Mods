package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Buried Treasure: the Genie's Oasis, a desert night out of the Arabian tales. The sand field (x, z -8..8, y -1..0)
 * and the players' sandstone strip (z -10..-9) stay as they are; a cut-sandstone rim closes the gap round the field
 * (nobody drops into it), then dunes, an oasis of palms round a glass pool, striped market tents, a domed palace with
 * minarets, a flying carpet, heaps of gold, and the Gamemaster as a genie rising from a giant golden lamp, turbaned.
 * Players stand at z -9.5 facing +z.
 */
public final class OasisLook {
    private OasisLook() {
    }

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 33333L);
        d.keepOut(-8, -1, -8, 8, 6, 8);
        d.keepOut(-10, 0, -10, 10, 6, -9);
        // ---- the rim round the field: no gap to fall into ----
        for (int x = -10; x <= 10; x++) {
            for (int z = -8; z <= 10; z++) {
                if (Math.abs(x) > 8 || z > 8) {
                    d.set(x, -1, z, "sandstone");
                    d.set(x, 0, z, (Math.abs(x) == 10 || z == 10) ? "chiseled_sandstone" : "cut_sandstone");
                }
            }
        }
        // ---- dunes all round (sand always on a sandstone core: nothing falls) ----
        for (int x = -64; x <= 64; x++) {
            for (int z = -50; z <= 70; z++) {
                if (Math.abs(x) <= 10 && z >= -10 && z <= 10) {
                    continue;
                }
                double r = Math.hypot(x, (z - 10) * 0.9);
                if (r > 62) {
                    continue;
                }
                double dune = Math.max(0, Math.sin(x * 0.11 + z * 0.05) * 2.5 + Math.sin(z * 0.13 - x * 0.04) * 2.0 + (r - 16) * 0.06);
                int top = r < 14 ? 0 : (int) Math.round(dune);
                int bottom = -3 - (int) Math.max(0, (62 - r) * 0.25);
                for (int y = bottom; y <= top; y++) {
                    d.set(x, y, z, y < top - 1 || y < 0 ? "sandstone|smooth_sandstone|sandstone" : "sand");
                }
            }
        }
        // ---- oasis: palms round a pool of glass (no water) ----
        int ox = -30, oz = 12;
        for (int x = ox - 9; x <= ox + 9; x++) {
            for (int z = oz - 7; z <= oz + 7; z++) {
                double e = Math.hypot((x - ox) / 9.0, (z - oz) / 7.0);
                if (e <= 1) {
                    for (int y = -2; y <= 2; y++) {
                        d.erase(x, y, z);
                    }
                    d.set(x, -2, z, "sandstone");
                    d.set(x, -1, z, e > 0.85 ? "grass_block" : "light_blue_stained_glass");
                    d.set(x, 0, z, e > 0.85 ? "short_grass" : "air");
                    if (e <= 0.85) {
                        d.set(x, -2, z, "prismarine_bricks");
                    }
                }
            }
        }
        int[][] palms = {{ox - 10, oz - 3}, {ox + 9, oz + 4}, {ox - 4, oz + 9}, {ox + 4, oz - 8}, {ox - 8, oz + 7}, {14, 22}, {22, 8}, {-16, -18}, {18, -16}};
        for (int i = 0; i < palms.length; i++) {
            palm(d, palms[i][0], palms[i][1], 8 + i % 4, i);
        }
        // ---- market tents on both sides of the strip ----
        tent(d, -19, -14, "red_wool", "white_wool");
        tent(d, 19, -14, "blue_wool", "white_wool");
        tent(d, 24, 2, "orange_wool", "yellow_wool");
        tent(d, -22, -2, "purple_wool", "white_wool");
        // ---- the palace beyond the field ----
        palace(d, 0, 42);
        // ---- flying carpet over the dunes ----
        for (int x = 12; x <= 18; x++) {
            for (int z = 12; z <= 15; z++) {
                int y = 9 + (int) Math.round(Math.sin((x - 12) * 0.9) * 0.6);
                d.set(x, y, z, (z == 12 || z == 15) ? "yellow_wool" : (Math.floorMod(x + z, 2) == 0 ? "magenta_wool" : "red_wool"));
            }
        }
        // ---- the genie rising from his lamp, behind the field ----
        int gx = 0, gz = 21;
        d.ellipsoid(gx + 0.5, 2.5, gz + 0.5, 5.5, 2.6, 3.4, 0, "gold_block");
        d.line(gx - 4.5, 3.5, gz + 0.5, gx - 9.5, 5.5, gz + 0.5, "gold_block");
        d.set(gx - 10, 6, gz, "gold_block");
        for (int y = 2; y <= 5; y++) {
            d.set(gx + 6, y, gz, "gold_block");
        }
        d.set(gx + 7, 5, gz, "gold_block");
        d.set(gx + 7, 3, gz, "gold_block");
        d.cyl(gx + 0.5, gz + 0.5, 2.2, 5, 5, "gold_block");
        Props.statue(d, gx, 5, gz, 0, -1, 18, "cyan_concrete", "gold_block");
        double k = 18 / 16.0;
        int ty = 6 + (int) Math.round(14.4 * k);
        d.ellipsoid(gx + 0.5, ty + 1.5, gz + 0.5, 2.6, 2.0, 2.6, 0, "white_wool");
        d.set(gx, ty + 1, gz - 3, "emerald_block");
        d.set(gx, ty + 2, gz - 3, "gold_block");
        // ---- heaps of gold near the lamp ----
        for (int i = 0; i < 70; i++) {
            int x = -14 + d.rnd.nextInt(29), z = 12 + d.rnd.nextInt(10);
            if (Math.abs(x) <= 10 && z <= 10) {
                continue;
            }
            d.set(x, 1, z, i % 7 == 0 ? "emerald_block" : (i % 5 == 0 ? "raw_gold_block" : "gold_block"));
        }
        // ---- lamps and light ----
        for (int[] p : new int[][] {{-11, -11}, {11, -11}, {-11, 11}, {11, 11}, {-11, 0}, {11, 0}}) {
            for (int y = 1; y <= 3; y++) {
                d.set(p[0], y, p[1], y == 1 ? "chiseled_sandstone" : "smooth_sandstone");
            }
            d.set(p[0], 4, p[1], "lantern[hanging=false]");
        }
        for (int x = -60; x <= 60; x += 6) {
            for (int z = -46; z <= 66; z += 6) {
                if (Math.hypot(x, (z - 10) * 0.9) < 58) {
                    d.set(x, 5, z, "light[level=15]");
                }
            }
        }
        d.commit();
        // light over the sand field, well above the diggers
        for (int x = -8; x <= 8; x += 3) {
            for (int z = -10; z <= 8; z += 3) {
                arena.setLater(x, 4, z, Deco.st("light[level=15]"));
            }
        }
    }

    private static void palm(Deco d, int x, int z, int h, int seed) {
        double lean = (seed % 2 == 0 ? 1 : -1) * 0.18;
        int tx = x, tz = z;
        for (int y = 1; y <= h; y++) {
            tx = x + (int) Math.round(lean * y * y / h);
            d.set(tx, y, tz, "jungle_log[axis=y]");
        }
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + seed;
            for (int s = 1; s <= 4; s++) {
                int lx = tx + (int) Math.round(Math.cos(a) * s), lz = tz + (int) Math.round(Math.sin(a) * s);
                d.set(lx, h + 1 - (s > 2 ? s - 2 : 0), lz, "jungle_leaves[persistent=true]");
            }
        }
        d.set(tx, h + 1, tz, "jungle_leaves[persistent=true]");
        d.set(tx + 1, h - 1, tz, "cocoa[age=2,facing=west]");
    }

    private static void tent(Deco d, int cx, int cz, String a, String b) {
        for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            for (int y = 1; y <= 5; y++) {
                d.set(cx + c[0], y, cz + c[1], "oak_fence[east=false,north=false,south=false,west=false,waterlogged=false]");
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int y = 6 + (4 - Math.max(Math.abs(x), Math.abs(z))) / 2;
                d.set(cx + x, y, cz + z, Math.floorMod(x, 2) == 0 ? a : b);
                d.set(cx + x, 1, cz + z, Math.abs(x) <= 2 && Math.abs(z) <= 2 ? (Math.floorMod(x + z, 2) == 0 ? "red_carpet" : "orange_carpet") : "air");
            }
        }
        d.set(cx, 1, cz, "barrel[facing=up,open=false]");
        d.set(cx, 2, cz, "lantern[hanging=false]");
        d.set(cx + 2, 1, cz - 2, "decorated_pot");
    }

    private static void palace(Deco d, int cx, int cz) {
        int w = 16, dz = 10, wall = 12;
        for (int x = cx - w; x <= cx + w; x++) {
            for (int z = cz; z <= cz + dz; z++) {
                for (int y = 0; y <= wall; y++) {
                    boolean shell = x == cx - w || x == cx + w || z == cz || z == cz + dz || y == wall || y == 0;
                    if (!shell) {
                        continue;
                    }
                    String s = y == wall ? "cut_sandstone" : (y % 4 == 0 ? "chiseled_sandstone" : "smooth_sandstone");
                    // arched windows glowing from within
                    if (z == cz && y >= 3 && y <= 7 && Math.floorMod(x - cx, 4) == 2) {
                        s = y == 7 ? "smooth_sandstone" : "ochre_froglight";
                    }
                    d.set(x, y, z, s);
                }
            }
        }
        // crenellation
        for (int x = cx - w; x <= cx + w; x += 2) {
            d.set(x, wall + 1, cz, "cut_sandstone");
            d.set(x, wall + 1, cz + dz, "cut_sandstone");
        }
        // the great onion dome and two smaller ones
        dome(d, cx, wall + 1, cz + dz / 2, 7.5, "gold_block");
        dome(d, cx - 10, wall + 1, cz + dz / 2, 3.5, "light_blue_terracotta");
        dome(d, cx + 10, wall + 1, cz + dz / 2, 3.5, "light_blue_terracotta");
        // minarets
        for (int s : new int[] {-1, 1}) {
            int mx = cx + s * (w + 4), mz = cz + 2;
            for (int y = 0; y <= 26; y++) {
                d.cyl(mx + 0.5, mz + 0.5, y % 7 == 6 ? 2.6 : 1.9, y, y, y % 7 == 6 ? "chiseled_sandstone" : "smooth_sandstone");
            }
            d.cyl(mx + 0.5, mz + 0.5, 1.9, 22, 23, "ochre_froglight");
            dome(d, mx, 27, mz, 2.4, "gold_block");
        }
        // gate
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int y = 1; y <= 7; y++) {
                if (Math.abs(x - cx) < 3 && y < 6 + (Math.abs(x - cx) < 2 ? 1 : 0)) {
                    d.set(x, y, cz, "dark_oak_planks");
                }
            }
        }
        d.set(cx - 4, 5, cz - 1, "lantern[hanging=false]");
        d.set(cx + 4, 5, cz - 1, "lantern[hanging=false]");
        d.set(cx - 4, 4, cz - 1, "chiseled_sandstone");
        d.set(cx + 4, 4, cz - 1, "chiseled_sandstone");
    }

    /** An onion dome sitting on y, with a spire. */
    private static void dome(Deco d, int cx, int y, int cz, double r, String state) {
        int h = (int) Math.round(r * 2.4);
        for (int i = 0; i <= h; i++) {
            double t = (double) i / h;
            double rr = r * (t < 0.45 ? 0.85 + t * 0.5 : Math.max(0.15, Math.cos((t - 0.45) / 0.55 * Math.PI / 2) * 1.07));
            d.cyl(cx + 0.5, cz + 0.5, rr, y + i, y + i, state);
        }
        for (int i = 1; i <= 3; i++) {
            d.set(cx, y + h + i, cz, "gold_block");
        }
    }
}
