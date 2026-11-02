package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Hide and Seek: the Haunted Manor Garden. The walled yard (x, z -half..half, wall at +-(half+1), y 1..4) is the
 * overgrown garden of a gothic manor: the wall is raised with a dark hedge and spiked iron railings to y 9 (no way
 * over), gas lamps line it, and outside stand the crooked manor with lit windows and a clock tower, a graveyard with
 * leaning headstones and dead trees, jack-o'-lanterns, a ghostly pale statue of the Gamemaster and a pale moon.
 * Inside the yard only invisible light is added (the huts, crates and hedges are the game).
 */
public final class ManorLook {
    private ManorLook() {
    }

    public static void build(Arena arena, int tier, int half) {
        Deco d = new Deco(arena, 25252L);
        int w = half + 1;
        d.keepOut(-half, 0, -half, half, 12, half); // the yard itself
        // ---- the raised wall: hedge to y 7, iron railing spikes to y 9, gas lamps on pillars ----
        for (int i = -w; i <= w; i++) {
            for (int[] p : new int[][] {{i, -w}, {i, w}, {-w, i}, {w, i}}) {
                boolean pillar = Math.floorMod(i, 6) == 0 || Math.abs(i) == w;
                for (int y = 5; y <= 7; y++) {
                    d.set(p[0], y, p[1], pillar ? "polished_blackstone_bricks" : "dark_oak_leaves[persistent=true]");
                }
                d.set(p[0], 8, p[1], pillar ? "polished_blackstone_bricks" : "iron_bars");
                d.set(p[0], 9, p[1], pillar ? "soul_lantern" : "iron_bars");
                // outer skin: stone and ivy
                int ox = p[0] + Integer.signum(p[0]) * (Math.abs(p[0]) == w ? 1 : 0), oz = p[1] + Integer.signum(p[1]) * (Math.abs(p[1]) == w ? 1 : 0);
                for (int y = 0; y <= 4; y++) {
                    d.set(ox, y, oz, Math.floorMod(i + y, 4) == 0 ? "mossy_stone_bricks" : "stone_bricks");
                }
            }
        }
        // the wrought-iron gate on the south side (decorative, closed)
        for (int x = -2; x <= 2; x++) {
            for (int y = 1; y <= 7; y++) {
                d.set(x, y, w + 2, (x == 0 && y > 5) ? "gold_block" : "iron_bars");
            }
        }
        // ---- ground outside: dark grass, podzol paths, mist (white glass) pooled low ----
        for (int x = -70; x <= 70; x++) {
            for (int z = -70; z <= 70; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) <= w + 1 || Math.hypot(x, z) > 72) {
                    continue;
                }
                d.set(x, 0, z, Math.floorMod(x * 7 + z * 3, 5) == 0 ? "podzol" : (Math.floorMod(x + z * 2, 11) == 0 ? "coarse_dirt" : "grass_block"));
                d.set(x, -1, z, "dirt");
                int k = Math.floorMod(x * 13 + z * 17, 31);
                if (k == 0) {
                    d.set(x, 1, z, "white_stained_glass");
                } else if (k < 3) {
                    d.set(x, 1, z, "dead_bush|short_grass|fern");
                }
            }
        }
        // ---- the manor to the north ----
        manor(d, 0, 1, -w - 34);
        // ---- graveyard east, dead trees west ----
        for (int i = 0; i < 26; i++) {
            int x = w + 6 + (i % 5) * 4, z = -18 + (i / 5) * 7 + (i % 2);
            headstone(d, x, 1, z, i);
        }
        for (int i = 0; i < 12; i++) {
            deadTree(d, -w - 8 - d.rnd.nextInt(28), 1, -30 + d.rnd.nextInt(60), 8 + d.rnd.nextInt(7));
        }
        for (int i = 0; i < 16; i++) {
            double a = d.rnd.nextDouble() * Math.PI * 2, r = w + 4 + d.rnd.nextDouble() * 20;
            d.set((int) (r * Math.cos(a)), 1, (int) (r * Math.sin(a)), "jack_o_lantern[facing=" + (i % 2 == 0 ? "north" : "east") + "]");
        }
        // ---- the pale Gamemaster to the south, beyond the gate, and the moon ----
        Props.statue(d, 0, 0, w + 24, 0, -1, 26, "white_terracotta", "light_gray_concrete");
        d.ellipsoid(-40, 70, -60, 12, 12, 2, 0, "white_concrete");
        d.ellipsoid(-37, 73, -59, 3, 3, 1, 0, "light_gray_concrete");
        d.ellipsoid(-44, 66, -59, 2, 2, 1, 0, "light_gray_concrete");
        d.commit();
        // dim, eerie but playable light over the yard (invisible)
        for (int x = -12; x <= 12; x += 8) {
            for (int z = -12; z <= 12; z += 8) {
                arena.setLater(x, 6, z, Deco.st("light[level=12]"));
            }
        }
    }

    static void manor(Deco d, int x, int y, int z) {
        int hw = 22, hd = 9, h = 16;
        for (int xx = x - hw; xx <= x + hw; xx++) {
            for (int zz = z - hd; zz <= z + hd; zz++) {
                boolean wall = xx == x - hw || xx == x + hw || zz == z - hd || zz == z + hd;
                if (!wall) {
                    continue;
                }
                for (int yy = y; yy <= y + h; yy++) {
                    boolean window = zz == z + hd && Math.floorMod(xx - x, 4) == 2 && Math.floorMod(yy - y, 6) >= 2 && Math.floorMod(yy - y, 6) <= 4;
                    d.set(xx, yy, zz, window ? (Math.floorMod(xx * 3 + yy, 4) == 0 ? "black_stained_glass" : "orange_stained_glass")
                        : (Math.floorMod(yy - y, 6) == 0 ? "dark_oak_planks" : "deepslate_bricks"));
                    if (window) {
                        d.set(xx, yy, zz - 1, "glowstone");
                    }
                }
            }
        }
        // steep roof with gables
        for (int k = 0; k <= hd + 2; k++) {
            for (int xx = x - hw - 1; xx <= x + hw + 1; xx++) {
                d.set(xx, y + h + 1 + k, z - hd - 1 + k, "deepslate_tile_stairs[facing=south]");
                d.set(xx, y + h + 1 + k, z + hd + 1 - k, "deepslate_tile_stairs[facing=north]");
            }
        }
        // clock tower in the middle with a gold-faced clock
        for (int yy = y; yy <= y + h + 26; yy++) {
            d.walls(x - 3, yy, z + hd - 3, x + 3, yy, z + hd + 3, "deepslate_bricks");
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                d.set(x + dx, y + h + 18 + dy, z + hd + 4, Math.abs(dx) == 2 || Math.abs(dy) == 2 ? "gold_block" : "white_concrete");
            }
        }
        d.set(x, y + h + 19, z + hd + 5, "black_concrete");
        d.set(x + 1, y + h + 18, z + hd + 5, "black_concrete");
        for (int k = 0; k < 8; k++) {
            d.walls(x - 3 + k / 2, y + h + 27 + k, z + hd - 3 + k / 2, x + 3 - k / 2, y + h + 27 + k, z + hd + 3 - k / 2, "deepslate_tiles");
        }
        d.set(x, y + h + 36, z + hd, "lightning_rod");
        // a crooked porch light
        d.set(x, y + 3, z + hd + 4, "soul_lantern");
    }

    static void headstone(Deco d, int x, int y, int z, int i) {
        String s = i % 3 == 0 ? "mossy_cobblestone_wall" : (i % 3 == 1 ? "andesite_wall" : "stone_brick_wall");
        d.set(x, y, z, s);
        d.set(x, y + 1, z, i % 4 == 0 ? "chiseled_stone_bricks" : s);
        d.set(x, y - 1, z + 1, "coarse_dirt");
        if (i % 7 == 0) {
            d.set(x, y + 2, z, "candle[candles=2,lit=true]");
        }
    }

    static void deadTree(Deco d, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            d.set(x + (i > h / 2 ? (i - h / 2) / 2 : 0), y + i, z, "dark_oak_log");
        }
        int tx = x + (h / 2) / 2, ty = y + h - 1;
        d.line(tx + 0.5, ty + 0.5, z + 0.5, tx - 3.5, ty + 3.5, z + 1.5, "dark_oak_log");
        d.line(tx + 0.5, ty - 1.5, z + 0.5, tx + 4.5, ty + 1.5, z - 1.5, "dark_oak_log");
        d.set(tx + 4, ty, z - 2, "iron_chain[axis=y]");
        d.set(tx + 4, ty - 1, z - 2, "soul_lantern[hanging=true]");
    }
}
