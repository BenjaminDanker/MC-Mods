package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Sumo: Pirate Cove. The ring (radius up to 6 at y 0) sits on a sea stack in a lagoon between two galleons with full
 * sails and the Gamemaster's jolly roger; palm islands with treasure hoards, a skull rock and a towering pirate
 * Gamemaster on a rock close the view. Within 16 blocks of the ring nothing stands higher than y -5, so anyone
 * knocked off is out even if they land on a deck.
 */
public final class PirateLook {
    private PirateLook() {
    }

    public static void build(Arena arena, int tier, int radius) {
        Deco d = new Deco(arena, 18181L);
        d.keepOut(-16, -5, -16, 16, 12, 16);
        int sea = -14;
        // ---- the sea (no water: prismarine and blue glass for sparkle) ----
        for (int x = -80; x <= 80; x++) {
            for (int z = -80; z <= 80; z++) {
                if (Math.hypot(x, z) > 80) {
                    continue;
                }
                int k = Math.floorMod(x * 7 + z * 13 + (int) (3 * Math.sin(x * 0.3)), 17);
                d.set(x, sea, z, k == 0 ? "sea_lantern" : k < 4 ? "light_blue_stained_glass" : k < 9 ? "prismarine" : "dark_prismarine");
                d.set(x, sea - 1, z, "dark_prismarine");
            }
        }
        // ---- the sea stack under the ring ----
        // (its top stays 5 below the ring and narrower than it, so nobody knocked off lands on a ledge)
        for (int y = sea; y <= -6; y++) {
            double r = radius - 0.5 + (-6 - y) * 0.3;
            d.cyl(0.5, 0.5, r, y, y, "stone|andesite|mossy_cobblestone|stone");
        }
        // ---- two galleons ----
        galleon(d, -28, sea + 1, 2, 1);
        galleon(d, 28, sea + 1, -2, -1);
        // ---- palm islands with treasure ----
        palmIsland(d, -14, sea, 42);
        palmIsland(d, 22, sea, 38);
        palmIsland(d, 4, sea, -44);
        // ---- skull rock ----
        d.ellipsoid(-40, sea + 10, -36, 10, 11, 9, 0, "stone|andesite|stone");
        for (int s : new int[] {-1, 1}) {
            d.ellipsoid(-40 + s * 4, sea + 13, -28, 2.6, 2.6, 2.6, 0, "black_concrete");
        }
        d.ellipsoid(-40, sea + 8, -27.5, 1.5, 2, 1.5, 0, "black_concrete");
        // ---- the pirate Gamemaster on his rock (+z), with a gold hoard ----
        d.ellipsoid(0, sea + 4, 60, 12, 10, 10, 0, "stone|andesite|mossy_cobblestone");
        Props.statue(d, 0, sea + 13, 60, 0, -1, 32, "black_concrete", "gold_block");
        // tricorn hat brim on top of his circlet
        double k = 32 / 16.0;
        int hatY = sea + 13 + 1 + (int) Math.round(16.0 * k) - 1;
        d.cyl(0.5, 60.5, 3.2 * k, hatY, hatY, "black_concrete");
        d.cyl(0.5, 60.5, 2 * k, hatY + 1, hatY + 3, "black_concrete");
        d.tube(0.5, 60.5, 3.2 * k, 3.2 * k - 1, hatY, hatY, "gold_block");
        for (int i = 0; i < 40; i++) {
            int x = d.rnd.nextInt(17) - 8, z = 50 + d.rnd.nextInt(6);
            d.set(x, sea + 12 + d.rnd.nextInt(2), z, i % 5 == 0 ? "chest[facing=north]" : "gold_block");
        }
        Props.die(d, 40.5, sea + 6, 30.5, 6, 20, 30, 10, "white_concrete", "black_concrete");
        d.commit();
        for (int x = -6; x <= 6; x += 6) {
            arena.setLater(x, 5, 0, Deco.st("light[level=15]"));
        }
    }

