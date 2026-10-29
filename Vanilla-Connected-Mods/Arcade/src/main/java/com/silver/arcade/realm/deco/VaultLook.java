package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Mastermind: the Codebreaker's Vault. The board (planks on z 7, pegs and the gold check button on z 6) becomes the
 * dial wall of a bank vault: a colossal round vault door with gold bolts stands open beside it, gold bars are stacked
 * on pallets, safes line the walls, red laser grids (glass) guard the treasury, and a statue of the Gamemaster
 * counts his coins on a throne of gold. Players stand at z 1.5 facing +z.
 */
public final class VaultLook {
    private VaultLook() {
    }

    public static void build(Arena arena, int tier, int pegs, int rows) {
        Deco d = new Deco(arena, 26262L);
        int bx1 = 2 * pegs + 3, top = 2 + rows + 1;
        d.keepOut(-3, 0, -2, bx1, top + 1, 7);
        int x0 = -16, x1 = bx1 + 16, z0 = -14, z1 = 24, h = Math.max(16, top + 8);
        int cx = (x0 + x1) / 2;
        // floor: steel plates with gold inlay, walls: iron and polished deepslate, ceiling lamps
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                d.set(x, 0, z, (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? "gold_block" : "polished_deepslate");
                d.set(x, h, z, Math.floorMod(x, 4) == 2 && Math.floorMod(z, 4) == 2 ? "sea_lantern" : "iron_block");
            }
        }
        for (int y = 1; y < h; y++) {
            d.walls(x0, y, z0, x1, y, z1, y % 5 == 0 ? "gold_block" : (Math.floorMod(y, 5) == 2 ? "iron_block" : "deepslate_bricks|polished_deepslate"));
        }
        // vault lamps set into the walls (a band of sea lanterns every four blocks, twice up the wall)
        for (int y : new int[] {3, 8}) {
            for (int x = x0 + 2; x <= x1 - 2; x += 4) {
                d.set(x, y, z0, "sea_lantern");
                d.set(x, y, z1, "sea_lantern");
            }
            for (int z = z0 + 2; z <= z1 - 2; z += 4) {
                d.set(x0, y, z, "sea_lantern");
                d.set(x1, y, z, "sea_lantern");
            }
        }
        // the board's surround: steel frame, rivets of gold, a big dial above
        for (int y = 0; y <= top + 2; y++) {
            d.set(-3, y, 8, "iron_block");
            d.set(bx1, y, 8, "iron_block");
        }
        d.fill(-3, top + 2, 8, bx1, top + 2, 8, "gold_block");
        int dx = (bx1 - 3) / 2;
        for (int x = -6; x <= 6; x++) {
            for (int y = -6; y <= 6; y++) {
                double r = Math.hypot(x, y);
                if (r <= 6.3) {
                    d.set(dx + x, top + 9 + y, 9, r > 5.3 ? "gold_block" : (r < 1.3 ? "gold_block" : ((Math.abs(x) + Math.abs(y)) % 3 == 0 ? "iron_block" : "light_gray_concrete")));
                }
            }
        }
        d.fill(-3, 1, 9, bx1, top + 16, 9, "polished_deepslate");
        // the colossal open vault door on the left
        int vx = -10, vz = 10;
        for (int y = 0; y <= 14; y++) {
            for (int z = -7; z <= 7; z++) {
                double r = Math.hypot(z, y - 7);
                if (r > 7.3) {
                    continue;
                }
                for (int t = 0; t < 2; t++) {
                    d.set(vx + t, y, vz + z - 9, r > 6.3 ? "gold_block" : (r < 1.6 ? "gold_block" : (r > 3.8 && r < 4.6 ? "iron_block" : "light_gray_concrete")));
                }
            }
            // the frame in the wall it swung out of
        }
        for (int z = -3; z <= 3; z++) {
            d.set(vx + 2, 7 + z, vz - 9, "gold_block");
            d.set(vx + 2, 7, vz - 9 + z, "gold_block");
        }
        // bolts sticking out of the door edge
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            d.set(vx + 2, 7 + (int) Math.round(7 * Math.sin(a)), vz - 9 + (int) Math.round(7 * Math.cos(a)), "gold_block");
        }
        // gold bars on pallets, safes along the walls
        for (int i = 0; i < 6; i++) {
            int px = x0 + 3 + (i % 3) * 5, pz = 12 + (i / 3) * 6;
            d.fill(px, 1, pz, px + 2, 1, pz + 2, "spruce_slab[type=bottom]");
            for (int l = 0; l < 3; l++) {
                d.fill(px, 2 + l, pz + (l % 2), px + 2 - (l % 2), 2 + l, pz + 2, "gold_block");
            }
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            if (x >= -4 && x <= bx1 + 1) {
                continue;
            }
            d.set(x, 1, z0 + 1, "iron_block");
            d.set(x, 2, z0 + 1, "iron_block");
            d.set(x, 2, z0 + 2, "lever[face=wall,facing=south,powered=false]");
        }
        // laser grid across the treasury at the back right (red glass panes in a lattice)
        for (int x = bx1 + 4; x <= x1 - 1; x++) {
            for (int y = 1; y <= 8; y++) {
                if ((x + y) % 3 == 0) {
                    d.set(x, y, 14, "red_stained_glass_pane");
                }
            }
        }
        // the Gamemaster on a throne of gold, counting
        int sx = bx1 + 9, sz = 19;
        d.fill(sx - 4, 1, sz - 3, sx + 4, 3, sz + 3, "gold_block");
        Props.statue(d, sx, 3, sz, -1, -1, 13);
        for (int i = 0; i < 20; i++) {
            d.set(sx - 4 + d.rnd.nextInt(9), 4, sz - 3 + d.rnd.nextInt(3), "gold_block");
        }
        // lamps beside the player's spot
        for (int x : new int[] {-5, bx1 + 2}) {
            Props.post(d, x, 1, 2, 4, "polished_blackstone_wall", "lantern");
        }
        d.commit();
        for (int x = -2; x <= bx1 - 1; x += 5) {
            arena.setLater(x, 4, 3, Deco.st("light[level=15]"));
            arena.setLater(x, top, 3, Deco.st("light[level=15]"));
        }
        for (int x = x0 + 2; x <= x1 - 2; x += 3) {
            for (int z = z0 + 2; z <= z1 - 2; z += 3) {
                arena.setLater(x, 2, z, Deco.st("light[level=15]"));
                arena.setLater(x + 1, 6, z + 1, Deco.st("light[level=15]"));
                arena.setLater(x, h - 3, z, Deco.st("light[level=15]"));
            }
        }
    }
}
