package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Musical Chairs: the Mad Tea Party. The flat blackstone floor (x, z -radius-2..radius+2, y 0) is left flat and clear;
 * around it an enchanted forest clearing: giant toadstools, an enormous tipped-over teapot and teacups, a long table
 * of oversized chairs, string lights between lantern trees, a giant pocket watch, and the Gamemaster as the Hatter
 * with a towering top hat. A three-block moat of air surrounds the floor.
 */
public final class TeaPartyLook {
    private TeaPartyLook() {
    }

    public static void build(Arena arena, int tier, int radius) {
        Deco d = new Deco(arena, 21212L);
        int edge = radius + 2, c = edge + 7; // too wide to jump across
        d.keepOut(-c, -30, -c, c, 6, c);
        // ---- forest floor beyond the moat ----
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                double r = Math.hypot(x, z);
                if (r > 58 || Math.max(Math.abs(x), Math.abs(z)) <= c) {
                    continue;
                }
                int h = -1 + (int) Math.round(Math.max(0, r - 20) * 0.12);
                d.set(x, h, z, Math.floorMod(x * 5 + z * 3, 11) == 0 ? "moss_block" : "grass_block");
                d.set(x, h - 1, z, "dirt");
                d.set(x, h - 2, z, "dirt");
                int k = Math.floorMod(x * 13 + z * 7, 29);
                if (k == 0) {
                    d.set(x, h + 1, z, "red_mushroom|brown_mushroom|allium|blue_orchid|pink_tulip|oxeye_daisy");
                } else if (k < 4) {
                    d.set(x, h + 1, z, "short_grass|fern");
                }
            }
        }
        // ---- giant toadstools ----
        int[][] shrooms = {{-c - 8, -8, 10}, {c + 9, 4, 13}, {-6, c + 12, 9}, {14, -c - 10, 11}, {-c - 16, 16, 15}, {c + 18, -18, 12}};
        for (int[] s : shrooms) {
            toadstool(d, s[0], 0, s[1], s[2]);
        }
        // ---- lantern trees in a ring with string lights between ----
        int n = 8;
        int[][] trees = new int[n][];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + 0.2;
            int x = (int) Math.round(30 * Math.cos(a)), z = (int) Math.round(30 * Math.sin(a));
            trees[i] = new int[] {x, z};
            GardenLook.tree(d, x, z, 9 + i % 3);
        }
        for (int i = 0; i < n; i++) {
            int[] a = trees[i], b = trees[(i + 1) % n];
            for (int s = 0; s <= 20; s++) {
                double t = s / 20.0;
                double x = a[0] + (b[0] - a[0]) * t, z = a[1] + (b[1] - a[1]) * t;
                double y = 9 - 3 * Math.sin(t * Math.PI);
                d.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z), s % 3 == 0 ? "lantern[hanging=true]" : "iron_chain[axis=x]");
            }
        }
        // ---- the long tea table with oversized chairs (beyond the moat, -z side) ----
        int tz = -c - 7;
        d.fill(-14, 3, tz - 2, 14, 3, tz + 2, "white_wool");
        for (int x = -14; x <= 14; x += 7) {
            d.fill(x, 0, tz, x, 2, tz, "dark_oak_log");
        }
        for (int x = -12; x <= 12; x += 4) {
            d.set(x, 4, tz, x % 8 == 0 ? "candle[candles=4,lit=true]" : "cake");
            d.set(x + 1, 4, tz + 1, "decorated_pot");
            bigChair(d, x, 0, tz - 4, "south", Math.floorMod(x, 3));
        }
        // ---- the giant tipped teapot and cups (+x side) ----
        teapot(d, c + 14, 0, 14);
        cup(d, c + 6, 0, 22, "light_blue_concrete");
        cup(d, -c - 6, 0, -22, "pink_concrete");
        // ---- the giant pocket watch leaning on a tree (-x) ----
        for (int y = 0; y <= 14; y++) {
            for (int z = -7; z <= 7; z++) {
                double r = Math.hypot(z, y - 7);
                if (r <= 7.3) {
                    d.set(-c - 22, y, z, r > 6.3 ? "gold_block" : "white_concrete");
                }
            }
        }
        d.line(-c - 22.5, 7.5, 0.5, -c - 22.5, 12.5, 0.5, "black_concrete");
        d.line(-c - 22.5, 7.5, 0.5, -c - 22.5, 7.5, 4.5, "black_concrete");
        d.fill(-c - 22, 15, -1, -c - 22, 16, 1, "gold_block");
        // ---- the Hatter: the Gamemaster with a towering top hat (+z) ----
        int hz = c + 22;
        Props.statue(d, 0, 0, hz, 0, -1, 22);
        double k = 22 / 16.0;
        int hatY = (int) Math.round(1 + 15.9 * k);
        d.cyl(0.5, hz + 0.5, 3.0 * k, hatY, hatY, "black_concrete");
        d.cyl(0.5, hz + 0.5, 2.0 * k, hatY + 1, hatY + 9, "black_concrete");
        d.cyl(0.5, hz + 0.5, 2.0 * k, hatY + 2, hatY + 2, "purple_concrete");
        d.set(-1, hatY + 3, (int) Math.floor(hz + 0.5 - 2.0 * k), "white_concrete");
        Props.die(d, 22.5, 3.0, -26.5, 5, 25, 30, 0, "white_concrete", "magenta_concrete");
        d.commit();
        for (int x = -6; x <= 6; x += 6) {
            for (int z = -6; z <= 6; z += 6) {
                arena.setLater(x, 5, z, Deco.st("light[level=15]"));
            }
        }
    }

    static void toadstool(Deco d, int x, int y, int z, int h) {
        d.cyl(x + 0.5, z + 0.5, 1.6, y, y + h, "mushroom_stem");
        double r = h * 0.55 + 2;
        for (int yy = 0; yy <= (int) (r * 0.6); yy++) {
            double rr = r * Math.sqrt(Math.max(0, 1 - Math.pow(yy / (r * 0.6 + 0.5), 2)));
            d.tube(x + 0.5, z + 0.5, rr, rr - 1.5, y + h + yy, y + h + yy, "red_mushroom_block");
            if (yy == (int) (r * 0.6)) {
                d.cyl(x + 0.5, z + 0.5, rr, y + h + yy, y + h + yy, "red_mushroom_block");
            }
        }
        d.cyl(x + 0.5, z + 0.5, r - 0.5, y + h, y + h, "shroomlight");
        for (int i = 0; i < 6; i++) {
            double a = Math.PI / 3 * i;
            int yy = y + h + (int) (r * 0.35);
            int sx = (int) Math.floor(x + 0.5 + r * 0.8 * Math.cos(a)), sz = (int) Math.floor(z + 0.5 + r * 0.8 * Math.sin(a));
            d.set(sx, yy, sz, "white_concrete");
        }
    }

    static void bigChair(Deco d, int x, int y, int z, String facing, int colour) {
        String[] cols = {"purple_wool", "red_wool", "light_blue_wool"};
        d.fill(x - 1, y, z - 1, x + 1, y + 2, z + 1, "dark_oak_planks");
        d.fill(x - 1, y + 3, z - 1, x + 1, y + 3, z + 1, cols[colour]);
        d.fill(x - 1, y + 4, z - 1, x + 1, y + 8, z - 1, "dark_oak_planks");
        d.fill(x, y + 5, z - 1, x, y + 7, z - 1, cols[colour]);
        d.set(x - 1, y + 9, z - 1, "gold_block");
        d.set(x + 1, y + 9, z - 1, "gold_block");
    }

    static void teapot(Deco d, int x, int y, int z) {
        d.ellipsoid(x + 0.5, y + 6, z + 0.5, 8, 6.5, 7, 0, "white_concrete");
        d.ellipsoid(x + 0.5, y + 6, z + 0.5, 8.2, 1.2, 7.2, 0, "blue_concrete");
        d.ellipsoid(x + 0.5, y + 12.5, z + 0.5, 4, 1.5, 3.5, 0, "white_concrete");
        d.set(x, y + 14, z, "gold_block");
        d.line(x - 7.5, y + 5.5, z + 0.5, x - 13.5, y + 11.5, z + 0.5, "white_concrete");
        d.line(x - 8.5, y + 5.5, z + 0.5, x - 13.5, y + 10.5, z + 0.5, "white_concrete");
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * i / 5;
            d.set((int) Math.floor(x + 8.5 + 3 * Math.sin(a)), (int) Math.floor(y + 6 + 4 * Math.cos(a)), z, "white_concrete");
        }
        // tea pouring out of the spout
        for (int yy = y; yy <= y + 10; yy++) {
            d.set(x - 14, yy, z, yy % 3 == 0 ? "orange_stained_glass" : "brown_stained_glass");
        }
    }

    static void cup(Deco d, int x, int y, int z, String colour) {
        for (int yy = 0; yy <= 5; yy++) {
            double r = 3 + yy * 0.4;
            d.tube(x + 0.5, z + 0.5, r, yy == 0 ? -1 : r - 1.2, y + yy, y + yy, yy == 5 ? "gold_block" : colour);
        }
        d.fill(x + 1 - 2, y + 4, z - 1 + 1, x + 1 + 2, y + 4, z + 1 - 1, "brown_concrete");
        d.ellipsoid(x + 0.5, y, z + 0.5, 6, 0.6, 6, 0, "white_concrete");
    }
}
