package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Archery: the Royal Tourney Field. The shooting line (x -4..4, z -2..2) faces the hay butt wall (x -10..10, y 0..12 at
 * z = distance) across a lawn kept wide open. The butt wall becomes the face of a striped target tent with pennants;
 * tiered spectator stands with striped awnings line both sides, a royal box with the Gamemaster's throne sits behind
 * the shooters, and beyond the butts rises a gold-roofed castle with towers and banners on a hill, a forest around
 * it, so the void is never seen.
 */
public final class TourneyLook {
    private TourneyLook() {
    }

    public static void build(Arena arena, int tier, int distance) {
        Deco d = new Deco(arena, 12121L + distance);
        int D = distance;
        // the shooting lane: everything between the line and the butts stays clear (and a margin round it)
        d.keepOut(-12, 0, -3, 12, 16, D);
        // ---- lawn. The shooting platform stays an island: open sky (void) round it and all along the lane, so
        // nobody can walk off it towards the butts (falling still resets them, as before). ----
        for (int x = -40; x <= 40; x++) {
            for (int z = -24; z <= D + 22; z++) {
                if (Math.abs(x) <= 12 && z >= -8 && z <= D - 1) {
                    continue;
                }
                String s = "grass_block";
                if ((Math.abs(x) == 14 || Math.abs(x) == 13) && z > -6 && z < D + 2) {
                    s = "gravel";
                }
                d.set(x, -1, z, s);
                d.set(x, -2, z, "dirt");
            }
        }
        // grass is under the room's level so the planks stay the floor; flowers in patches away from the lane
        for (int i = 0; i < 160; i++) {
            int x = -38 + d.rnd.nextInt(77), z = -22 + d.rnd.nextInt(D + 43);
            if (Math.abs(x) < 16) {
                continue;
            }
            d.set(x, 0, z, "short_grass|short_grass|dandelion|poppy|oxeye_daisy|cornflower");
        }
        // ---- the target tent: striped canvas frame round the butts with pennants ----
        for (int x = -14; x <= 14; x++) {
            for (int y = 0; y <= 16; y++) {
                boolean frame = Math.abs(x) >= 11 || y >= 13;
                if (!frame) {
                    continue;
                }
                d.set(x, y, D, ((x + 40) / 2) % 2 == 0 ? "red_wool" : "white_wool");
                d.set(x, y, D + 1, "red_wool");
            }
        }
        for (int x = -15; x <= 15; x++) {
            int y = 17 + (15 - Math.abs(x)) / 3;
            d.set(x, y, D, "gold_block");
            d.set(x, y, D + 1, "gold_block");
            for (int yy = 17; yy < y; yy++) {
                d.set(x, yy, D + 1, "red_wool");
            }
        }
        for (int x = -12; x <= 12; x += 4) {
            pennant(d, x, 13, D - 1, (x / 4) % 2 == 0 ? "yellow" : "purple");
        }
        for (int sx : new int[] {-15, 15}) {
            for (int y = 0; y <= 24; y++) {
                d.set(sx, y, D, y > 21 ? "gold_block" : "dark_oak_log");
            }
            d.set(sx, 25, D, "lantern");
            flag(d, sx, 21, D, sx < 0 ? -1 : 1, "purple_wool", "yellow_wool");
        }
        // ---- spectator stands on both sides ----
        for (int side : new int[] {-1, 1}) {
            int xin = side * 17;
            for (int z = -4; z <= D + 2; z++) {
                for (int t = 0; t < 5; t++) {
                    int x = xin + side * t * 2;
                    int y = t;
                    d.set(x, y, z, "spruce_planks");
                    d.set(x + side, y, z, "spruce_planks");
                    d.set(x, y + 1, z, side < 0 ? "spruce_stairs[facing=east]" : "spruce_stairs[facing=west]");
                    for (int yy = -1; yy < y; yy++) {
                        d.set(x, yy, z, "stripped_spruce_log");
                        d.set(x + side, yy, z, "stripped_spruce_log");
                    }
                }
                // back wall and striped awning
                int xb = xin + side * 10;
                for (int y = 0; y <= 10; y++) {
                    d.set(xb, y, z, "spruce_planks");
                }
                for (int t = 0; t <= 11; t++) {
                    int x = xb - side * t;
                    int y = 11 - t / 3;
                    d.set(x, y, z, ((z + 64) / 3) % 2 == 0 ? (side < 0 ? "blue_wool" : "red_wool") : "white_wool");
                }
                if (Math.floorMod(z, 6) == 0) {
                    for (int y = 0; y <= 9; y++) {
                        d.set(xin - side, y - 0, z, y >= 8 ? "gold_block" : "dark_oak_log");
                    }
                    d.set(xin - side, 10, z, "lantern");
                    pennant(d, xb - side * 6, 8, z, side < 0 ? "blue" : "red");
                }
            }
        }
        // ---- the royal box behind the shooters with his throne ----
        d.fill(-9, 0, -16, 9, 3, -9, "spruce_planks");
        d.fill(-9, 4, -16, 9, 4, -9, "red_carpet");
        d.walls(-9, 4, -9, 9, 5, -9, "dark_oak_fence");
        d.fill(-8, 4, -9, 8, 4, -9, "gold_block");
        for (int x = -9; x <= 9; x++) {
            for (int y = 4; y <= 16; y++) {
                if (y > 12 + (9 - Math.abs(x)) / 3) {
                    continue;
                }
                d.set(x, y, -17, Math.abs(x) == 9 || y >= 12 + (9 - Math.abs(x)) / 3 - 1 ? "gold_block" : (Math.floorMod(x, 3) == 0 ? "purple_wool" : "magenta_wool"));
            }
        }
        Props.statue(d, 0, 4, -14, 0, 1, 10);
        for (int x : new int[] {-9, 9}) {
            for (int y = 4; y <= 14; y++) {
                d.set(x, y, -9, "dark_oak_log");
            }
            d.set(x, 15, -9, "gold_block");
            d.set(x, 16, -9, "lantern");
        }
        for (int x = -10; x <= 10; x++) {
            int y = 15 + (10 - Math.abs(x)) / 4;
            for (int z = -17; z <= -8; z++) {
                d.set(x, y, z, (Math.floorMod(x, 4) < 2) ? "purple_wool" : "yellow_wool");
            }
        }
        // ---- the castle on a hill beyond the butts ----
        int cz = D + 40;
        d.ellipsoid(0.5, -6, cz + 0.5, 38, 18, 20, 0, "grass_block");
        d.ellipsoid(0.5, -7, cz + 0.5, 37, 17, 19, 0, "dirt");
        castle(d, 0, 9, cz);
        // forest on either side of the hill
        for (int i = 0; i < 26; i++) {
            int side = (i % 2 == 0) ? -1 : 1;
            int x = side * (28 + d.rnd.nextInt(30)), z = D - 4 + d.rnd.nextInt(Math.max(10, 50));
            tree(d, x, -1, z, 6 + d.rnd.nextInt(6));
        }
        for (int i = 0; i < 18; i++) {
            int x = -60 + d.rnd.nextInt(121), z = -40 - d.rnd.nextInt(30);
            tree(d, x, -1, z, 7 + d.rnd.nextInt(6));
        }
        // ---- light over the lawn so it is playable at night (lanterns on the stands do the rest) ----
        for (int x = -10; x <= 10; x += 10) {
            for (int z = 0; z <= D; z += 8) {
                arena.setLater(x, 15, z, Deco.st("light[level=15]"));
            }
        }
        for (int x = -8; x <= 8; x += 4) {
            arena.setLater(x, 5, D - 3, Deco.st("light[level=15]"));
        }
        d.commit();
    }