    /** A galleon broadside to the ring at x, bow toward +z * dir. Deck at y + 6 (stays below y -5 near the ring). */
    static void galleon(Deco d, int x, int y, int z, int dir) {
        int deck = y + 6;
        for (int u = -18; u <= 18; u++) { // along z
            double t = Math.abs(u) / 18.0;
            int half = (int) Math.round(5 * Math.sqrt(1 - t * t));
            int bottom = y + (int) Math.round(3 * t * t);
            for (int v = -half; v <= half; v++) {
                for (int yy = bottom; yy <= deck; yy++) {
                    boolean shell = Math.abs(v) == half || yy == bottom || yy == deck;
                    if (!shell) {
                        continue;
                    }
                    String s = yy == deck ? "spruce_planks" : (yy == deck - 1 ? "gold_block" : (yy <= bottom + 1 ? "dark_oak_planks" : "dark_oak_planks"));
                    d.set(x + v, yy, z + u * dir, s);
                }
                if (Math.abs(v) == half) {
                    d.set(x + v, deck + 1, z + u * dir, "dark_oak_fence");
                }
            }
            // gunports
            if (Math.abs(u) < 14 && u % 3 == 0) {
                for (int s : new int[] {-1, 1}) {
                    int hx = x + s * (int) Math.round(5 * Math.sqrt(1 - t * t));
                    d.set(hx, deck - 2, z + u * dir, "black_concrete");
                }
            }
        }
        // stern castle
        d.fill(x - 4, deck, z - 18 * dir, x + 4, deck + 5, z - 13 * dir, "dark_oak_planks");
        d.fill(x - 4, deck + 6, z - 18 * dir, x + 4, deck + 6, z - 13 * dir, "spruce_slab[type=bottom]");
        d.fill(x - 3, deck + 3, z - 18 * dir, x + 3, deck + 3, z - 18 * dir, "yellow_stained_glass");
        d.set(x, deck + 4, z - 13 * dir, "lantern");
        // masts with sails
        for (int m = -1; m <= 1; m++) {
            int mz = z + m * 9 * dir;
            int h = m == 0 ? 30 : 24;
            for (int yy = deck; yy <= deck + h; yy++) {
                d.set(x, yy, mz, "stripped_dark_oak_log");
            }
            for (int s = 0; s < 3; s++) {
                int sy = deck + 6 + s * 7;
                int w = 7 - s * 2;
                d.fill(x - w - 1, sy + 5, mz, x + w + 1, sy + 5, mz, "stripped_spruce_log[axis=x]");
                for (int yy = sy; yy <= sy + 4; yy++) {
                    for (int v = -w; v <= w; v++) {
                        int bulge = (int) Math.round(1.2 * Math.sin(Math.PI * (yy - sy) / 4.0));
                        d.set(x + v, yy, mz + bulge * dir, m == 0 && s == 1 && Math.abs(v) <= 1 && yy == sy + 2 ? "gold_block" : "white_wool");
                    }
                }
            }
            d.set(x, deck + h + 1, mz, "lantern");
        }
        // the jolly roger on the main mast: black flag, white die skull
        for (int u = 1; u <= 6; u++) {
            for (int yy = 0; yy < 4; yy++) {
                d.set(x + u, deck + 26 + yy, z, (u == 3 || u == 4) && (yy == 1 || yy == 2) ? "white_concrete" : "black_wool");
            }
        }
        // bowsprit
        d.line(x + 0.5, deck + 1.5, z + 18 * dir + 0.5, x + 0.5, deck + 5.5, z + 26 * dir + 0.5, "stripped_dark_oak_log");
        d.set(x, deck + 1, z + 17 * dir, "lantern");
    }

    static void palmIsland(Deco d, int x, int sea, int z) {
        d.ellipsoid(x + 0.5, sea, z + 0.5, 9, 3, 7, 0, "sandstone|smooth_sandstone|sandstone");
        int top = sea + 2;
        for (int[] p : new int[][] {{-3, -1, 9}, {3, 2, 7}}) {
            int px = x + p[0], pz = z + p[1];
            for (int i = 0; i < p[2]; i++) {
                d.set(px + (i * 2 / p[2]), top + i, pz, "jungle_log");
            }
            int hx = px + 2, hy = top + p[2];
            for (int[] f : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {-1, -1}, {1, -1}, {-1, 1}}) {
                for (int s = 1; s <= 4; s++) {
                    d.set(hx + f[0] * s, hy - (s > 2 ? 1 : 0), pz + f[1] * s, "jungle_leaves[persistent=true]");
                }
            }
            d.set(hx, hy, pz, "jungle_leaves[persistent=true]");
        }
        d.set(x, top, z + 3, "chest[facing=south]");
        d.set(x + 1, top, z + 3, "gold_block");
        d.set(x - 1, top, z + 3, "gold_block");
        d.set(x, top + 1, z + 3, "lantern");
    }
}
