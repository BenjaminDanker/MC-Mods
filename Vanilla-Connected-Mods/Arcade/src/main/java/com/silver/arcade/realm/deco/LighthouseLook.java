package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Dropper: the Lighthouse above the Clouds. The shaft (walls at x/z +-5, y 0..h) becomes the inside of a candy-striped
 * lighthouse rising from a rocky isle in a sea of cloud far below; a gallery ring and a golden lantern room on four
 * pillars crown it high above the open shaft top (the drop stays open), floating islands and a colossal Gamemaster
 * stand around in the cloud sea. Nothing is placed within three blocks below the top ledge outside the shaft, so
 * nobody can land on decoration near the start.
 */
public final class LighthouseLook {
    private LighthouseLook() {
    }

    public static void build(Arena arena, int tier, int half, int height) {
        Deco d = new Deco(arena, 13131L);
        int H = height;
        d.keepOut(-half - 1, -1, -half - 1, half + 1, H + 40, half + 1);      // the shaft and everything above its opening
        d.keepOut(-half - 3, H + 1, -half - 3, half + 3, H + 4, -half - 2);   // the start ledge and its headroom
        // ---- outer cladding: an octagonal striped tower round the shaft, down from just under the ledge ----
        double R = half + 4.6;
        int cladTop = H;
        for (int y = -18; y <= cladTop; y++) {
            boolean red = Math.floorMod(y, 12) < 6;
            String s = y % 12 == 0 || y >= H - 1 ? "gold_block" : red ? "red_concrete" : "white_concrete";
            d.tube(0.5, 0.5, R, R - 1.4, y, y, s);
            if (y >= H - 1) {
                d.tube(0.5, 0.5, R - 1.3, half + 1.6, y, y, "gold_block"); // close the rim to the shaft
            }
            if (y % 8 == 4) { // windows with warm light
                for (int[] w : new int[][] {{0, (int) R}, {0, -(int) R}, {(int) R, 0}, {-(int) R, 0}}) {
                    d.set(w[0], y, w[1], "glowstone");
                    d.set(w[0], y + 1, w[1], "yellow_stained_glass");
                }
            }
        }
        // a flared base on the rock
        for (int y = -26; y <= -18; y++) {
            double r = R + (-18 - y) * 0.45;
            d.tube(0.5, 0.5, r, r - 1.6, y, y, "stone_bricks|stone_bricks|mossy_stone_bricks");
        }
        d.ellipsoid(0.5, -30, 0.5, R + 9, 7, R + 9, 0, "stone|andesite|stone|cobblestone|mossy_cobblestone");
        // ---- gallery ring just under the cladding top with railing and lanterns (well below the ledge) ----
        int gy = cladTop - 6;
        d.tube(0.5, 0.5, R + 2.5, R - 0.5, gy, gy, "dark_oak_slab[type=top]");
        d.tube(0.5, 0.5, R + 2.5, R + 1.5, gy + 1, gy + 1, "dark_oak_fence");
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i + Math.PI / 8;
            d.set((int) Math.floor(0.5 + (R + 2) * Math.cos(a)), gy + 2, (int) Math.floor(0.5 + (R + 2) * Math.sin(a)), "lantern");
            d.set((int) Math.floor(0.5 + (R + 2) * Math.cos(a)), gy + 1, (int) Math.floor(0.5 + (R + 2) * Math.sin(a)), "gold_block");
        }
        // ---- the golden lantern room on four pillars high above the shaft top ----
        int ly = H + 14;
        for (int[] c : new int[][] {{-half - 2, -half - 2}, {half + 2, -half - 2}, {-half - 2, half + 2}, {half + 2, half + 2}}) {
            for (int y = cladTop + 1; y <= ly; y++) {
                if (c[1] < 0 && y >= H + 1 && y <= H + 4) {
                    continue; // the ledge corners
                }
                d.set(c[0], y, c[1], y % 4 == 0 ? "gold_block" : "quartz_pillar");
            }
        }
        // roof ring and dome
        d.tube(0.5, 0.5, R + 1, R - 1, ly, ly, "gold_block");
        for (int k = 0; k <= 8; k++) {
            double r = (R + 1) * Math.cos(Math.toRadians(k * 10.5));
            d.tube(0.5, 0.5, r, r - 1.4, ly + 1 + k, ly + 1 + k, k % 3 == 2 ? "gold_block" : "red_concrete");
        }
        // the great lamp under the dome (hangs over the open shaft but far above anyone)
        d.cyl(0.5, 0.5, 2.5, ly - 1, ly, "sea_lantern");
        d.set(0, ly + 10, 0, "gold_block");
        d.set(0, ly + 11, 0, "lightning_rod");
        for (int y = ly - 4; y < ly - 1; y++) {
            for (int[] c : new int[][] {{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
                d.set(c[0], y, c[1], "iron_chain[axis=y]");
            }
        }
        // ---- the ledge's frame: a gilded railing behind the start and an arch over it ----
        for (int x = -half - 3; x <= half + 3; x++) {
            d.set(x, H + 1, -half - 4, "gold_block");
            d.set(x, H + 2, -half - 4, "quartz_bricks");
        }
        for (int z = -half - 4; z <= -half - 2; z++) {
            d.set(-half - 4, H + 2, z, "quartz_bricks");
            d.set(half + 4, H + 2, z, "quartz_bricks");
            d.set(-half - 4, H + 1, z, "gold_block");
            d.set(half + 4, H + 1, z, "gold_block");
        }
        for (int x : new int[] {-half - 3, half + 3}) {
            d.set(x, H + 3, -half - 4, "lantern");
        }
        // ---- the sea of clouds far below, islands rising from it, and the colossus ----
        int cy = -36;
        java.util.Random r = d.rnd;
        for (int i = 0; i < 70; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 14 + r.nextDouble() * 70;
            double x = dist * Math.cos(a), z = dist * Math.sin(a);
            d.ellipsoid(x, cy + r.nextInt(4), z, 5 + r.nextInt(7), 2 + r.nextInt(2), 4 + r.nextInt(6), 0, "white_wool|white_wool|white_concrete|light_gray_wool");
        }
        int[][] isles = {{-40, -10, 30}, {38, -4, 22}, {-26, 6, -42}, {30, 14, -38}, {6, -14, 52}, {-58, 20, -6}};
        for (int[] is : isles) {
            island(d, is[0], cy + 6 + is[1], is[2], 6 + r.nextInt(5));
        }
        // the Gamemaster, standing in the clouds and looking up at the tower top
        Props.statue(d, -55, cy, 40, 1, -1, 64);
        Props.die(d, 46.5, cy + 30, 46.5, 9, 30, 25, 15, "white_concrete", "black_concrete");
        Props.die(d, -40.5, cy + 46, -45.5, 7, -20, 10, 40, "white_concrete", "red_concrete");
        d.commit();
    }

    static void island(Deco d, int x, int y, int z, int r) {
        for (int k = 0; k < r + 3; k++) {
            double rr = r * (1 - (double) k / (r + 3));
            d.cyl(x + 0.5, z + 0.5, rr, y - k, y - k, k == 0 ? "grass_block" : k < 3 ? "dirt" : "stone|andesite|stone");
        }
        // a tree and a lantern
        for (int i = 1; i <= 5; i++) {
            d.set(x, y + i, z, "oak_log");
        }
        d.ellipsoid(x + 0.5, y + 6, z + 0.5, 2.8, 2.2, 2.8, 0, "oak_leaves[persistent=true]|flowering_azalea_leaves[persistent=true]");
        d.set(x + 2, y + 1, z, "lantern");
    }
}
