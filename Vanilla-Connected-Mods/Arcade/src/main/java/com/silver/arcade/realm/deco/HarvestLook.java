package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Whack-a-Mole: the Harvest Fair. The grass field with its coarse-dirt holes (x -2..2s+1, z -3..2s+3, y 0; lanterns pop
 * up at y 1) is ringed by hay bales (nobody walks off the edge); around it a night-time pumpkin farm: patches of
 * pumpkins and hay, a red barn with a hay loft, a windmill, string lights on posts, a giant mallet, and the Gamemaster
 * as a patchwork scarecrow in a straw hat. Players stand at z -1.5 facing +z.
 */
public final class HarvestLook {
    private HarvestLook() {
    }

    public static void build(Arena arena, int tier, int size) {
        Deco d = new Deco(arena, 30303L);
        int fx1 = size * 2 + 1, fz1 = size * 2 + 3;
        d.keepOut(-2, 0, -3, fx1, 8, fz1);
        int cx = size - 1, cz = size;
        // ---- the farm ground, an island of dirt with a skirt of earth below ----
        for (int x = -46; x <= 46; x++) {
            for (int z = -40; z <= 52; z++) {
                double r = Math.hypot(x - cx, (z - cz) * 0.85);
                if (r > 44 + 3 * Math.sin(x * 0.3 + z * 0.2)) {
                    continue;
                }
                boolean row = Math.floorMod(z, 4) == 0;
                d.set(x, 0, z, row ? "coarse_dirt" : (Math.floorMod(x * 3 + z, 9) == 0 ? "podzol" : "grass_block"));
                int depth = 1 + (int) Math.max(0, (44 - r) * 0.35);
                for (int y = 1; y <= depth; y++) {
                    d.set(x, -y, z, y < 3 ? "dirt" : (y < depth - 1 ? "stone|andesite|stone" : "tuff"));
                }
                if (r > 14 && !row && Math.floorMod(x * 7 + z * 11, 13) == 0) {
                    d.set(x, 1, z, "short_grass|fern|short_grass|poppy|dandelion");
                }
            }
        }
        // ---- hay-bale ring round the field (the edge everyone would otherwise fall from) ----
        for (int x = -3; x <= fx1 + 1; x++) {
            for (int z : new int[] {-4, fz1 + 1}) {
                d.set(x, 0, z, "coarse_dirt");
                d.set(x, 1, z, "hay_block[axis=x]");
            }
        }
        for (int z = -4; z <= fz1 + 1; z++) {
            for (int x : new int[] {-3, fx1 + 1}) {
                d.set(x, 0, z, "coarse_dirt");
                d.set(x, 1, z, "hay_block[axis=z]");
            }
        }
        int[][] corners = {{-3, -4}, {fx1 + 1, -4}, {-3, fz1 + 1}, {fx1 + 1, fz1 + 1}};
        for (int[] c : corners) {
            for (int y = 1; y <= 4; y++) {
                d.set(c[0], y, c[1], "stripped_spruce_log[axis=y]");
            }
            d.set(c[0], 5, c[1], "lantern[hanging=false]");
        }
        // ---- pumpkin patches and hay stacks in the rows ----
        for (int x = -40; x <= 40; x++) {
            for (int z = -34; z <= 46; z += 4) {
                if (Math.abs(x - cx) < size + 8 && z > -10 && z < fz1 + 8) {
                    continue;
                }
                if (!d.has(x, 0, z)) {
                    continue; // only on the island
                }
                int k = Math.floorMod(x * 17 + z * 5, 7);
                if (k == 0) {
                    d.set(x, 1, z, "pumpkin");
                } else if (k == 3 && Math.floorMod(x, 3) == 0) {
                    d.set(x, 1, z, "melon");
                }
            }
        }
        int[][] stacks = {{cx - 16, -8}, {cx + 17, -2}, {cx - 20, 16}, {cx + 21, 20}, {cx - 9, fz1 + 9}};
        for (int[] s : stacks) {
            d.fill(s[0], 1, s[1], s[0] + 2, 2, s[1] + 1, "hay_block[axis=x]");
            d.fill(s[0] + 1, 3, s[1], s[0] + 1, 3, s[1] + 1, "hay_block[axis=x]");
        }
        // ---- the red barn behind the field ----
        barn(d, cx, fz1 + 18);
        // ---- windmill to the side ----
        windmill(d, cx + 30, fz1 + 4);
        // ---- string lights on posts round the fair ----
        int n = 10;
        int[][] posts = new int[n][];
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + 0.15;
            int x = cx + (int) Math.round(18 * Math.cos(a)), z = cz + (int) Math.round(16 * Math.sin(a));
            posts[i] = new int[] {x, z};
            for (int y = 1; y <= 6; y++) {
                d.set(x, y, z, "spruce_fence[east=false,north=false,south=false,west=false,waterlogged=false]");
            }
            d.set(x, 7, z, "lantern[hanging=false]");
        }
        for (int i = 0; i < n; i++) {
            int[] a = posts[i], b = posts[(i + 1) % n];
            for (int s = 1; s < 16; s++) {
                double t = s / 16.0;
                double x = a[0] + (b[0] - a[0]) * t, z = a[1] + (b[1] - a[1]) * t, y = 7 - 2.2 * Math.sin(t * Math.PI);
                d.set((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z),
                    s % 4 == 0 ? "lantern[hanging=true]"
                        : (Math.abs(b[0] - a[0]) > Math.abs(b[1] - a[1]) ? "iron_chain[axis=x]" : "iron_chain[axis=z]"));
            }
        }
        // ---- the giant mallet, standing on its head, left of the players ----
        int mx = fx1 + 7, mz = 0;
        d.fill(mx - 2, 1, mz - 1, mx + 2, 3, mz + 1, "red_concrete");
        d.fill(mx - 2, 1, mz - 1, mx - 2, 3, mz + 1, "white_concrete");
        d.fill(mx + 2, 1, mz - 1, mx + 2, 3, mz + 1, "white_concrete");
        for (int y = 4; y <= 11; y++) {
            d.set(mx, y, mz, "stripped_oak_log[axis=y]");
        }
        d.set(mx, 12, mz, "red_concrete");
        // ---- the scarecrow Gamemaster, behind the field, facing the players ----
        int sx = cx - 12, sz = fz1 + 6;
        d.fill(sx - 1, 1, sz - 1, sx + 1, 1, sz + 1, "hay_block[axis=y]");
        Props.statue(d, sx, 1, sz, 1, -1, 16, "brown_wool|orange_wool|brown_wool|green_wool", "hay_block[axis=y]");
        hat(d, sx, 2 + 15, sz, 2.8, "hay_block[axis=y]");
        // ---- light: lanterns above, invisible light over the ground ----
        for (int x = cx - 40; x <= cx + 40; x += 6) {
            for (int z = -36; z <= 48; z += 6) {
                if (Math.hypot(x - cx, (z - cz) * 0.85) < 42) {
                    d.set(x, 3, z, "light[level=15]");
                }
            }
        }
        d.commit();
        // light over the field (high above the holes, out of reach)
        for (int x = -1; x <= fx1; x += 3) {
            for (int z = -2; z <= fz1; z += 3) {
                arena.setLater(x, 4, z, Deco.st("light[level=15]"));
            }
        }
    }

    /** A wide-brimmed hat whose brim sits at y (on the statue's head centred at x, z). */
    static void hat(Deco d, int x, int y, int z, double brim, String state) {
        d.tube(x + 0.5, z + 0.5, brim, -1, y, y, state);
        d.tube(x + 0.5, z + 0.5, brim * 0.55, -1, y + 1, y + 2, state);
        d.tube(x + 0.5, z + 0.5, brim * 0.35, -1, y + 3, y + 3, state);
    }

    private static void barn(Deco d, int cx, int z0) {
        int w = 11, depth = 16, wall = 9;
        for (int x = cx - w; x <= cx + w; x++) {
            for (int z = z0; z <= z0 + depth; z++) {
                d.set(x, 0, z, "spruce_planks");
                boolean edge = x == cx - w || x == cx + w || z == z0 || z == z0 + depth;
                if (!edge) {
                    continue;
                }
                for (int y = 1; y <= wall; y++) {
                    boolean corner = (x == cx - w || x == cx + w) && (z == z0 || z == z0 + depth);
                    d.set(x, y, z, corner ? "white_terracotta" : (y == wall ? "white_terracotta" : "red_terracotta"));
                }
            }
        }
        // gambrel roof: steep lower slope, shallow upper
        for (int x = cx - w - 1; x <= cx + w + 1; x++) {
            int dx = Math.abs(x - cx);
            int h = dx > 6 ? wall + 1 + (w + 1 - dx) * 2 : wall + 1 + (w + 1 - 6) * 2 + (6 - dx) / 2;
            for (int z = z0 - 1; z <= z0 + depth + 1; z++) {
                d.set(x, h, z, "dark_oak_planks|spruce_planks");
                if (dx > 6) {
                    d.set(x, h - 1, z, "dark_oak_planks|spruce_planks");
                }
                if (z == z0 || z == z0 + depth) {
                    for (int y = wall + 1; y < h; y++) {
                        d.set(x, y, z, "red_terracotta");
                    }
                }
            }
        }
        // big doors facing the field with a white X, and the hay loft above
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int y = 1; y <= 7; y++) {
                int u = x - cx, v = y - 4;
                d.set(x, y, z0, (Math.abs(u) == 4 || y == 7 || Math.abs(u) == Math.abs(v) * 4 / 3 || x == cx) ? "white_terracotta" : "red_terracotta");
            }
        }
        d.fill(cx - 2, 11, z0, cx + 2, 14, z0, "spruce_planks");
        d.fill(cx - 1, 11, z0, cx + 1, 13, z0 - 0, "hay_block[axis=y]");
        d.set(cx, 15, z0 - 1, "lantern[hanging=false]");
        d.set(cx - 5, 6, z0 - 1, "lantern[hanging=false]");
        d.set(cx + 5, 6, z0 - 1, "lantern[hanging=false]");
        d.set(cx - 5, 5, z0 - 1, "spruce_planks");
        d.set(cx + 5, 5, z0 - 1, "spruce_planks");
    }

    private static void windmill(Deco d, int x, int z) {
        for (int y = 1; y <= 16; y++) {
            double r = 3.6 - y * 0.12;
            d.tube(x + 0.5, z + 0.5, r, r - 1.2, y, y, y % 5 == 0 ? "spruce_planks" : "stone_bricks|cobblestone|stone_bricks");
        }
        d.cyl(x + 0.5, z + 0.5, 2.4, 17, 17, "dark_oak_planks");
        d.cyl(x + 0.5, z + 0.5, 1.4, 18, 19, "dark_oak_planks");
        d.set(x, 17, z - 3, "dark_oak_log[axis=z]");
        d.set(x, 17, z - 4, "dark_oak_log[axis=z]");
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + 0.4;
            for (int s = 1; s <= 9; s++) {
                int px = x + (int) Math.round(Math.cos(a) * s), py = 17 + (int) Math.round(Math.sin(a) * s);
                d.set(px, py, z - 5, "stripped_spruce_log[axis=z]");
                if (s > 2) {
                    int qx = x + (int) Math.round(Math.cos(a) * s - Math.sin(a) * 1.5), qy = 17 + (int) Math.round(Math.sin(a) * s + Math.cos(a) * 1.5);
                    d.set(qx, qy, z - 5, "white_wool");
                }
            }
        }
        d.set(x, 4, z - 4, "lantern[hanging=false]");
        d.set(x, 3, z - 4, "spruce_planks");
    }
}
