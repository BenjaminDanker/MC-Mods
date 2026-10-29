package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Hot Potato: High Noon in Gamblers' Gulch. The sandstone square (x, z -half..half, y 0) is the plaza of a frontier
 * town: saloon with swing doors and a balcony, a bank, a water tower, hitching rails, barrels of TNT stacked by the
 * gallows-style bell tower (a giant stick of dynamite with a sparkling fuse), red mesas striped with terracotta
 * beyond, cacti and tumbleweeds, and the Gamemaster as the sheriff in a wide hat on the bank's roof. A 7-block
 * drop-away gap keeps everyone on the square (no hiding from the potato in the scenery).
 */
public final class FrontierLook {
    private FrontierLook() {
    }

    public static void build(Arena arena, int tier, int half) {
        Deco d = new Deco(arena, 23232L);
        int c = half + 7;
        d.keepOut(-c, -40, -c, c, 8, c);
        // ---- the town ground beyond the gap ----
        for (int x = -70; x <= 70; x++) {
            for (int z = -70; z <= 70; z++) {
                double r = Math.hypot(x, z);
                if (r > 70 || Math.max(Math.abs(x), Math.abs(z)) <= c) {
                    continue;
                }
                d.set(x, -1, z, Math.floorMod(x * 7 + z * 3, 13) == 0 ? "coarse_dirt" : (Math.floorMod(x + 2 * z, 9) == 0 ? "red_sandstone" : "smooth_sandstone"));
                d.set(x, -2, z, "sandstone");
            }
        }
        // ---- buildings facing the square ----
        saloon(d, -c - 2, 0);             // west side, facing east
        bank(d, c + 2, 0);                 // east side, facing west
        store(d, 0, -c - 2, "GENERAL");    // north
        // water tower (south-east)
        int wx = c + 10, wz = c + 10;
        for (int[] l : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
            d.line(wx + l[0] + 0.5, -0.5, wz + l[1] + 0.5, wx + l[0] * 0.6 + 0.5, 14.5, wz + l[1] * 0.6 + 0.5, "spruce_fence");
        }
        d.cyl(wx + 0.5, wz + 0.5, 4.5, 15, 23, "spruce_planks");
        d.cyl(wx + 0.5, wz + 0.5, 4.8, 16, 16, "iron_block");
        d.cyl(wx + 0.5, wz + 0.5, 4.8, 21, 21, "iron_block");
        for (int k = 0; k < 4; k++) {
            d.cyl(wx + 0.5, wz + 0.5, 4.8 - k * 1.3, 24 + k, 24 + k, "dark_oak_planks");
        }
        // the giant dynamite stick with a fuse and sparks (south-west)
        int tx = -c - 8, tz = c + 8;
        d.cyl(tx + 0.5, tz + 0.5, 3.5, 0, 22, "red_concrete");
        d.cyl(tx + 0.5, tz + 0.5, 3.7, 4, 5, "white_concrete");
        d.cyl(tx + 0.5, tz + 0.5, 3.7, 17, 18, "white_concrete");
        d.line(tx + 0.5, 23.5, tz + 0.5, tx + 3.5, 27.5, tz - 1.5, "brown_concrete");
        d.set(tx + 4, 28, tz - 2, "shroomlight");
        d.set(tx + 4, 29, tz - 2, "end_rod[facing=up]");
        // TNT barrels and crates about town
        for (int i = 0; i < 18; i++) {
            double a = d.rnd.nextDouble() * Math.PI * 2, r = c + 3 + d.rnd.nextDouble() * 6;
            int x = (int) (r * Math.cos(a)), z = (int) (r * Math.sin(a));
            d.set(x, 0, z, i % 3 == 0 ? "tnt" : "barrel[facing=up]");
            if (i % 4 == 0) {
                d.set(x, 1, z, "tnt");
            }
        }
        // hitching rails and lamps round the gap's edge
        for (int i = -c; i <= c; i += 2) {
            for (int[] p : new int[][] {{i, -c - 1}, {i, c + 1}, {-c - 1, i}, {c + 1, i}}) {
                d.set(p[0], 0, p[1], "spruce_fence");
            }
        }
        for (int i = -c; i <= c; i += 8) {
            for (int[] p : new int[][] {{i, -c - 1}, {i, c + 1}, {-c - 1, i}, {c + 1, i}}) {
                Props.post(d, p[0], 0, p[1], 3, "spruce_fence", "lantern");
            }
        }
        // ---- mesas striped with terracotta all round, cacti on the flats ----
        String[] bands = {"orange_terracotta", "terracotta", "yellow_terracotta", "white_terracotta", "red_terracotta", "light_gray_terracotta", "brown_terracotta"};
        int[][] mesas = {{-50, -40, 16, 26}, {48, -30, 12, 34}, {-30, 55, 18, 22}, {40, 50, 14, 30}, {0, -62, 20, 18}};
        for (int[] m : mesas) {
            for (int y = -1; y <= m[3]; y++) {
                double rr = m[2] * (1 - Math.max(0, y - m[3] * 0.7) / (m[3] * 0.6));
                d.cyl(m[0] + 0.5, m[1] + 0.5, rr, y, y, bands[Math.floorMod(y / 2, bands.length)]);
            }
        }
        for (int i = 0; i < 30; i++) {
            double a = d.rnd.nextDouble() * Math.PI * 2, r = c + 18 + d.rnd.nextDouble() * 30;
            int x = (int) (r * Math.cos(a)), z = (int) (r * Math.sin(a));
            int h = 3 + d.rnd.nextInt(5);
            for (int y = 0; y < h; y++) {
                d.set(x, y, z, "green_terracotta");
            }
            d.set(x + 1, h - 2, z, "green_terracotta");
            d.set(x + 1, h - 1, z, "green_terracotta");
            d.set(x - 1, h - 3, z, "green_terracotta");
        }
        d.commit();
        for (int x = -6; x <= 6; x += 6) {
            for (int z = -6; z <= 6; z += 6) {
                arena.setLater(x, 5, z, Deco.st("light[level=15]"));
            }
        }
    }