    /** A triangular pennant hanging down from y at (x, z): 3 wide at the top. */
    private static void pennant(Deco d, int x, int y, int z, String colour) {
        d.set(x - 1, y, z, colour + "_wool");
        d.set(x, y, z, colour + "_wool");
        d.set(x + 1, y, z, colour + "_wool");
        d.set(x, y - 1, z, colour + "_wool");
    }

    private static void flag(Deco d, int x, int y, int z, int dir, String a, String b) {
        for (int i = 1; i <= 5; i++) {
            for (int j = 0; j < 3; j++) {
                d.set(x + dir * i, y + j, z, (i + j) % 2 == 0 ? a : b);
            }
        }
    }

    private static void castle(Deco d, int cx, int y, int cz) {
        // curtain wall
        int hw = 22, hd = 10;
        for (int x = cx - hw; x <= cx + hw; x++) {
            for (int z = cz - hd; z <= cz + hd; z++) {
                boolean wall = x == cx - hw || x == cx + hw || z == cz - hd || z == cz + hd;
                if (!wall) {
                    continue;
                }
                for (int yy = y - 4; yy <= y + 9; yy++) {
                    d.set(x, yy, z, "stone_bricks|stone_bricks|mossy_stone_bricks|cracked_stone_bricks");
                }
                if (((x + z) & 1) == 0) {
                    d.set(x, y + 10, z, "stone_bricks");
                }
            }
        }
        // gatehouse facing the field
        d.fill(cx - 4, y - 4, cz - hd - 1, cx + 4, y + 13, cz - hd + 1, "stone_bricks");
        d.fill(cx - 2, y - 4, cz - hd - 1, cx + 2, y + 2, cz - hd + 1, "air");
        d.fill(cx - 2, y + 3, cz - hd - 2, cx + 2, y + 3, cz - hd - 2, "gold_block");
        d.fill(cx - 2, y - 4, cz - hd - 1, cx + 2, y + 2, cz - hd - 1, "iron_bars");
        for (int x = cx - 4; x <= cx + 4; x += 2) {
            d.set(x, y + 14, cz - hd - 1, "stone_bricks");
        }
        d.set(cx - 3, y + 6, cz - hd - 2, "lantern");
        d.set(cx + 3, y + 6, cz - hd - 2, "lantern");
        // towers at the corners with gold cones
        for (int tx : new int[] {cx - hw, cx + hw}) {
            for (int tz : new int[] {cz - hd, cz + hd}) {
                tower(d, tx, y - 4, tz, 20, 4.2);
            }
        }
        // the keep
        d.fill(cx - 7, y - 4, cz - 4, cx + 7, y + 20, cz + 5, "stone_bricks");
        for (int x = cx - 7; x <= cx + 7; x += 2) {
            d.set(x, y + 21, cz - 4, "stone_bricks");
        }
        for (int yy = y + 6; yy <= y + 16; yy += 5) {
            for (int x = cx - 5; x <= cx + 5; x += 3) {
                d.set(x, yy, cz - 5, "yellow_stained_glass");
                d.set(x, yy + 1, cz - 5, "yellow_stained_glass");
                d.set(x, yy, cz - 4, "glowstone");
                d.set(x, yy + 1, cz - 4, "glowstone");
            }
        }
        tower(d, cx, y + 21, cz, 10, 3.2);
        // banners down the keep
        for (int x : new int[] {cx - 6, cx + 6}) {
            for (int yy = y + 4; yy <= y + 18; yy++) {
                d.set(x, yy, cz - 5, yy % 3 == 0 ? "gold_block" : "purple_wool");
            }
        }
    }

