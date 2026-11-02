package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Spot the Difference: the Detective's Office, a rainy night in the city. The andesite floor (z -4..4), both pictures
 * (z 6, y 2..size+1) with their plank backing (z 7) and the cells in front of them (z 5, where found differences light
 * up) stay as they are. Around them: dark oak frames, a panelled office with green wallpaper, a desk with lamp and
 * typewriter, filing cabinets, a corkboard of clues tied with red string, a window with blinds onto a rainy skyline,
 * a giant magnifying glass, and the Gamemaster as the detective in coat and hat. Players stand at z -1.5 facing +z.
 */
public final class DetectiveLook {
    private DetectiveLook() {
    }

    public static void build(Arena arena, int tier, int size) {
        Deco d = new Deco(arena, 32323L);
        d.keepOut(-size - 3, 0, -4, size + 4, size + 4, 4);
        d.keepOut(-size - 1, 2, 5, -2, size + 1, 6);
        d.keepOut(2, 2, 5, size + 1, size + 1, 6);
        int x0 = -size - 13, x1 = size + 14, z0 = -14, z1 = 15, h = size + 11;
        // ---- floor (herringbone oak, a red rug), panelled walls with wallpaper, ceiling ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                d.set(x, 0, z, Math.floorMod(x + z, 4) < 2 ? "dark_oak_planks" : "spruce_planks");
                d.set(x, -1, z, "spruce_planks");
                d.set(x, h, z, Math.floorMod(x, 5) == 0 || Math.floorMod(z, 5) == 0 ? "dark_oak_planks" : "smooth_quartz");
            }
        }
        for (int x = -size - 8; x <= size + 9; x++) {
            for (int z = -11; z <= -6; z++) {
                d.set(x, 1, z, (x == -size - 8 || x == size + 9 || z == -11 || z == -6) ? "yellow_carpet" : "red_carpet");
            }
        }
        for (int y = 1; y < h; y++) {
            String s = y <= 4 ? (y == 4 ? "stripped_dark_oak_log[axis=x]" : "dark_oak_planks")
                : (y == h - 1 ? "dark_oak_planks" : (Math.floorMod(y, 2) == 0 ? "green_terracotta" : "green_terracotta|lime_terracotta|green_terracotta"));
            d.walls(x0, y, z0, x1, y, z1, s);
        }
        // the wall behind the pictures (z 8) carries a clock
        for (int x = x0 + 1; x < x1; x++) {
            for (int y = 1; y < h; y++) {
                d.set(x, y, 8, y <= 4 ? "dark_oak_planks" : "green_terracotta");
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dy = -3; dy <= 3; dy++) {
                double r = Math.hypot(dx, dy);
                if (r <= 3.3) {
                    d.set(dx + 1, size + 7 + dy, 7, r > 2.4 ? "gold_block" : ((dx == 0 && dy >= 0) || (dy == 0 && dx >= 0 && dx <= 1) ? "black_concrete" : "white_concrete"));
                }
            }
        }
        // ---- frames round both pictures (z 6) ----
        frame(d, -size - 2, -1, size);
        frame(d, 1, size + 2, size);
        // ---- desk with lamp and typewriter, left of the players ----
        int dx0 = size + 6, dz = -3;
        d.fill(dx0, 1, dz - 2, dx0, 2, dz - 2, "dark_oak_log[axis=y]");
        d.fill(dx0 + 4, 1, dz - 2, dx0 + 4, 2, dz - 2, "dark_oak_log[axis=y]");
        d.fill(dx0, 1, dz + 2, dx0, 2, dz + 2, "dark_oak_log[axis=y]");
        d.fill(dx0 + 4, 1, dz + 2, dx0 + 4, 2, dz + 2, "dark_oak_log[axis=y]");
        d.fill(dx0, 3, dz - 2, dx0 + 4, 3, dz + 2, "dark_oak_slab[type=bottom]");
        d.set(dx0 + 1, 4, dz - 1, "black_concrete");
        d.set(dx0 + 1, 4, dz, "black_concrete");
        d.set(dx0 + 3, 4, dz + 1, "lantern[hanging=false]");
        d.set(dx0 + 2, 4, dz + 1, "white_carpet");
        d.set(dx0 + 3, 4, dz - 1, "white_carpet");
        d.fill(dx0 + 6, 1, dz - 1, dx0 + 6, 2, dz + 1, "dark_oak_stairs[facing=east,half=bottom,shape=straight]");
        // ---- filing cabinets along the right wall ----
        for (int z = z0 + 2; z <= z0 + 10; z++) {
            d.fill(x0 + 1, 1, z, x0 + 1, 4, z, Math.floorMod(z, 2) == 0 ? "iron_block" : "light_gray_concrete");
        }
        // ---- corkboard of clues with red string (left wall) ----
        int cz0 = -8, cz1 = 4;
        for (int z = cz0; z <= cz1; z++) {
            for (int y = 5; y <= 11; y++) {
                d.set(x1 - 1, y, z, (z == cz0 || z == cz1 || y == 5 || y == 11) ? "dark_oak_planks" : "brown_terracotta");
            }
        }
        int[][] notes = {{-6, 9}, {-3, 7}, {0, 10}, {2, 7}, {-1, 6}};
        for (int[] n : notes) {
            d.set(x1 - 2, n[1], n[0], "white_concrete");
        }
        for (int i = 0; i + 1 < notes.length; i++) {
            d.line(x1 - 1.5, notes[i][1] + 0.5, notes[i][0] + 0.5, x1 - 1.5, notes[i + 1][1] + 0.5, notes[i + 1][0] + 0.5, "red_wool");
        }
        for (int[] n : notes) {
            d.set(x1 - 2, n[1], n[0], "white_concrete|yellow_concrete");
        }
        // ---- window with blinds onto a rainy skyline (back right) ----
        for (int z = -4; z <= 4; z++) {
            for (int y = 5; y <= 12; y++) {
                boolean frame = z == -4 || z == 4 || y == 5 || y == 12;
                d.set(x0, y, z, frame ? "dark_oak_planks" : (y % 2 == 0 ? "glass" : "gray_stained_glass"));
            }
        }
        for (int i = 0; i < 9; i++) {
            int bx = x0 - 12 - (i % 3) * 7, bz = -12 + i * 4, bh = 14 + (i * 7) % 13;
            d.fill(bx - 2, -6, bz - 1, bx + 2, bh, bz + 1, "gray_concrete|black_concrete|gray_concrete");
            for (int y = -4; y <= bh - 2; y += 3) {
                if ((y + i) % 2 == 0) {
                    d.set(bx + 3, y, bz, "ochre_froglight");
                }
            }
        }
        // ---- the giant magnifying glass leaning beside the pictures ----
        int mx = size + 9, my = 9, mz = 6;
        for (int u = -5; u <= 5; u++) {
            for (int v = -5; v <= 5; v++) {
                double r = Math.hypot(u, v);
                if (r <= 4.6) {
                    d.set(mx + u, my + v, mz, r > 3.7 ? "gold_block" : "light_blue_stained_glass");
                }
            }
        }
        d.line(mx + 3.5, my - 3.5, mz + 0.5, mx + 6.5, 1, mz + 0.5, "dark_oak_log[axis=y]");
        // ---- the Gamemaster as the detective, back right, facing the players ----
        int gx = -size - 8, gz = 4;
        Props.statue(d, gx, 0, gz, 1, -1, 15, "brown_concrete", "gold_block");
        double k = 15 / 16.0;
        int hy = 1 + (int) Math.round(15.0 * k);
        d.cyl(gx + 0.5, gz + 0.5, 2.9, hy, hy, "gray_wool");
        d.cyl(gx + 0.5, gz + 0.5, 1.7, hy + 1, hy + 2, "gray_wool");
        d.cyl(gx + 0.5, gz + 0.5, 1.7, hy + 1, hy + 1, "black_wool");
        // ---- light: hanging lamps and an even glow ----
        for (int x = x0 + 4; x <= x1 - 4; x += 6) {
            for (int z = z0 + 4; z <= z1 - 4; z += 6) {
                d.set(x, h - 1, z, "iron_chain[axis=y]");
                d.set(x, h - 2, z, "lantern[hanging=true]");
                d.set(x, 3, z, "light[level=15]");
                d.set(x + 3, h - 4, z + 3, "light[level=15]");
            }
        }
        d.commit();
        for (int x = -size - 2; x <= size + 3; x += 3) {
            for (int z = -3; z <= 3; z += 3) {
                arena.setLater(x, 4, z, Deco.st("light[level=15]"));
            }
        }
    }

    /** A dark oak frame with gold corners round a picture spanning x a..b (frame columns), y 1..size+2, z 6. */
    private static void frame(Deco d, int a, int b, int size) {
        for (int x = a; x <= b; x++) {
            for (int y = 1; y <= size + 2; y++) {
                boolean edge = x == a || x == b || y == 1 || y == size + 2;
                if (edge) {
                    boolean corner = (x == a || x == b) && (y == 1 || y == size + 2);
                    d.set(x, y, 6, corner ? "gold_block" : "dark_oak_planks");
                }
            }
        }
    }
}
