package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Red Light, Green Light: the Cherry Blossom Shrine Road. The sandstone track (x -6..6, walls at x +-7, z -2..len+3,
 * the light at x -2..2, y 1..5, z len+5) runs through a row of vermilion torii gates under falling blossom; stone
 * lanterns and cherry trees line it on islands set apart from the track, a five-tier pagoda and a snow-capped
 * mountain close the view, and a towering Gamemaster stands behind the light as the one who watches. Everything
 * stays at least two blocks clear of the track walls (no new footing next to them).
 */
public final class ShrineLook {
    private ShrineLook() {
    }

    public static void build(Arena arena, int tier, int len) {
        Deco d = new Deco(arena, 15151L);
        d.keepOut(-9, -3, -6, 9, 7, len + 6);
        d.keepOut(-3, 0, len + 4, 3, 7, len + 6);
        // ---- torii gates every 10 blocks: pillars at x +-10, beams high above the track ----
        for (int z = 4; z <= len; z += 11) {
            torii(d, z, 10, 12);
        }
        torii(d, -6, 12, 14);
        // ---- garden islands on both sides: moss and grass, cherry trees, stone lanterns ----
        for (int side : new int[] {-1, 1}) {
            for (int x = 11; x <= 34; x++) {
                for (int z = -12; z <= len + 16; z++) {
                    int xx = side * x;
                    double edge = 3 * Math.sin(z * 0.17 + side) + 2 * Math.cos(z * 0.07);
                    if (x > 30 + edge) {
                        continue;
                    }
                    d.set(xx, -1, z, x == 11 ? "mossy_stone_bricks" : (Math.floorMod(x * 3 + z * 7, 13) == 0 ? "moss_block" : "grass_block"));
                    d.set(xx, -2, z, "dirt");
                    d.set(xx, -3, z, "dirt");
                    if (x > 12 && Math.floorMod(x * 5 + z * 11, 17) == 0) {
                        d.set(xx, 0, z, "pink_petals[facing=north,flower_amount=4]");
                    }
                }
                // a gravel path along the islands
            }
            for (int z = -10; z <= len + 14; z++) {
                d.set(side * 15, -1, z, "gravel");
                d.set(side * 16, -1, z, "gravel");
            }
            for (int z = 0; z <= len + 10; z += 9) {
                stoneLantern(d, side * 13, 0, z);
            }
            for (int z = -6; z <= len + 12; z += 12) {
                cherry(d, side * (21 + Math.floorMod(z, 5)), 0, z + 3, 8 + Math.floorMod(z, 3));
            }
        }
        // ---- a pagoda on the left beyond the islands, a mountain ahead ----
        pagoda(d, -46, -1, len / 2 + 6, 5);
        for (int x = -90; x <= 90; x++) {
            for (int z = len + 60; z <= len + 130; z++) {
                double r = Math.hypot(x / 1.4, z - (len + 100));
                int h = (int) Math.round(70 - r * 1.3);
                if (h < -10) {
                    continue;
                }
                for (int y = Math.max(-10, h - 3); y <= h; y++) {
                    d.set(x, y, z, h > 46 ? "snow_block" : h > 36 ? (Math.floorMod(x + z, 3) == 0 ? "snow_block" : "stone") : "stone|andesite|stone|tuff");
                }
            }
        }
        // ---- the watcher: the Gamemaster behind the light, on a red shrine platform ----
        int sz = len + 14;
        d.fill(-9, -1, sz - 6, 9, 0, sz + 6, "red_terracotta");
        d.fill(-10, 1, sz - 7, 10, 1, sz - 7, "dark_oak_fence");
        Props.statue(d, 0, 0, sz, 0, -1, 26);
        for (int x : new int[] {-8, 8}) {
            Props.post(d, x, 1, sz - 5, 6, "red_nether_brick_wall", "lantern");
        }
        // ---- falling blossom: pink leaves drifting in the air above the track ----
        for (int i = 0; i < 70; i++) {
            int x = d.rnd.nextInt(41) - 20, y = 8 + d.rnd.nextInt(10), z = d.rnd.nextInt(len + 10);
            d.set(x, y, z, "cherry_leaves[persistent=true]");
        }
        // ---- light over the track for the night ----
        for (int z = 0; z <= len; z += 8) {
            arena.setLater(-4, 9, z, Deco.st("light[level=15]"));
            arena.setLater(4, 9, z, Deco.st("light[level=15]"));
        }
        d.commit();
    }

