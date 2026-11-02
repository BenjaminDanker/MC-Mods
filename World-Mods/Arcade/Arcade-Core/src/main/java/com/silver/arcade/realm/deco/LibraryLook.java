package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Typing Race: the Gamemaster's Grand Library. A gilded reading hall of towering bookshelves under a barrel vault,
 * three chandeliers, a gallery ledge with lamps, and at the far end a colossal open book with a quill in its inkwell,
 * flanked by two statues of him. Players stand on the quartz dais (x -4..4, z -4..4) facing +z.
 */
public final class LibraryLook {
    private LibraryLook() {
    }

    static final int X = 16, ZB = -13, ZF = 27, TOP = 17;

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 4041L);
        d.keepOut(-4, 1, -4, 4, 4, 4); // the dais and the space above it (lectern included)
        // ---- floor: dark parquet with a gold frame round the dais and a red runner to the book ----
        for (int x = -X + 1; x <= X - 1; x++) {
            for (int z = ZB + 1; z <= ZF - 1; z++) {
                boolean lamp = Math.floorMod(x, 5) == 0 && Math.floorMod(z, 5) == 2 && (Math.abs(x) > 6 || Math.abs(z - 0) > 6);
                d.set(x, 0, z, lamp ? "ochre_froglight" : ((x + z) & 1) == 0 ? "dark_oak_planks" : "stripped_dark_oak_log[axis=x]");
            }
        }
        d.walls(-5, 0, -5, 5, 0, 5, "gold_block");
        d.walls(-6, 0, -6, 6, 0, 6, "polished_blackstone_bricks");
        for (int z = 6; z <= 19; z++) {
            d.set(-2, 1, z, "yellow_carpet");
            d.set(2, 1, z, "yellow_carpet");
            d.fill(-1, 1, z, 1, 1, z, "red_carpet");
        }
        for (int cx : new int[] {-6, 6}) {
            for (int cz : new int[] {-6, 6}) {
                d.set(cx, 1, cz, "gold_block");
                d.set(cx, 2, cz, "polished_blackstone_wall");
                d.set(cx, 3, cz, "polished_blackstone_wall");
                d.set(cx, 4, cz, "gold_block");
                d.set(cx, 5, cz, "lantern");
            }
        }
        // ---- walls: shelves, log pillars, a gold band and a gallery ledge ----
        d.walls(-X, 0, ZB, X, TOP, ZF, "bookshelf");
        for (int x = -X; x <= X; x++) {
            for (int z = ZB; z <= ZF; z++) {
                boolean edge = x == -X || x == X || z == ZB || z == ZF;
                if (!edge) {
                    continue;
                }
                boolean pillar = (x == -X || x == X) ? Math.floorMod(z - ZB, 5) == 0 : Math.floorMod(x + X, 5) == 0;
                for (int y = 1; y <= TOP; y++) {
                    if (pillar) {
                        d.set(x, y, z, "stripped_dark_oak_log");
                    } else if (y == 9) {
                        d.set(x, y, z, "gold_block");
                    } else if (y > 9 && d.rnd.nextInt(7) == 0) {
                        d.set(x, y, z, "chiseled_bookshelf[facing=" + inward(x, z) + "]");
                    }
                }
                if (pillar) {
                    d.set(x, 9, z, "gold_block");
                    d.set(x, TOP, z, "gold_block");
                }
            }
        }
        // gallery ledge one block in, at y 9: slab top, fence on its inner edge, lamps on it, lanterns hung under it
        for (int x = -X + 1; x <= X - 1; x++) {
            for (int z = ZB + 1; z <= ZF - 1; z++) {
                boolean ring = x == -X + 1 || x == X - 1 || z == ZB + 1 || z == ZF - 1;
                if (!ring) {
                    continue;
                }
                d.set(x, 9, z, "dark_oak_slab[type=top]");
                int along = (x == -X + 1 || x == X - 1) ? z : x;
                d.set(x, 10, z, Math.floorMod(along, 4) == 0 ? "lantern" : "dark_oak_fence");
                if (Math.floorMod(along, 4) == 2) {
                    d.set(x, 8, z, "lantern[hanging=true]");
                }
            }
        }
        // purple and gold banners on the pillars below the ledge
        for (int z = ZB + 5; z < ZF; z += 5) {
            d.set(-X + 1, 7, z, "purple_wall_banner[facing=east]");
            d.set(X - 1, 7, z, "purple_wall_banner[facing=west]");
        }
        // ---- barrel vault along z with gold ribs ----
        for (int x = -X; x <= X; x++) {
            int h = TOP + (int) Math.round(6 * Math.sqrt(Math.max(0, 1 - (x / (double) X) * (x / (double) X))));
            for (int z = ZB; z <= ZF; z++) {
                boolean rib = Math.floorMod(z - ZB, 5) == 0;
                boolean lamp = !rib && Math.floorMod(z - ZB, 5) == 2 && Math.floorMod(x, 6) == 3;
                d.set(x, h, z, rib ? "gold_block" : lamp ? "shroomlight" : "dark_oak_planks");
                d.set(x, h + 1, z, "dark_oak_planks");
                for (int y = TOP + 1; y < h; y++) {
                    if (x == -X || x == X || z == ZB || z == ZF) {
                        d.set(x, y, z, "dark_oak_planks");
                    }
                }
            }
        }
        // end walls of the vault: purple glass rose windows lit from behind
        for (int z : new int[] {ZB, ZF}) {
            for (int x = -6; x <= 6; x++) {
                for (int y = TOP + 1; y <= TOP + 5; y++) {
                    if (Math.hypot(x, (y - TOP) * 1.2) <= 6.2) {
                        boolean spoke = x == 0 || (y - TOP) == Math.abs(x) / 2 + 1;
                        d.set(x, y, z, spoke ? "gold_block" : "purple_stained_glass");
                        d.set(x, y, z + (z == ZB ? -1 : 1), "shroomlight");
                    }
                }
            }
        }
        // ---- chandeliers ----
        Props.chandelier(d, 0, TOP + 5, 0, 13, 4, 8);
        Props.chandelier(d, 0, TOP + 5, 12, 14, 3, 6);
        Props.chandelier(d, 0, TOP + 5, -9, 14, 3, 6);
        // ---- reading tables with candles along the sides ----
        for (int z : new int[] {-8, 0, 8, 14}) {
            for (int sx : new int[] {-11, 11}) {
                for (int x = sx - 1; x <= sx + 1; x++) {
                    d.set(x, 1, z, "dark_oak_fence");
                    d.set(x, 2, z, "dark_oak_slab[type=top]");
                }
                d.set(sx - 1, 3, z, "candle[candles=3,lit=true]");
                d.set(sx + 1, 3, z, "candle[candles=2,lit=true]");
                d.set(sx, 3, z, "lectern[facing=" + (sx < 0 ? "east" : "west") + "]");
                d.set(sx, 1, z - 1, "dark_oak_stairs[facing=south]");
                d.set(sx, 1, z + 1, "dark_oak_stairs[facing=north]");
            }
        }
        // ---- the colossal open book on a stand, at the far end ----
        int bz = 21;
        d.fill(-7, 1, bz, 7, 3, bz + 4, "dark_oak_planks");
        d.fill(-8, 1, bz - 1, 8, 3, bz - 1, "polished_blackstone_bricks");
        d.fill(-7, 4, bz, 7, 4, bz + 4, "gold_block");
        for (int x = -9; x <= 9; x++) {
            int ax = Math.abs(x);
            int curl = ax == 0 ? -1 : ax <= 2 ? 0 : ax <= 7 ? 1 : 0; // pages bow up from the spine and droop at the edge
            for (int t = 0; t < 10; t++) {
                int y = 5 + t + curl, z = bz + 1 + t / 3;
                if (ax == 0) {
                    d.set(x, y, z, "gold_block");
                    d.set(x, y, z + 1, "purple_concrete");
                    continue;
                }
                boolean border = ax == 9 || t == 9;
                boolean text = !border && ax >= 2 && ax <= 7 && t >= 2 && t <= 8 && t % 3 == 2 && d.rnd.nextInt(5) != 0;
                d.set(x, y, z, border ? "gold_block" : text ? "light_gray_concrete" : "white_concrete");
                d.set(x, y, z + 1, "purple_concrete");
                d.set(x, y, z + 2, ax == 9 || t == 9 ? "gold_block" : "purple_concrete");
            }
        }
        // a row of candles and two lamps light the pages
        for (int x = -6; x <= 6; x += 2) {
            d.set(x, 5, bz, "candle[candles=4,lit=true]");
        }
        for (int lx : new int[] {-5, 5}) {
            for (int yy = 15; yy <= TOP + 4; yy++) {
                d.set(lx, yy, bz - 3, "iron_chain[axis=y]");
            }
            d.set(lx, 14, bz - 3, "lantern[hanging=true]");
        }
        for (int lx = -6; lx <= 6; lx += 6) {
            d.set(lx, 9, bz - 2, "light[level=15]"); // invisible light on the pages
        }
        d.set(-8, 5, bz - 1, "lantern");
        d.set(8, 5, bz - 1, "lantern");
        d.fill(-8, 4, bz - 1, 8, 4, bz - 1, "gold_block");
        // the quill in its inkwell on the right of the book
        d.fill(9, 1, bz + 1, 11, 3, bz + 3, "black_concrete");
        d.walls(9, 4, bz + 1, 11, 4, bz + 3, "gold_block");
        d.set(10, 4, bz + 2, "black_concrete");
        for (int i = 0; i < 16; i++) {
            int x = 10 - i / 3, y = 5 + i, z = bz + 2 + i / 6;
            d.set(x, y, z, i < 2 ? "gold_block" : "light_gray_wool");
            if (i >= 3) {
                d.set(x + 1, y, z, "white_wool");
                d.set(x - 1, y, z, i < 13 ? "white_wool" : "light_gray_wool");
                if (i >= 6 && i <= 12) {
                    d.set(x + 2, y, z, "white_wool");
                }
            }
        }
        // ---- two statues of the Gamemaster flanking the book ----
        Props.statueOnPlinth(d, -12, 1, 21, 0, -1, 12, 2);
        Props.statueOnPlinth(d, 12, 1, 18, 0, -1, 12, 2);
        // ---- a grand (closed) door behind the dais with gilt frame and lamps ----
        d.fill(-3, 1, ZB + 1, 3, 7, ZB + 1, "dark_oak_planks");
        d.walls(-4, 1, ZB + 1, 4, 8, ZB + 1, "gold_block");
        d.fill(-3, 1, ZB + 1, 3, 7, ZB + 1, "spruce_planks");
        d.set(-1, 4, ZB + 1, "gold_block");
        d.set(1, 4, ZB + 1, "gold_block");
        d.set(-5, 5, ZB + 2, "lantern");
        d.set(5, 5, ZB + 2, "lantern");
        d.set(-5, 4, ZB + 2, "gold_block");
        d.set(5, 4, ZB + 2, "gold_block");
        // giant dice lying about on the floor
        Props.die(d, -12.5, 3.0, -6.5, 5, 25, 0, 0, "white_concrete", "black_concrete");
        Props.die(d, 12.5, 2.5, -9.5, 4, -15, 0, 0, "white_concrete", "purple_concrete");
        d.commit();
    }

    private static String inward(int x, int z) {
        if (x == -X) {
            return "east";
        }
        if (x == X) {
            return "west";
        }
        return z == ZB ? "south" : "north";
    }
}
