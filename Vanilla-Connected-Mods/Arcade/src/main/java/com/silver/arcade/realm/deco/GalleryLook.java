package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Pattern Copy: the Gamemaster's Grand Gallery. A marble picture gallery under a gold-ribbed glass roof. The board
 * (dark oak on z 6, picture above the canvas on z 5) gets a heavy gilt frame; the side walls hang framed pixel
 * paintings with picture lamps, velvet ropes guard plinths with sculptures (dice, busts), easels and paint pots stand
 * about, and two marble statues of him admire his own taste. Players stand near z 0.5 facing +z.
 */
public final class GalleryLook {
    private GalleryLook() {
    }

    public static void build(Arena arena, int tier, int size) {
        Deco d = new Deco(arena, 11111L + size);
        int boardTop = 2 + 2 * size + 1;
        d.keepOut(-4, 0, -3, 4, 0, 5);
        d.keepOut(-4, 1, -3, 4, boardTop, 6);
        int x0 = -17, x1 = 17, z0 = -14, z1 = 20, top = 18;
        // ---- floor: marble with a herringbone parquet runner ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                String s;
                if (x == x0 || x == x1 || z == z0 || z == z1) {
                    s = "polished_blackstone_bricks";
                } else if (Math.abs(x) <= 6) {
                    s = ((x + z) & 3) < 2 ? "oak_planks" : "birch_planks";
                } else {
                    s = Math.floorMod(x * 3 + z * 5, 11) == 0 ? "polished_diorite" : "smooth_quartz";
                }
                d.set(x, 0, z, s);
            }
        }
        // ---- walls: quartz with gold skirting and cornice, pilasters ----
        for (int y = 1; y <= top; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z : new int[] {z0, z1}) {
                    d.set(x, y, z, wall(x - x0, y, top));
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int x : new int[] {x0, x1}) {
                    d.set(x, y, z, wall(z - z0, y, top));
                }
            }
        }
        // ---- glass roof with gold ribs, lamps hanging from the ribs ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int ridge = top + 1 + (17 - Math.abs(x)) / 3;
                boolean rib = Math.floorMod(z - z0, 5) == 0 || x == x0 || x == x1;
                d.set(x, ridge, z, rib ? "gold_block" : "glass");
                if (rib && Math.floorMod(x, 6) == 0 && z > z0 && z < z1) {
                    d.set(x, ridge - 1, z, "iron_chain[axis=y]");
                    d.set(x, ridge - 2, z, "lantern[hanging=true]");
                }
            }
        }
        for (int z : new int[] {z0, z1}) {
            for (int x = x0; x <= x1; x++) {
                int ridge = top + 1 + (17 - Math.abs(x)) / 3;
                for (int y = top + 1; y < ridge; y++) {
                    d.set(x, y, z, "smooth_quartz");
                }
            }
        }
        // ---- a heavy gilt frame round the board ----
        for (int y = 0; y <= boardTop + 1; y++) {
            for (int x : new int[] {-5, 5}) {
                d.set(x, y, 6, (y & 1) == 0 ? "gold_block" : "raw_gold_block");
                d.set(x, y, 5, "gold_block");
            }
        }
        for (int x = -6; x <= 6; x++) {
            d.set(x, boardTop + 1, 6, "gold_block");
            d.set(x, boardTop + 1, 5, "gold_block");
            d.set(x, boardTop + 2, 6, Math.abs(x) % 3 == 0 ? "raw_gold_block" : "gold_block");
        }
        d.set(0, boardTop + 3, 6, "gold_block");
        d.set(-1, boardTop + 3, 6, "gold_block");
        d.set(1, boardTop + 3, 6, "gold_block");
        d.set(0, boardTop + 4, 6, "gold_block");
        // picture lamps over the board
        for (int x = -3; x <= 3; x += 3) {
            d.set(x, boardTop + 2, 4, "lantern[hanging=true]");
            d.set(x, boardTop + 3, 4, "iron_chain[axis=y]");
            d.set(x, boardTop + 4, 4, "gold_block");
        }
        d.fill(-7, 1, 7, 7, boardTop + 4, 7, "dark_oak_planks");
        d.set(0, boardTop + 1, 2, "light[level=15]");
        d.set(-6, 4, 2, "light[level=15]");
        d.set(6, 4, 2, "light[level=15]");
        for (int z = -10; z <= 16; z += 6) {
            d.set(-11, 9, z, "light[level=15]");
            d.set(11, 9, z, "light[level=15]");
            d.set(0, 14, z, "light[level=15]");
        }
        // ---- framed pixel paintings on the side walls ----
        int k = 0;
        for (int z = z0 + 4; z <= z1 - 6; z += 8) {
            painting(d, x0 + 1, 4, z, 5, 6, true, k++);
            painting(d, x1 - 1, 4, z, 5, 6, false, k++);
        }
        backPainting(d, z0 + 1, 5, 11, 8);
        // ---- plinths with sculptures behind velvet ropes ----
        int[][] plinths = {{-11, -8}, {11, -8}, {-11, 12}, {11, 12}};
        for (int i = 0; i < plinths.length; i++) {
            int px = plinths[i][0], pz = plinths[i][1];
            d.fill(px - 1, 1, pz - 1, px + 1, 2, pz + 1, "chiseled_quartz_block");
            d.fill(px - 1, 3, pz - 1, px + 1, 3, pz + 1, "gold_block");
            if (i % 2 == 0) {
                Props.die(d, px + 0.5, 6.0, pz + 0.5, 4, 30 + i * 20, 35, 20, i == 0 ? "white_concrete" : "red_concrete", i == 0 ? "black_concrete" : "white_concrete");
            } else {
                Props.statue(d, px, 3, pz, (px > 0 ? -1 : 1), 0, 7);
            }
            for (int[] c : new int[][] {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}}) {
                d.set(px + c[0], 1, pz + c[1], "gold_block");
                d.set(px + c[0], 2, pz + c[1], "lightning_rod");
            }
            for (int t = -2; t <= 2; t++) {
                d.set(px + t, 1, pz - 3, "red_carpet");
                d.set(px + t, 1, pz + 3, "red_carpet");
                d.set(px - 3, 1, pz + t, "red_carpet");
                d.set(px + 3, 1, pz + t, "red_carpet");
            }
        }
        // ---- easels with canvases and paint pots near the player ----
        int[][] easels = {{-9, 1}, {9, 1}};
        for (int[] e : easels) {
            int ex = e[0], ez = e[1];
            d.set(ex, 1, ez, "oak_fence");
            d.set(ex, 2, ez, "oak_fence");
            d.set(ex, 3, ez, "oak_fence");
            d.set(ex - 1, 1, ez + 1, "oak_fence");
            d.set(ex + 1, 1, ez + 1, "oak_fence");
            String[] cols = {"red_wool", "yellow_wool", "blue_wool", "lime_wool", "white_wool"};
            for (int y = 2; y <= 4; y++) {
                for (int dx = -1; dx <= 1; dx++) {
                    d.set(ex + dx, y, ez + 1, dx == 0 && y == 3 ? cols[d.rnd.nextInt(cols.length - 1)] : "white_wool");
                }
            }
            for (int dx = -2; dx <= 2; dx += 4) {
                d.set(ex + dx, 1, ez - 1, "decorated_pot");
            }
            d.set(ex, 1, ez - 2, "red_candle[candles=3,lit=true]");
        }
        // paint splashes on the parquet
        String[] splash = {"red_concrete", "yellow_concrete", "blue_concrete", "lime_concrete", "magenta_concrete"};
        for (int i = 0; i < 26; i++) {
            int x = d.rnd.nextInt(2 * 15) - 15, z = z0 + 2 + d.rnd.nextInt(z1 - z0 - 4);
            if (Math.abs(x) <= 5 && z >= -4 && z <= 7) {
                continue;
            }
            d.set(x, 0, z, splash[d.rnd.nextInt(splash.length)]);
        }
        // ---- two marble statues of him admiring the board ----
        Props.statueOnPlinth(d, -13, 1, 16, 1, -1, 11, 1);
        Props.statueOnPlinth(d, 13, 1, 16, -1, -1, 11, 1);
        // benches and lamps along the walk
        for (int z : new int[] {-10, -5}) {
            for (int x = -3; x <= 3; x++) {
                d.set(x, 1, z, "dark_oak_stairs[facing=north]");
            }
        }
        for (int z = z0 + 3; z <= z1 - 3; z += 6) {
            Props.post(d, -7, 1, z, 3, "gold_block", "lantern");
            Props.post(d, 7, 1, z, 3, "gold_block", "lantern");
        }
        d.commit();
    }

    /** A big gilt-framed portrait of the Gamemaster on the back wall (inner face at z), centred on x 0. */
    private static void backPainting(Deco d, int z, int y0, int w, int h) {
        for (int u = -1; u <= w; u++) {
            for (int v = -1; v <= h; v++) {
                int x = u - w / 2;
                String s;
                if (u == -1 || u == w || v == -1 || v == h) {
                    s = "gold_block";
                } else if (Math.abs(x) <= 1 && v >= h - 3) {
                    s = v == h - 3 ? "smooth_quartz" : v == h - 1 ? "gold_block" : (Math.abs(x) == 1 ? "yellow_concrete" : "smooth_quartz");
                } else if (Math.abs(x) <= 2 + (h - 4 - v) / 2 && v < h - 3) {
                    s = Math.abs(x) <= 0 && v >= h - 6 && v <= h - 5 ? "white_concrete" : "purple_concrete";
                } else {
                    s = "magenta_terracotta";
                }
                d.set(x, y0 + v, z, s);
            }
        }
        d.set(-3, y0 + h + 1, z + 1, "lantern");
        d.set(3, y0 + h + 1, z + 1, "lantern");
    }

    private static String wall(int along, int y, int top) {
        if (y == 1 || y == top) {
            return "gold_block";
        }
        if (Math.floorMod(along, 8) == 0) {
            return y % 4 == 0 ? "chiseled_quartz_block" : "quartz_pillar";
        }
        return y == top - 1 ? "gold_block" : "quartz_block";
    }

    /** A gilt-framed pixel painting (w x h) on a side wall (fixed x) or the back wall (fixed z when onX false at x==0). */
    private static void painting(Deco d, int fixed, int y0, int c, int w, int h, boolean leftOrBack, int seed) {
        java.util.Random r = new java.util.Random(seed * 31L + 7);
        String[] skies = {"light_blue_wool", "cyan_wool", "orange_wool", "purple_wool", "blue_wool"};
        String[] lands = {"green_wool", "lime_wool", "brown_wool", "yellow_wool", "gray_wool"};
        String sky = skies[r.nextInt(skies.length)], land = lands[r.nextInt(lands.length)];
        int horizon = 1 + r.nextInt(h / 2 + 1);
        int sunU = 1 + r.nextInt(w - 2), sunV = h - 2;
        boolean back = seed == 99;
        for (int u = -1; u <= w; u++) {
            for (int v = -1; v <= h; v++) {
                String s;
                if (u == -1 || u == w || v == -1 || v == h) {
                    s = "gold_block";
                } else if (u == sunU && v == sunV) {
                    s = "yellow_concrete";
                } else if (v < horizon + (int) Math.round(Math.sin(u * 1.3 + seed) * 1.2)) {
                    s = land;
                } else {
                    s = sky;
                }
                if (back) {
                    d.set(c - w / 2 + u + fixed - c + (c - c), y0 + v, fixed, s);
                } else {
                    d.set(fixed, y0 + v, c + u - w / 2, s);
                }
            }
        }
        // picture lamp
        if (back) {
            d.set(fixed, y0 + h + 1, c + 1, "lantern");
        } else {
            d.set(fixed + (leftOrBack ? 1 : -1), y0 + h, c, "lantern");
        }
    }
}