    /** A vermilion torii across the track at z: pillars at x +-px, top beam at y top. */
    static void torii(Deco d, int z, int px, int top) {
        for (int x : new int[] {-px, px}) {
            d.fill(x, -3, z, x, top - 1, z, "red_concrete");
            d.set(x, -3, z, "polished_blackstone");
            d.set(x, -2, z, "polished_blackstone");
        }
        d.fill(-px - 2, top, z, px + 2, top, z, "red_concrete");
        d.fill(-px - 3, top + 1, z, px + 3, top + 1, z, "black_concrete");
        d.set(-px - 3, top, z, "black_concrete");
        d.set(px + 3, top, z, "black_concrete");
        d.fill(-px, top - 3, z, px, top - 3, z, "red_concrete");
        d.set(0, top - 2, z, "gold_block");
        d.set(0, top - 4, z, "lantern[hanging=true]");
        d.set(-px + 3, top - 4, z, "lantern[hanging=true]");
        d.set(px - 3, top - 4, z, "lantern[hanging=true]");
    }

    static void stoneLantern(Deco d, int x, int y, int z) {
        d.set(x, y, z, "stone_brick_wall");
        d.set(x, y + 1, z, "stone_brick_wall");
        d.set(x, y + 2, z, "lantern");
        d.set(x, y + 3, z, "stone_brick_slab[type=bottom]");
    }

    static void cherry(Deco d, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            d.set(x + (i > h / 2 ? 1 : 0), y + i, z, "cherry_log");
        }
        d.ellipsoid(x + 1.5, y + h + 1, z + 0.5, 5, 3, 5, 0, "cherry_leaves[persistent=true]");
        d.ellipsoid(x - 1.5, y + h - 1, z + 2.5, 3, 2, 3, 0, "cherry_leaves[persistent=true]");
    }

    static void pagoda(Deco d, int x, int y, int z, int tiers) {
        int h = 0;
        for (int t = 0; t < tiers; t++) {
            int r = 7 - t;
            d.fill(x - r + 1, y + h, z - r + 1, x + r - 1, y + h + 4, z + r - 1, "dark_oak_planks");
            d.fill(x - r + 1, y + h + 1, z - r + 1, x + r - 1, y + h + 3, z - r + 1, "red_concrete");
            d.fill(x + r - 1, y + h + 1, z - r + 1, x + r - 1, y + h + 3, z + r - 1, "red_concrete");
            d.set(x, y + h + 2, z - r + 1, "glowstone");
            d.set(x + r - 1, y + h + 2, z, "glowstone");
            for (int i = 0; i <= 1; i++) {
                int rr = r + 2 - i;
                d.walls(x - rr, y + h + 5 + i, z - rr, x + rr, y + h + 5 + i, z + rr, "deepslate_tile_slab[type=bottom]");
            }
            d.fill(x - r, y + h + 5, z - r, x + r, y + h + 5, z + r, "deepslate_tiles");
            for (int[] c : new int[][] {{-r - 2, -r - 2}, {r + 2, -r - 2}, {-r - 2, r + 2}, {r + 2, r + 2}}) {
                d.set(x + c[0], y + h + 4, z + c[1], "lantern[hanging=true]");
                d.set(x + c[0], y + h + 5, z + c[1], "gold_block");
            }
            h += 6;
        }
        for (int i = 0; i < 6; i++) {
            d.set(x, y + h + i, z, i % 2 == 0 ? "gold_block" : "iron_chain[axis=y]");
        }
        d.fill(x - 9, y - 1, z - 9, x + 9, y - 1, z + 9, "smooth_stone");
    }
}
