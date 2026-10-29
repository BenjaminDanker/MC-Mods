package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Hole in the Wall: the Gamemaster's TV game show. The stage (x -4..4, z -1..2) and the runway the walls slide down
 * (x -5..5, z 3..17, walls y 1..3 from z 16 down to z -2) stay clear, with six blocks of open air round them (no
 * jumping off the runway onto safe ground the walls never reach) and no floor behind the stage. Round it: a studio with
 * bleachers of cheering crowd on both sides, lighting trusses, cameras on tripods, a neon arch the walls come out of,
 * a scoreboard, a foam pit far below the stage, and the Gamemaster as the show's host in a magenta suit.
 */
public final class StudioLook {
    private StudioLook() {
    }

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 35353L);
        d.keepOut(-7, -3, -4, 7, 9, 19);
        int x0 = -34, x1 = 34, z0 = -22, z1 = 36, h = 24;
        // ---- studio floor (not near the stage), black walls and ceiling ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                // no floor within six blocks of the stage and runway: nobody can hop off the runway onto safe ground
                if (Math.abs(x) >= 12 || z >= 24 || z <= -17) {
                    d.set(x, 0, z, Math.floorMod(x / 3 + z / 3, 2) == 0 ? "black_concrete" : "gray_concrete");
                    d.set(x, -1, z, "black_concrete");
                }
                d.set(x, h, z, "black_concrete");
            }
        }
        for (int y = 1; y < h; y++) {
            d.walls(x0, y, z0, x1, y, z1, y % 8 == 0 ? "magenta_concrete" : "black_concrete");
        }
        // ---- the foam pit behind and below the stage ----
        for (int x = -8; x <= 8; x++) {
            for (int z = -16; z <= -5; z++) {
                for (int y = -10; y <= 0; y++) {
                    d.erase(x, y, z);
                }
                d.set(x, -11, z, "white_concrete");
                d.set(x, -10, z, ((x + z) & 1) == 0 ? "yellow_wool|light_blue_wool|lime_wool" : "pink_wool|orange_wool|magenta_wool");
                if (Math.abs(x) == 8 || z == -16 || z == -5) {
                    for (int y = -9; y <= -1; y++) {
                        d.set(x, y, z, "white_concrete");
                    }
                }
            }
        }
        // ---- a dark floor far below the gap round the runway (a pit, not a hole into the sky) ----
        for (int x = -11; x <= 11; x++) {
            for (int z = -4; z <= 23; z++) {
                d.set(x, -24, z, Math.floorMod(x * 5 + z * 3, 17) == 0 ? "sea_lantern" : "black_concrete");
            }
        }
        // ---- bleachers with the crowd on both sides ----
        String[] shirts = {"red_wool", "yellow_wool", "blue_wool", "lime_wool", "orange_wool", "light_blue_wool", "magenta_wool", "white_wool"};
        String[] heads = {"smooth_sandstone", "brown_terracotta", "white_terracotta", "orange_terracotta"};
        for (int s : new int[] {-1, 1}) {
            for (int row = 0; row < 7; row++) {
                int x = s * (12 + row * 2);
                for (int dx = 0; dx < 2; dx++) {
                    for (int z = -6; z <= 22; z++) {
                        for (int y = 1; y <= row + 1; y++) {
                            d.set(x + s * dx, y, z, "polished_blackstone_bricks");
                        }
                        d.set(x + s * dx, row + 1, z, dx == 0 ? "purple_concrete" : "polished_blackstone_bricks");
                    }
                }
                for (int z = -5; z <= 21; z += 2) {
                    int k = Math.floorMod(z * 7 + row * 3 + s, shirts.length);
                    if (Math.floorMod(z + row, 5) == 0) {
                        continue;
                    }
                    d.set(x + s, row + 2, z, shirts[k]);
                    d.set(x + s, row + 3, z, heads[Math.floorMod(z + row, heads.length)]);
                }
            }
        }
        // ---- the neon arch the walls come out of ----
        for (int x = -9; x <= 9; x++) {
            for (int y = 0; y <= 12; y++) {
                boolean post = Math.abs(x) >= 7 && y <= 9;
                boolean lintel = y >= 10;
                if (!(post || lintel)) {
                    continue;
                }
                String s = (Math.abs(x) == 7 || y == 10) ? "pearlescent_froglight" : (y == 12 || Math.abs(x) == 9 ? "gold_block" : "magenta_concrete");
                d.set(x, y, 20, s);
                d.set(x, y, 21, "black_concrete");
            }
        }
        // scoreboard above the arch: lit squares
        for (int x = -8; x <= 8; x++) {
            for (int y = 14; y <= 19; y++) {
                boolean border = Math.abs(x) == 8 || y == 14 || y == 19;
                d.set(x, y, 22, border ? "gold_block" : ((Math.floorMod(x * 3 + y * 5, 7) < 2) ? "ochre_froglight" : "black_concrete"));
            }
        }
        // ---- lighting trusses with spotlights ----
        for (int z = -8; z <= 28; z += 9) {
            for (int x = x0 + 1; x < x1; x++) {
                d.set(x, h - 3, z, "iron_block");
            }
            for (int x = -24; x <= 24; x += 6) {
                d.set(x, h - 4, z, Math.floorMod(x / 6 + z, 3) == 0 ? "sea_lantern" : "pearlescent_froglight");
            }
        }
        // ---- cameras on tripods ----
        int[][] cams = {{-14, -8}, {14, -8}, {-13, 27}};
        for (int[] c : cams) {
            d.set(c[0], 1, c[1], "iron_chain[axis=y]");
            d.set(c[0], 2, c[1], "iron_chain[axis=y]");
            d.set(c[0], 3, c[1], "iron_chain[axis=y]");
            d.fill(c[0], 4, c[1], c[0], 5, c[1] + 1, "black_concrete");
            d.set(c[0], 4, c[1] + (c[1] < 0 ? 2 : -1), "light_blue_stained_glass");
            d.set(c[0], 6, c[1], "red_concrete");
        }
        // ---- the host on his podium at the arch, facing the contestants ----
        int gx = 12, gz = 26;
        d.fill(gx - 2, 1, gz - 2, gx + 2, 2, gz + 2, "gold_block");
        Props.statue(d, gx, 2, gz, -1, -1, 16, "magenta_concrete", "gold_block");
        d.fill(gx - 1, 3, gz - 3, gx + 1, 3, gz - 3, "gold_block");
        // microphone stand in front of the stage
        for (int y = 1; y <= 4; y++) {
            d.set(-12, y, 2, "iron_chain[axis=y]");
        }
        d.set(-12, 5, 2, "gray_concrete");
        // ---- light ----
        for (int x = x0 + 4; x <= x1 - 4; x += 6) {
            for (int z = z0 + 4; z <= z1 - 4; z += 6) {
                d.set(x, 8, z, "light[level=15]");
            }
        }
        d.commit();
        // stage lights over the stage and the runway (above the walls)
        for (int x = -4; x <= 4; x += 3) {
            for (int z = -1; z <= 17; z += 3) {
                arena.setLater(x, 5, z, Deco.st("light[level=15]"));
            }
        }
    }
}
