package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Connect Four: the Gamemaster's High-Stakes Casino. The board (x 0..6, rows y 2..7 on z 6, blue backing on z 7) is
 * framed like a gilded slot cabinet with marquee bulbs; behind it the Gamemaster himself looms as a statue, playing
 * against you. Red velvet and black-and-gold checker floor, card tables, a giant roulette wheel and fanned giant
 * playing cards on the walls, chandeliers. The player stands at x 3.5, z 2.5 facing +z.
 */
public final class CasinoLook {
    private CasinoLook() {
    }

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 7071L);
        d.keepOut(-1, 0, 0, 7, 0, 7);      // the room's floor
        d.keepOut(-1, 1, 0, 7, 8, 7);      // the play space, the board and its backing
        int x0 = -15, x1 = 21, z0 = -12, z1 = 24, top = 34;
        // ---- floor: black and gold checker with a red carpet to the board ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean border = x == x0 || x == x1 || z == z0 || z == z1;
                d.set(x, 0, z, border ? "polished_blackstone_bricks" : ((x + z) & 1) == 0 ? "black_concrete" : "gold_block");
                if (x >= 1 && x <= 5 && z >= z0 + 1 && z <= -1) {
                    d.set(x, 0, z, x == 1 || x == 5 ? "gold_block" : "red_concrete");
                }
            }
        }
        // ---- walls: red velvet (wool) panels between gold pilasters, a gilt rail; ceiling coffered ----
        for (int y = 1; y <= top; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z : new int[] {z0, z1}) {
                    d.set(x, y, z, wallBlock(x - x0, y, top));
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int x : new int[] {x0, x1}) {
                    d.set(x, y, z, wallBlock(z - z0, y, top));
                }
            }
        }
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean beam = Math.floorMod(x - x0, 6) == 0 || Math.floorMod(z - z0, 6) == 0;
                boolean lamp = Math.floorMod(x - x0, 6) == 3 && Math.floorMod(z - z0, 6) == 3;
                d.set(x, top + 1, z, beam ? "gold_block" : lamp ? "glowstone" : "red_terracotta");
            }
        }
        // ---- the board as a gilded cabinet: frame, marquee bulbs, crest on top ----
        for (int y = 0; y <= 9; y++) {
            for (int x : new int[] {-2, 8}) {
                for (int z = 5; z <= 8; z++) {
                    d.set(x, y, z, y % 3 == 0 ? "gold_block" : "purple_concrete");
                }
            }
        }
        for (int x = -2; x <= 8; x++) {
            d.set(x, 9, 5, "gold_block");
            d.set(x, 9, 4, (x & 1) == 0 ? "redstone_lamp[lit=true]" : "gold_block");
        }
        for (int y = 1; y <= 8; y++) {
            d.set(-2, y, 4, (y & 1) == 0 ? "redstone_lamp[lit=true]" : "gold_block");
            d.set(8, y, 4, (y & 1) == 0 ? "redstone_lamp[lit=true]" : "gold_block");
        }
        // ---- the Gamemaster looms behind the board ----
        Props.statue(d, 3, 1, 18, 0, -1, 28);
        d.fill(-4, 1, 13, 10, 1, 22, "gold_block");
        d.fill(-5, 0, 12, 11, 0, 23, "polished_blackstone_bricks");
        // his throne back behind the statue
        for (int x = -6; x <= 12; x++) {
            int h = 31 - Math.abs(x - 3);
            for (int y = 2; y <= h; y++) {
                d.set(x, y, 23, (x == -6 || x == 12 || y >= h - 1 || y == 2) ? "gold_block" : (Math.floorMod(x - 3, 4) == 0 ? "magenta_wool" : "purple_wool"));
            }
        }
        // spotlights on him from the gallery
        for (int[] p : new int[][] {{-6, 12, 10}, {12, 12, 10}, {3, 26, 11}, {-2, 20, 14}, {8, 20, 14}}) {
            d.set(p[0], p[1], p[2], "light[level=15]");
        }
        // ---- card tables with green felt, stools and candles, on both sides ----
        for (int cx : new int[] {-9, 15}) {
            for (int cz : new int[] {-6, 4, 14}) {
                d.fill(cx - 2, 1, cz - 1, cx + 2, 1, cz + 1, "dark_oak_fence");
                d.fill(cx - 2, 2, cz - 1, cx + 2, 2, cz + 1, "green_wool");
                d.set(cx - 3, 1, cz, "dark_oak_stairs[facing=east]");
                d.set(cx + 3, 1, cz, "dark_oak_stairs[facing=west]");
                d.set(cx, 1, cz - 2, "dark_oak_stairs[facing=south]");
                d.set(cx, 1, cz + 2, "dark_oak_stairs[facing=north]");
                d.set(cx, 3, cz, "candle[candles=3,lit=true]");
                d.set(cx - 1, 3, cz, "white_carpet");
                d.set(cx + 1, 3, cz, "red_carpet");
                Props.chandelier(d, cx, top, cz, 11, 2, 4);
            }
        }
        // ---- giant roulette wheel on the left wall, fanned cards on the right wall ----
        roulette(d, x0 + 1, 8, 5, 6);
        int[][] cards = {{-3, 0}, {0, 0}, {3, 0}};
        String[] suits = {"red_concrete", "black_concrete", "red_concrete"};
        for (int i = 0; i < 3; i++) {
            card(d, x1 - 1, 3 + i, -2 + i * 5, suits[i], i);
        }
        // ---- grand chandelier over the player, two statues' lamps ----
        Props.chandelier(d, -5, top, -6, 14, 3, 6);
        Props.chandelier(d, 11, top, -6, 14, 3, 6);
        for (int[] p : new int[][] {{-5, 2}, {11, 2}, {-5, -8}, {11, -8}}) {
            Props.post(d, p[0], 1, p[1], 4, "gold_block", "lantern");
        }
        Props.die(d, -10.5, 3.0, 18.5, 5, 20, 30, 10, "red_concrete", "white_concrete");
        Props.die(d, 16.5, 2.5, -9.5, 4, -25, 0, 0, "red_concrete", "white_concrete");
        d.commit();
    }

    private static String wallBlock(int along, int y, int top) {
        if (y == 1 || y == top || y == 16) {
            return "gold_block";
        }
        if (y > 16) {
            if (Math.floorMod(along, 6) == 0) {
                return y % 6 == 2 ? "redstone_lamp[lit=true]" : "gold_block";
            }
            return y % 6 == 2 ? "purple_concrete" : "purple_terracotta";
        }
        if (Math.floorMod(along, 6) == 0) {
            return y == 7 ? "redstone_lamp[lit=true]" : "gold_block";
        }
        if (y == 4) {
            return "dark_oak_planks";
        }
        return y < 4 ? "dark_oak_planks" : "red_wool";
    }

    /** A roulette wheel of radius r on a wall at x (facing +x), centred y, z. */
    private static void roulette(Deco d, int x, int cy, int cz, int r) {
        for (int y = cy - r - 1; y <= cy + r + 1; y++) {
            for (int z = cz - r - 1; z <= cz + r + 1; z++) {
                double dist = Math.hypot(y - cy, z - cz);
                if (dist > r + 0.6) {
                    continue;
                }
                double a = Math.toDegrees(Math.atan2(y - cy, z - cz));
                int seg = Math.floorMod((int) Math.floor(a / 18.0), 20);
                String s;
                if (dist > r - 0.4) {
                    s = "gold_block";
                } else if (dist > 2.4) {
                    s = seg == 0 ? "lime_concrete" : (seg & 1) == 0 ? "red_concrete" : "black_concrete";
                } else if (dist > 1.4) {
                    s = "dark_oak_planks";
                } else {
                    s = "gold_block";
                }
                d.set(x, y, z, s);
            }
        }
        d.set(x + 1, cy, cz, "lightning_rod[facing=east]");
        d.set(x + 1, cy + r - 1, cz, "white_concrete"); // the ball
    }

    /** A giant playing card (7 x 10) on the right wall, slightly lifted per index. */
    private static void card(Deco d, int x, int lift, int cz, String suit, int i) {
        for (int y = 0; y < 10; y++) {
            for (int z = 0; z < 7; z++) {
                boolean edge = y == 0 || y == 9 || z == 0 || z == 6;
                String s = edge ? "gold_block" : "white_concrete";
                int yy = y + lift + 1;
                d.set(x, yy, cz + z - 3, s);
            }
        }
        // a big suit symbol (diamond for red, spade-ish for black)
        for (int y = 2; y <= 7; y++) {
            int w = suit.startsWith("red") ? 2 - Math.abs(y - 5) / 1 : (y >= 4 ? 2 - Math.max(0, y - 6) : 0);
            for (int z = -Math.max(0, w); z <= Math.max(0, w); z++) {
                d.set(x, y + lift + 1, cz + z, suit);
            }
        }
        if (!suit.startsWith("red")) {
            d.set(x, 3 + lift, cz, "black_concrete");
            d.set(x, 2 + lift, cz, "black_concrete");
        }
    }
}
