package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Sliding Picture: Candy Land. The puzzle (tiles at x 0..n-1 on z 5, the solved picture at x n+3.. on z 5, prismarine
 * backboard on z 6) is set into a giant gingerbread house's front; around it rise candy-cane pillars, lollipop trees,
 * gumdrop hills, a chocolate river with a wafer bridge, a frosted cake mountain and the Gamemaster as a confectioner
 * in a chef's hat. Players stand at z 1.5 facing +z.
 */
public final class CandyLook {
    private CandyLook() {
    }

    public static void build(Arena arena, int tier, int n) {
        Deco d = new Deco(arena, 27272L);
        int bx0 = -4, bx1 = n + 8;
        d.keepOut(bx0, 0, -2, bx1, n + 3, 6);
        // ---- ground: pink frosting with sprinkles ----
        for (int x = -60; x <= 60; x++) {
            for (int z = -50; z <= 60; z++) {
                if (Math.hypot(x - 4, z) > 60) {
                    continue;
                }
                int k = Math.floorMod(x * 7 + z * 13, 17);
                d.set(x, 0, z, k == 0 ? "lime_concrete" : k == 1 ? "light_blue_concrete" : k == 2 ? "yellow_concrete" : "pink_concrete");
                d.set(x, -1, z, "white_concrete");
            }
        }
        // chocolate river crossing in front, with a wafer bridge where the players stand
        for (int x = -60; x <= 60; x++) {
            int zc = -9 + (int) Math.round(3 * Math.sin(x * 0.12));
            for (int z = zc - 2; z <= zc + 2; z++) {
                if (x >= bx0 - 1 && x <= bx1 + 1) {
                    d.set(x, 0, z, Math.floorMod(x, 2) == 0 ? "orange_terracotta" : "yellow_terracotta");
                } else {
                    d.set(x, 0, z, "brown_concrete");
                    d.set(x, -1, z, "brown_terracotta");
                }
            }
        }
        // ---- the gingerbread house around the board ----
        int hx0 = bx0 - 3, hx1 = bx1 + 3, hz0 = 7, hz1 = 22, wh = n + 7;
        for (int y = 1; y <= wh; y++) {
            for (int x = hx0; x <= hx1; x++) {
                d.set(x, y, hz0, y % 4 == 0 ? "white_concrete" : "brown_terracotta");
                d.set(x, y, hz1, "brown_terracotta");
            }
            for (int z = hz0; z <= hz1; z++) {
                d.set(hx0, y, z, y % 4 == 0 ? "white_concrete" : "brown_terracotta");
                d.set(hx1, y, z, y % 4 == 0 ? "white_concrete" : "brown_terracotta");
            }
        }
        int span = (hx1 - hx0) / 2 + 2;
        for (int k = 0; k <= span; k++) {
            for (int z = hz0 - 2; z <= hz1 + 1; z++) {
                d.set(hx0 - 1 + k, wh + 1 + k, z, k % 2 == 0 ? "white_concrete" : "pink_concrete");
                d.set(hx1 + 1 - k, wh + 1 + k, z, k % 2 == 0 ? "white_concrete" : "pink_concrete");
            }
            for (int x = hx0 - 1 + k; x <= hx1 + 1 - k; x++) {
                d.set(x, wh + 1 + k, hz0 - 2, "brown_terracotta");
            }
        }
        // icing drips along the eaves and gumdrops on the roof
        for (int x = hx0 - 1; x <= hx1 + 1; x++) {
            d.set(x, wh, hz0 - 2, "white_concrete");
            if (Math.floorMod(x, 3) == 0) {
                d.set(x, wh - 1, hz0 - 2, "white_concrete");
            }
        }
        // a round candy window above the board
        int wx = (bx0 + bx1) / 2;
        for (int x = -3; x <= 3; x++) {
            for (int y = -3; y <= 3; y++) {
                double r = Math.hypot(x, y);
                if (r <= 3.3) {
                    d.set(wx + x, n + 9 + y, hz0, r > 2.4 ? "red_concrete" : (Math.abs(x) == Math.abs(y) || x == 0 || y == 0 ? "white_concrete" : "yellow_stained_glass"));
                    d.set(wx + x, n + 9 + y, hz0 + 1, "glowstone");
                }
            }
        }
        // ---- candy cane pillars flanking the board, lollipop trees, gumdrop hills ----
        for (int x : new int[] {bx0 - 2, bx1 + 2}) {
            cane(d, x, 1, 6, n + 8, x < 0 ? 1 : -1);
        }
        int[][] lolli = {{-16, -2, 12}, {-22, 12, 15}, {26, 2, 13}, {32, 16, 16}, {-30, -20, 11}, {36, -18, 14}};
        String[][] swirl = {{"red_concrete", "white_concrete"}, {"lime_concrete", "yellow_concrete"}, {"magenta_concrete", "white_concrete"},
            {"light_blue_concrete", "white_concrete"}, {"orange_concrete", "yellow_concrete"}, {"purple_concrete", "pink_concrete"}};
        for (int i = 0; i < lolli.length; i++) {
            lollipop(d, lolli[i][0], 1, lolli[i][1], lolli[i][2], swirl[i]);
        }
        String[] drops = {"red_stained_glass", "lime_stained_glass", "orange_stained_glass", "purple_stained_glass", "yellow_stained_glass"};
        for (int i = 0; i < 14; i++) {
            double a = d.rnd.nextDouble() * Math.PI * 2, r = 22 + d.rnd.nextDouble() * 30;
            int x = (int) (r * Math.cos(a)) + 4, z = (int) (r * Math.sin(a));
            d.ellipsoid(x + 0.5, 0, z + 0.5, 3 + i % 3, 3 + i % 4, 3 + i % 3, 0, drops[i % drops.length]);
        }
        // ---- the frosted cake mountain behind and the confectioner Gamemaster on its top tier ----
        int mz = 44;
        for (int t = 0; t < 4; t++) {
            int r = 22 - t * 5, y0 = t * 7;
            d.cyl(4.5, mz + 0.5, r, y0, y0 + 6, t % 2 == 0 ? "pink_concrete" : "white_concrete");
            d.tube(4.5, mz + 0.5, r + 0.5, r - 0.5, y0 + 6, y0 + 6, "white_concrete");
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                d.set((int) Math.floor(4.5 + (r - 1) * Math.cos(a)), y0 + 7, (int) Math.floor(mz + 0.5 + (r - 1) * Math.sin(a)),
                    i % 2 == 0 ? "red_concrete" : "candle[candles=4,lit=true]");
            }
        }
        Props.statue(d, 4, 27, mz, 0, -1, 20, "white_concrete", "gold_block");
        double k = 20 / 16.0;
        int hatY = 27 + 1 + (int) Math.round(15.9 * k);
        d.cyl(4.5, mz + 0.5, 2.2 * k, hatY, hatY + 2, "white_concrete");
        d.ellipsoid(4.5, hatY + 4, mz + 0.5, 3 * k, 2, 3 * k, 0, "white_concrete");
        d.commit();
        arena.setLater(wx, n + 4, 2, Deco.st("light[level=15]"));
        for (int x = -12; x <= 20; x += 8) {
            arena.setLater(x, 5, -6, Deco.st("light[level=15]"));
        }
    }

    static void cane(Deco d, int x, int y, int z, int h, int hookDir) {
        for (int i = 0; i < h; i++) {
            d.set(x, y + i, z, (i / 2) % 2 == 0 ? "red_concrete" : "white_concrete");
        }
        for (int i = 0; i < 4; i++) {
            d.set(x + hookDir * (i + 1) / 2, y + h + (i < 2 ? i : 3 - i), z, i % 2 == 0 ? "red_concrete" : "white_concrete");
        }
        d.set(x + hookDir * 2, y + h - 1, z, "red_concrete");
    }

    static void lollipop(Deco d, int x, int y, int z, int h, String[] c) {
        for (int i = 0; i < h; i++) {
            d.set(x, y + i, z, "white_concrete");
        }
        int r = 4;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                double rr = Math.hypot(dx, dy);
                if (rr <= r + 0.3) {
                    double a = Math.atan2(dy, dx) + rr * 0.9;
                    d.set(x + dx, y + h + r + dy, z, ((int) Math.floor((a + Math.PI) / (Math.PI / 3))) % 2 == 0 ? c[0] : c[1]);
                }
            }
        }
    }
}
