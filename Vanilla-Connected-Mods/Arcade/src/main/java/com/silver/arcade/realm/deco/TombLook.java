package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Memory Pairs: the Pharaoh's Treasure Tomb. The card wall (cards at x 0..columns-1 on z 5, deepslate backing on z 6)
 * is set into a painted sandstone tomb wall between two jackal-headed guardians; the hall has hieroglyph bands, a
 * golden sarcophagus, heaps of treasure and gems, braziers, a starry blue ceiling and the Gamemaster as pharaoh in a
 * striped headdress. Players stand at z 1.5 facing +z.
 */
public final class TombLook {
    private TombLook() {
    }

    public static void build(Arena arena, int tier, int columns, int rows) {
        Deco d = new Deco(arena, 28282L);
        d.keepOut(-3, 0, -3, columns + 2, rows + 3, 6);
        int x0 = -14, x1 = columns + 14, z0 = -12, z1 = 22, h = Math.max(15, rows + 10);
        // floor, walls with hieroglyph bands, starry ceiling
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                d.set(x, 0, z, Math.floorMod(x + z, 2) == 0 ? "smooth_sandstone" : "cut_sandstone");
                d.set(x, h, z, Math.floorMod(x * 7 + z * 11, 13) == 0 ? "glowstone" : "blue_terracotta");
            }
        }
        String[] glyph = {"chiseled_sandstone", "orange_terracotta", "blue_terracotta", "chiseled_red_sandstone", "black_terracotta"};
        for (int y = 1; y < h; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z : new int[] {z0, z1}) {
                    d.set(x, y, z, band(y, x, glyph));
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int x : new int[] {x0, x1}) {
                    d.set(x, y, z, band(y, z, glyph));
                }
            }
        }
        // the card wall's surround: a gilded portal with a winged sun disc
        for (int y = 0; y <= rows + 4; y++) {
            d.set(-2, y, 6, y % 3 == 0 ? "gold_block" : "cut_sandstone");
            d.set(columns + 1, y, 6, y % 3 == 0 ? "gold_block" : "cut_sandstone");
        }
        int mid = columns / 2;
        d.fill(-2, rows + 4, 6, columns + 1, rows + 4, 6, "gold_block");
        for (int x = -8; x <= 8; x++) {
            int y = rows + 6 + (Math.abs(x) <= 1 ? 1 : 0) - Math.abs(x) / 4;
            d.set(mid + x, y, 6, Math.abs(x) <= 1 ? "orange_concrete" : "gold_block");
            if (Math.abs(x) <= 1) {
                d.set(mid + x, y + 1, 6, "gold_block");
                d.set(mid + x, y - 1, 6, "gold_block");
            }
        }
        d.fill(-3, 1, 7, columns + 2, h - 1, 7, "smooth_sandstone");
        // two jackal-headed guardians flanking the wall
        for (int x : new int[] {-6, columns + 5}) {
            jackal(d, x, 1, 5);
        }
        // the golden sarcophagus on a dais (left), treasure heaps (right)
        d.fill(x0 + 3, 1, 8, x0 + 9, 1, 16, "cut_sandstone");
        d.fill(x0 + 4, 2, 9, x0 + 8, 3, 15, "gold_block");
        d.fill(x0 + 5, 4, 10, x0 + 7, 4, 14, "gold_block");
        d.set(x0 + 6, 4, 9, "orange_concrete");
        d.fill(x0 + 5, 3, 9, x0 + 7, 3, 9, "blue_terracotta");
        String[] gems = {"emerald_block", "diamond_block", "gold_block", "redstone_block", "lapis_block", "amethyst_block"};
        for (int i = 0; i < 70; i++) {
            int x = x1 - 9 + d.rnd.nextInt(7), z = 9 + d.rnd.nextInt(10);
            int hh = 1 + Math.max(0, 3 - (Math.abs(x - (x1 - 6)) + Math.abs(z - 14)) / 2);
            for (int y = 1; y <= hh; y++) {
                d.set(x, y, z, y == hh ? gems[d.rnd.nextInt(gems.length)] : "gold_block");
            }
        }
        d.set(x1 - 6, 6, 14, "chest[facing=north]");
        // braziers along the walk
        for (int z = -9; z <= 3; z += 6) {
            for (int x : new int[] {-6, columns + 5}) {
                d.set(x, 1, z, "cut_sandstone");
                d.set(x, 2, z, "gold_block");
                d.set(x, 3, z, "campfire[lit=true]");
            }
        }
        // the pharaoh Gamemaster at the back of the hall, behind the player (facing the wall)
        Props.statue(d, mid, 0, z0 + 4, 0, 1, Math.min(h - 3, 14), "white_concrete", "gold_block");
        double k = Math.min(h - 3, 14) / 16.0;
        int hy = (int) Math.round(1 + 12 * k);
        for (int y = hy; y <= hy + (int) Math.round(4 * k); y++) {
            for (int s : new int[] {-1, 1}) {
                for (int t = 2; t <= 3; t++) {
                    d.set(mid + s * (int) Math.round(t * k), y, z0 + 4, (y % 2 == 0) ? "blue_concrete" : "gold_block");
                }
            }
        }
        d.commit();
        for (int x = -1; x <= columns; x += 4) {
            arena.setLater(x, 3, 3, Deco.st("light[level=15]"));
            arena.setLater(x, rows + 2, 3, Deco.st("light[level=15]"));
        }
    }

    private static String band(int y, int along, String[] glyph) {
        if (y % 5 == 0) {
            return "gold_block";
        }
        if (y % 5 == 2 || y % 5 == 3) {
            return glyph[Math.floorMod(along * 3 + y, glyph.length)];
        }
        return "smooth_sandstone";
    }

    /** A jackal-headed guardian (about 12 tall), facing -z. */
    static void jackal(Deco d, int x, int y, int z) {
        d.fill(x - 1, y, z - 1, x + 1, y + 6, z + 1, "black_concrete");
        d.fill(x - 1, y, z - 1, x + 1, y + 1, z + 1, "gold_block");
        d.fill(x - 1, y + 4, z - 2, x + 1, y + 4, z - 2, "gold_block");
        d.fill(x - 1, y + 7, z - 1, x + 1, y + 9, z + 1, "black_concrete");
        d.fill(x, y + 7, z - 3, x, y + 8, z - 2, "black_concrete"); // snout
        d.set(x - 1, y + 10, z, "black_concrete");
        d.set(x + 1, y + 10, z, "black_concrete");
        d.set(x - 1, y + 11, z, "black_concrete");
        d.set(x + 1, y + 11, z, "black_concrete");
        d.set(x - 1, y + 8, z - 2, "gold_block");
        d.set(x + 1, y + 8, z - 2, "gold_block");
        for (int yy = y; yy <= y + 12; yy++) {
            d.set(x + 2, yy, z - 1, "gold_block"); // staff
        }
        d.set(x + 2, y + 13, z - 1, "lantern");
    }
}