    private static void tower(Deco d, int x, int y, int z, int h, double r) {
        d.cyl(x + 0.5, z + 0.5, r, y, y + h, "stone_bricks|stone_bricks|mossy_stone_bricks");
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            d.set(x + (int) Math.round(r * Math.cos(a)), y + h + 1, z + (int) Math.round(r * Math.sin(a)), "stone_bricks");
        }
        int cone = (int) Math.round(r * 2.4);
        for (int k = 0; k <= cone; k++) {
            double rr = (r + 0.8) * (1 - (double) k / (cone + 1));
            d.tube(x + 0.5, z + 0.5, rr, rr - 1.2, y + h + 1 + k, y + h + 1 + k, k % 3 == 0 ? "gold_block" : "purple_terracotta");
        }
        d.set(x, y + h + cone + 2, z, "gold_block");
        d.set(x, y + h + cone + 3, z, "lightning_rod");
        d.set(x + (int) Math.round(r) + 1, y + h - 3, z, "lantern");
    }

    static void tree(Deco d, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            d.set(x, y + i, z, "oak_log");
        }
        d.ellipsoid(x + 0.5, y + h, z + 0.5, 3.4, 2.8, 3.4, 0, "oak_leaves[persistent=true]|oak_leaves[persistent=true]|birch_leaves[persistent=true]");
    }
}
