package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Lights Out: inside a Giant Geode. The board (lamps on z 5, deepslate backing on z 6, floor x -4..4, z -3..5) is set
 * into the crystal wall of a vast hollow geode: smooth basalt rind, calcite, then an amethyst lining bristling with
 * crystal clusters; tall crystal spires, a gilded amethyst statue of the Gamemaster and soft violet light. Kept dimmer
 * than the other rooms so the board's lamps stand out.
 */
public final class GeodeLook {
    private GeodeLook() {
    }

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 10101L);
        d.keepOut(-4, 0, -3, 4, 0, 5);
        d.keepOut(-4, 1, -3, 4, 7, 6);
        double cx = 0.5, cy = 7, cz = 3, rx = 21, ry = 15, rz = 23;
        // ---- the geode shell: rind, calcite, amethyst lining with buds and clusters pointing inwards ----
        for (int x = (int) (cx - rx - 2); x <= cx + rx + 2; x++) {
            for (int y = -5; y <= cy + ry + 2; y++) {
                for (int z = (int) (cz - rz - 2); z <= cz + rz + 2; z++) {
                    double nx = (x + 0.5 - cx) / rx, ny = (y + 0.5 - cy) / ry, nz = (z + 0.5 - cz) / rz;
                    double wob = 0.06 * Math.sin(x * 0.7 + z * 0.4) * Math.cos(y * 0.6 - z * 0.3);
                    double r = Math.sqrt(nx * nx + ny * ny + nz * nz) + wob;
                    double depth = (r - 1.0) * Math.min(rx, Math.min(ry, rz)); // > 0 outside the cavity
                    if (depth > 3.2 || depth < -0.6) {
                        continue;
                    }
                    if (y < 0 && depth < 0) {
                        continue; // the floor fills the bottom
                    }
                    String s;
                    if (depth > 2.0) {
                        s = "smooth_basalt";
                    } else if (depth > 0.8) {
                        s = "calcite";
                    } else if (depth > -0.6) {
                        s = "amethyst_block";
                    } else {
                        s = null;
                    }
                    if (s != null) {
                        d.set(x, y, z, s);
                    }
                }
            }
        }
        // ---- floor: calcite and amethyst mosaic, a gold ring round the board's floor ----
        for (int x = (int) (cx - rx); x <= cx + rx; x++) {
            for (int z = (int) (cz - rz); z <= cz + rz; z++) {
                double nx = (x + 0.5 - cx) / rx, nz = (z + 0.5 - cz) / rz, ny = (0.5 - cy) / ry;
                if (nx * nx + nz * nz + ny * ny > 1.02) {
                    continue;
                }
                boolean ring = (x == -5 || x == 5) && z >= -4 && z <= 6 || (z == -4) && x >= -5 && x <= 5;
                d.set(x, 0, z, ring ? "gold_block" : Math.floorMod(x + z, 5) == 0 ? "amethyst_block" : "calcite|calcite|polished_diorite");
                for (int y = -1; y >= -4; y--) {
                    d.set(x, y, z, "smooth_basalt");
                }
            }
        }
        // ---- crystal spires rising from the floor and hanging from the roof ----
        int[][] spires = {{-14, 4, 12}, {-11, 14, 9}, {13, 10, 13}, {15, -6, 8}, {-15, -8, 10}, {9, 18, 11}, {-6, 18, 7},
            {12, -14, 7}, {-10, -15, 8}};
        for (int[] s : spires) {
            spire(d, s[0], 0, s[1], s[2], 1, d.rnd.nextDouble() * 0.5 - 0.25, d.rnd.nextDouble() * 0.5 - 0.25);
        }
        int[][] hanging = {{-8, 2, 7}, {7, 6, 8}, {0, 14, 6}, {-3, -10, 7}, {6, -9, 6}};
        for (int[] s : hanging) {
            int top = (int) Math.round(cy + ry * Math.sqrt(Math.max(0, 1 - Math.pow((s[0] + 0.5 - cx) / rx, 2) - Math.pow((s[1] + 0.5 - cz) / rz, 2))));
            spire(d, s[0], top, s[1], s[2], -1, 0, 0);
        }
        // clusters scattered on the floor lining
        for (int i = 0; i < 70; i++) {
            int x = (int) (cx - rx + 2 + d.rnd.nextInt((int) (2 * rx - 4))), z = (int) (cz - rz + 2 + d.rnd.nextInt((int) (2 * rz - 4)));
            if (Math.abs(x) <= 6 && z >= -5 && z <= 8) {
                continue;
            }
            d.set(x, 1, z, d.rnd.nextInt(3) == 0 ? "amethyst_cluster[facing=up]" : "large_amethyst_bud[facing=up]");
        }
        // ---- the board's surround: a gilded crystal frame ----
        for (int y = 0; y <= 9; y++) {
            for (int x : new int[] {-5, 5}) {
                d.set(x, y, 6, y % 3 == 0 ? "gold_block" : "purpur_pillar");
                d.set(x, y, 5, y >= 8 ? "gold_block" : "amethyst_block");
            }
        }
        for (int x = -5; x <= 5; x++) {
            d.set(x, 8, 6, "gold_block");
            d.set(x, 9, 6, Math.abs(x) % 2 == 0 ? "amethyst_block" : "gold_block");
        }
        for (int x = -4; x <= 4; x += 2) {
            d.set(x, 10, 6, "amethyst_cluster[facing=up]");
        }
        for (int x = -4; x <= 4; x++) {
            d.set(x, 8, 5, "gold_block");
        }
        // ---- the amethyst Gamemaster rises behind the board ----
        Props.statue(d, 0, 3, 13, 0, -1, 14, "quartz_block", "gold_block");
        d.fill(-4, 0, 9, 4, 2, 17, "calcite");
        d.fill(-4, 3, 9, 4, 3, 17, "gold_block");
        for (int x = -5; x <= 5; x += 2) {
            d.set(x, 2, 8, "amethyst_cluster[facing=north]");
        }
        // ---- soft light: dim lanterns, end rods in clusters, a few hidden light blocks ----
        for (int[] p : new int[][] {{-9, -2}, {9, -2}, {-9, 8}, {9, 8}, {-12, 3}, {12, 3}}) {
            Props.post(d, p[0], 1, p[1], 2, "amethyst_block", "soul_lantern");
        }
        for (int x = -15; x <= 15; x += 6) {
            for (int z = -17; z <= 22; z += 6) {
                if (Math.abs(x) <= 6 && z >= -6 && z <= 8) {
                    d.set(x, 12, z, "light[level=11]"); // softer over the board
                } else {
                    d.set(x, 9, z, "light[level=15]");
                }
            }
        }
        for (int[] p : new int[][] {{-4, 16, 12}, {4, 16, 12}, {0, 24, 9}}) {
            d.set(p[0], p[1], p[2], "light[level=15]"); // on the statue
        }
        Props.die(d, -12.5, 2.5, -11.5, 5, 35, 10, 0, "purple_concrete", "white_concrete");
        d.commit();
    }

    /** A hexagon-ish crystal spire of amethyst growing up (dir 1) or down (dir -1), tilted by (tx, tz) per block. */
    static void spire(Deco d, int x, int y, int z, int h, int dir, double tx, double tz) {
        for (int i = 0; i < h; i++) {
            double r = Math.max(0.5, 1.8 * (1 - (double) i / h));
            double px = x + 0.5 + tx * i, pz = z + 0.5 + tz * i;
            int yy = y + dir * i;
            for (int ix = (int) Math.floor(px - r); ix <= px + r; ix++) {
                for (int iz = (int) Math.floor(pz - r); iz <= pz + r; iz++) {
                    if (Math.hypot(ix + 0.5 - px, iz + 0.5 - pz) <= r) {
                        d.set(ix, yy, iz, i % 5 == 3 ? "purple_stained_glass" : "amethyst_block");
                    }
                }
            }
        }
        int tipY = y + dir * h;
        d.set((int) Math.floor(x + 0.5 + tx * h), tipY, (int) Math.floor(z + 0.5 + tz * h), "amethyst_cluster[facing=" + (dir > 0 ? "up" : "down") + "]");
        d.set(x, y + dir, z, "pearlescent_froglight"); // glow at the root
    }
}
