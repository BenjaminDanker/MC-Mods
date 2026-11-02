package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * The Tower (Hanoi): the Toymaker's Workshop. The granite stage (x -half..half, z -4..3), the gold bases (z 2), the
 * fence posts (z 3) and the ring wall (z 4) stay as they are; around them a cosy night-time workshop: plank floor,
 * timber walls with shelves, beams with hanging lanterns, a giant stacking-ring toy behind the ring wall, toy
 * soldiers, building blocks, a rocking horse, a spinning top, and the Gamemaster as the toymaker in apron and cap at
 * his bench. Players stand at z -2.5 facing +z.
 */
public final class ToyshopLook {
    private ToyshopLook() {
    }

    public static void build(Arena arena, int tier, int rings) {
        Deco d = new Deco(arena, 31313L);
        int half = 3 * rings + 6;
        d.keepOut(-half, 0, -4, half, rings + 3, 4);
        int x0 = -half - 12, x1 = half + 12, z0 = -15, z1 = 26, h = Math.max(23, rings + 13);
        // ---- floor, walls, ceiling ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                d.set(x, 0, z, Math.floorMod(x + 2 * z, 6) < 3 ? "oak_planks" : "spruce_planks");
                d.set(x, -1, z, "spruce_planks");
                d.set(x, h, z, Math.floorMod(z, 5) == 0 ? "dark_oak_log[axis=x]" : "spruce_planks");
            }
        }
        for (int y = 1; y < h; y++) {
            String band = y == 1 ? "dark_oak_planks" : (y == 4 ? "stripped_dark_oak_log[axis=x]" : (y < 4 ? "spruce_planks" : "birch_planks|birch_planks|stripped_birch_log[axis=y]"));
            d.walls(x0, y, z0, x1, y, z1, band);
        }
        for (int x = x0; x <= x1; x += 6) {
            for (int y = 1; y < h; y++) {
                d.set(x, y, z0, "dark_oak_log[axis=y]");
                d.set(x, y, z1, "dark_oak_log[axis=y]");
            }
        }
        for (int z = z0; z <= z1; z += 6) {
            for (int y = 1; y < h; y++) {
                d.set(x0, y, z, "dark_oak_log[axis=y]");
                d.set(x1, y, z, "dark_oak_log[axis=y]");
            }
        }
        // round windows onto the night, high on the side walls
        for (int z = z0 + 3; z <= z1 - 3; z += 6) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (Math.abs(dy) + Math.abs(dz) < 2) {
                        d.set(x0, h - 5 + dy, z + dz, "light_blue_stained_glass");
                        d.set(x1, h - 5 + dy, z + dz, "light_blue_stained_glass");
                    }
                }
            }
        }
        // beams across with hanging lanterns
        for (int z = z0 + 5; z <= z1 - 5; z += 5) {
            for (int x = x0 + 1; x < x1; x++) {
                d.set(x, h - 1, z, "dark_oak_log[axis=x]");
            }
            for (int x = x0 + 4; x < x1 - 2; x += 7) {
                d.set(x, h - 2, z, "iron_chain[axis=y]");
                d.set(x, h - 3, z, "lantern[hanging=true]");
            }
        }
        // ---- shelves of toys along the side walls ----
        String[] toy = {"red_concrete", "yellow_concrete", "blue_concrete", "lime_concrete", "orange_concrete", "magenta_concrete", "light_blue_concrete"};
        for (int x : new int[] {x0 + 1, x1 - 1}) {
            for (int z = z0 + 2; z <= z1 - 2; z++) {
                if (Math.floorMod(z, 6) == 0) {
                    continue;
                }
                for (int y : new int[] {3, 6, 9}) {
                    d.set(x, y, z, "spruce_slab[type=top]");
                    if (Math.floorMod(z * 3 + y, 4) != 0) {
                        d.set(x, y + 1, z, toy[Math.floorMod(z * 5 + y, toy.length)]);
                    }
                }
                d.set(x, 1, z, Math.floorMod(z, 3) == 0 ? "barrel[facing=up,open=false]" : "bookshelf");
            }
        }
        // ---- the giant stacking-ring toy behind the ring wall ----
        int tz = 14;
        String[] colours = {"red_wool", "orange_wool", "yellow_wool", "lime_wool", "cyan_wool", "blue_wool", "purple_wool"};
        d.cyl(0.5, tz + 0.5, 8.5, 1, 1, "oak_planks");
        int y = 2;
        for (int i = 0; i < colours.length; i++) {
            double r = 7.6 - i;
            d.tube(0.5, tz + 0.5, r, 0, y, y + 1, colours[i]);
            y += 2;
        }
        for (int yy = 2; yy <= y + 2; yy++) {
            d.set(0, yy, tz, "stripped_oak_log[axis=y]");
        }
        d.ellipsoid(0.5, y + 3.5, tz + 0.5, 2.2, 2.2, 2.2, 0, "red_concrete");
        // ---- toy soldiers on parade, both sides ----
        for (int s : new int[] {-1, 1}) {
            for (int i = 0; i < 5; i++) {
                int sx = s * (half + 3 + (i % 2) * 3), sz = -8 + i * 4;
                soldier(d, sx, sz, i % 2 == 0 ? "red_concrete" : "blue_concrete");
            }
        }
        // ---- building blocks, a rocking horse and a spinning top ----
        int[][] blocks = {{-half - 5, 8}, {-half - 7, 12}, {half + 6, 9}, {half + 4, 13}};
        for (int i = 0; i < blocks.length; i++) {
            int bx = blocks[i][0], bz = blocks[i][1];
            d.fill(bx, 1, bz, bx + 1, 2, bz + 1, toy[i]);
            d.fill(bx, 3, bz, bx + 1, 4, bz + 1, toy[i + 2]);
            if (i % 2 == 0) {
                d.fill(bx + 2, 1, bz, bx + 3, 2, bz + 1, toy[i + 1]);
            }
        }
        horse(d, half + 6, 18);
        for (int yy = 1; yy <= 5; yy++) {
            double r = yy <= 3 ? yy * 0.9 : 3.2 - (yy - 3) * 1.2;
            d.cyl(-half - 6.5, 19.5, Math.max(0.5, r), yy, yy, yy % 2 == 0 ? "magenta_concrete" : "yellow_concrete");
        }
        d.set(-half - 7, 6, 19, "stripped_oak_log[axis=y]");
        d.set(-half - 7, 7, 19, "stripped_oak_log[axis=y]");
        // ---- the toymaker at his bench, back left, facing the players ----
        int gx = -half + 2, gz = 20;
        d.fill(gx - 3, 1, gz - 5, gx + 3, 1, gz - 4, "spruce_planks");
        d.fill(gx - 3, 2, gz - 5, gx + 3, 2, gz - 4, "spruce_slab[type=bottom]");
        d.set(gx - 2, 3, gz - 5, "red_concrete");
        d.set(gx + 2, 3, gz - 5, "lantern[hanging=false]");
        d.set(gx, 3, gz - 4, "crafting_table");
        Props.statue(d, gx, 0, gz, 0, -1, 14, "green_concrete", "gold_block");
        double k = 14 / 16.0;
        // white apron on his front, cap on his head
        for (int yy = 1; yy <= (int) (8 * k); yy++) {
            d.set(gx - 1, yy, gz - (int) Math.round(2.0 * k) - 1, "white_wool");
            d.set(gx, yy, gz - (int) Math.round(2.0 * k) - 1, "white_wool");
            d.set(gx + 1, yy, gz - (int) Math.round(2.0 * k) - 1, "white_wool");
        }
        int capY = 1 + (int) Math.round(15.0 * k);
        d.cyl(gx + 0.5, gz + 0.5, 2.2, capY, capY + 1, "brown_wool");
        d.fill(gx - 1, capY, gz - 3, gx + 1, capY, gz - 3, "brown_wool");
        // ---- light ----
        for (int x = x0 + 3; x <= x1 - 3; x += 5) {
            for (int z = z0 + 3; z <= z1 - 3; z += 5) {
                d.set(x, 3, z, "light[level=15]");
                d.set(x, h - 5, z, "light[level=15]");
            }
        }
        d.commit();
        for (int x = -half; x <= half; x += 3) {
            for (int z = -3; z <= 3; z += 3) {
                arena.setLater(x, 4, z, Deco.st("light[level=15]"));
            }
        }
    }

    private static void soldier(Deco d, int x, int z, String coat) {
        d.set(x, 1, z, "black_concrete");
        d.set(x, 2, z, "white_concrete");
        d.set(x, 3, z, coat);
        d.set(x, 4, z, coat);
        d.set(x, 5, z, "smooth_sandstone");
        d.set(x, 6, z, "black_wool");
        d.set(x, 7, z, "black_wool");
        d.set(x + 1, 3, z, coat);
        d.set(x + 1, 4, z, "stripped_birch_log[axis=y]");
        d.set(x + 1, 5, z, "stripped_birch_log[axis=y]");
        d.set(x + 1, 6, z, "iron_block");
    }

    private static void horse(Deco d, int x, int z) {
        for (int dz = -3; dz <= 3; dz++) {
            d.set(x, 1, z + dz, Math.abs(dz) == 3 ? "dark_oak_planks" : "dark_oak_slab[type=bottom]");
        }
        d.fill(x, 2, z - 2, x, 2, z - 2, "dark_oak_planks");
        d.fill(x, 2, z + 2, x, 2, z + 2, "dark_oak_planks");
        d.fill(x, 3, z - 2, x, 3, z + 2, "white_terracotta");
        d.fill(x, 4, z - 3, x, 5, z - 3, "white_terracotta");
        d.set(x, 6, z - 3, "white_terracotta");
        d.set(x, 6, z - 4, "white_terracotta");
        d.set(x, 4, z - 2, "red_wool");
        d.set(x, 4, z + 3, "brown_wool");
        d.set(x, 5, z - 2, "brown_wool");
    }
}