    static void saloon(Deco d, int x, int z) {
        // front faces +x at x; the building extends to -x
        d.fill(x - 14, 0, z - 9, x, 0, z + 9, "spruce_planks");
        for (int y = 1; y <= 12; y++) {
            for (int zz = z - 9; zz <= z + 9; zz++) {
                d.set(x, y, zz, y >= 10 ? "dark_oak_planks" : (y == 5 ? "dark_oak_planks" : "spruce_planks"));
            }
            d.fill(x - 14, y, z - 9, x - 1, y, z - 9, "spruce_planks");
            d.fill(x - 14, y, z + 9, x - 1, y, z + 9, "spruce_planks");
            d.fill(x - 14, y, z - 9, x - 14, y, z + 9, "spruce_planks");
        }
        d.fill(x - 14, 9, z - 9, x, 9, z + 9, "dark_oak_planks");
        // false front with a sign band
        d.fill(x, 13, z - 7, x, 15, z + 7, "dark_oak_planks");
        d.fill(x + 1, 11, z - 6, x + 1, 13, z + 6, "red_wool");
        // swing doors, windows, balcony with lanterns
        d.eraseBox(x, 1, z - 1, x, 3, z + 1);
        d.set(x, 1, z - 1, "spruce_trapdoor[facing=east,open=true]");
        d.set(x, 1, z + 1, "spruce_trapdoor[facing=east,open=true]");
        for (int zz : new int[] {z - 6, z - 4, z + 4, z + 6}) {
            d.set(x, 2, zz, "yellow_stained_glass");
            d.set(x, 3, zz, "yellow_stained_glass");
            d.set(x, 7, zz, "yellow_stained_glass");
        }
        d.fill(x + 1, 5, z - 9, x + 3, 5, z + 9, "spruce_slab[type=top]");
        d.fill(x + 3, 6, z - 9, x + 3, 6, z + 9, "spruce_fence");
        for (int zz = z - 8; zz <= z + 8; zz += 4) {
            d.set(x + 3, 4, zz, "lantern[hanging=true]");
            d.set(x + 3, 1, zz, "spruce_fence");
            d.set(x + 3, 2, zz, "spruce_fence");
            d.set(x + 3, 3, zz, "spruce_fence");
        }
    }

    static void bank(Deco d, int x, int z) {
        d.fill(x, 0, z - 8, x + 12, 10, z + 8, "smooth_sandstone");
        d.fill(x + 1, 1, z - 7, x + 11, 9, z + 7, "air");
        d.fill(x, 11, z - 8, x + 12, 11, z + 8, "cut_sandstone");
        for (int zz = z - 8; zz <= z + 8; zz += 4) {
            d.fill(x - 1, 0, zz, x - 1, 10, zz, "quartz_pillar");
            d.set(x - 1, 11, zz, "gold_block");
        }
        d.eraseBox(x, 1, z - 1, x, 4, z + 1);
        d.fill(x, 5, z - 2, x, 5, z + 2, "gold_block");
        for (int zz : new int[] {z - 5, z + 5}) {
            d.set(x, 3, zz, "iron_bars");
            d.set(x, 4, zz, "iron_bars");
        }
        // the sheriff on the roof: the Gamemaster with a wide-brimmed hat and a star
        Props.statue(d, x + 6, 11, z, -1, 0, 16);
        int hy = 11 + 1 + 16;
        d.cyl(x + 6.5, z + 0.5, 4.2, hy - 1, hy - 1, "brown_concrete");
        d.cyl(x + 6.5, z + 0.5, 2.2, hy, hy + 1, "brown_concrete");
        d.set(x + 4, 11 + 10, z + 1, "gold_block");
    }

    static void store(Deco d, int x, int z, String name) {
        d.fill(x - 9, 0, z - 12, x + 9, 0, z, "spruce_planks");
        for (int y = 1; y <= 9; y++) {
            d.walls(x - 9, y, z - 12, x + 9, y, z, y % 4 == 0 ? "stripped_spruce_log[axis=x]" : "spruce_planks");
        }
        d.fill(x - 9, 10, z - 12, x + 9, 10, z, "dark_oak_slab[type=bottom]");
        d.fill(x - 7, 10, z, x + 7, 13, z, "spruce_planks");
        d.fill(x - 6, 11, z + 1, x + 6, 12, z + 1, "light_blue_wool");
        d.fill(x - 9, 4, z + 1, x + 9, 4, z + 3, "spruce_slab[type=top]");
        for (int xx = x - 8; xx <= x + 8; xx += 4) {
            d.set(xx, 3, z + 3, "lantern[hanging=true]");
            d.set(xx, 1, z + 3, "spruce_fence");
            d.set(xx, 2, z + 3, "spruce_fence");
        }
        d.eraseBox(x - 1, 1, z, x + 1, 3, z);
        for (int xx : new int[] {x - 6, x - 4, x + 4, x + 6}) {
            d.set(xx, 2, z, "glass");
            d.set(xx, 3, z, "glass");
        }
    }
}
