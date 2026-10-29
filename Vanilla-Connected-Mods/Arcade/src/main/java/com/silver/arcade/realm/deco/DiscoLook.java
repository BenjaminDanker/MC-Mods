package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Block Party: the Rooftop Disco. The repainted floor (x, z -half..half, y 0) is the dance floor on a skyscraper
 * roof: a giant mirror ball hangs high above it, speaker stacks and a DJ booth where the Gamemaster spins, neon
 * signs in coloured glass, spotlights, and a night city skyline of lit towers all around. A clear ring of open air
 * (4 blocks) and nothing below the floor keep "falling" exactly as it was.
 */
public final class DiscoLook {
    private DiscoLook() {
    }

    public static void build(Arena arena, int tier, int half) {
        Deco d = new Deco(arena, 20202L);
        int c = half + 7; // a 7-block gap: too wide to jump across to the deck
        d.keepOut(-c, -40, -c, c, 8, c);
        // ---- the roof deck around the clear ring (a moat of air between) ----
        int r0 = c + 1, r1 = c + 14;
        for (int x = -r1; x <= r1; x++) {
            for (int z = -r1; z <= r1; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) < r0) {
                    continue;
                }
                d.set(x, -1, z, ((x + z) & 1) == 0 ? "black_concrete" : "gray_concrete");
                if (Math.max(Math.abs(x), Math.abs(z)) == r0) {
                    d.set(x, -1, z, "sea_lantern");
                    d.set(x, 0, z, "magenta_stained_glass_pane");
                }
            }
        }
        // the building below the deck
        for (int y = -60; y <= -2; y++) {
            for (int x = -r1; x <= r1; x++) {
                for (int z = -r1; z <= r1; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != r1) {
                        continue;
                    }
                    boolean win = Math.floorMod(x + z, 3) != 0 && Math.floorMod(y, 4) != 0;
                    d.set(x, y, z, win ? (Math.floorMod(x * 7 + y * 3 + z, 5) < 2 ? "yellow_stained_glass" : "black_stained_glass") : "gray_concrete");
                }
            }
            if (y % 4 == 0) {
                d.walls(-r1 + 1, y, -r1 + 1, r1 - 1, y, r1 - 1, "glowstone");
            }
        }
        // ---- the DJ booth (+z) with the Gamemaster as DJ ----
        int bz = r0 + 6;
        d.fill(-8, 0, bz - 2, 8, 2, bz + 2, "black_concrete");
        d.fill(-8, 2, bz - 3, 8, 2, bz - 3, "gold_block");
        for (int x = -7; x <= 7; x++) {
            d.set(x, 1, bz - 3, Math.floorMod(x, 2) == 0 ? "redstone_lamp[lit=true]" : "magenta_stained_glass");
        }
        d.set(-3, 3, bz - 1, "jukebox");
        d.set(3, 3, bz - 1, "jukebox");
        d.set(0, 3, bz - 1, "note_block");
        Props.statue(d, 0, 2, bz + 3, 0, -1, 18);
        // speaker stacks either side
        for (int sx : new int[] {-13, 13}) {
            for (int y = 0; y <= 12; y++) {
                for (int x = sx - 2; x <= sx + 2; x++) {
                    d.set(x, y, bz, "black_concrete");
                }
            }
            for (int y = 1; y <= 11; y += 4) {
                d.set(sx, y + 1, bz - 1, "note_block");
                d.set(sx - 1, y + 1, bz - 1, "gray_concrete");
                d.set(sx + 1, y + 1, bz - 1, "gray_concrete");
                d.set(sx, y, bz - 1, "gray_concrete");
                d.set(sx, y + 2, bz - 1, "gray_concrete");
            }
            d.set(sx, 13, bz, "sea_lantern");
        }
        // ---- the mirror ball, high above the floor ----
        int by = 26;
        for (int y = by + 6; y <= by + 22; y++) {
            d.set(0, y, 0, "iron_chain[axis=y]");
        }
        d.ellipsoid(0.5, by + 0.5, 0.5, 5.5, 5.5, 5.5, 0, "glass|tinted_glass|white_stained_glass|light_gray_stained_glass|glass");
        d.ellipsoid(0.5, by + 0.5, 0.5, 4.2, 4.2, 4.2, 0, "sea_lantern");
        // ---- spotlights on poles at the deck corners, beams of coloured glass toward the ball ----
        String[] beams = {"magenta_stained_glass", "cyan_stained_glass", "lime_stained_glass", "yellow_stained_glass"};
        int[][] poles = {{-r1 + 3, -r1 + 3}, {r1 - 3, -r1 + 3}, {-r1 + 3, r1 - 3}, {r1 - 3, r1 - 3}};
        for (int i = 0; i < 4; i++) {
            int px = poles[i][0], pz = poles[i][1];
            for (int y = 0; y <= 14; y++) {
                d.set(px, y, pz, "iron_bars");
            }
            d.set(px, 15, pz, "redstone_lamp[lit=true]");
            d.line(px + 0.5, 16.5, pz + 0.5, px * 0.45 + 0.5, by - 4.5, pz * 0.45 + 0.5, beams[i]);
        }
        // ---- neon signs on the parapet sides: words in coloured glass and glowstone ----
        neon(d, -18, 2, -r0 - 4, "DANCE", "magenta_stained_glass");
        neon(d, -r0 - 4, 2, -6, "GM", "cyan_stained_glass");
        // ---- the skyline all around ----
        java.util.Random r = d.rnd;
        for (int i = 0; i < 60; i++) {
            double a = r.nextDouble() * Math.PI * 2, dist = 50 + r.nextDouble() * 60;
            int x = (int) (dist * Math.cos(a)), z = (int) (dist * Math.sin(a));
            int w = 4 + r.nextInt(6), h = 10 + r.nextInt(55);
            tower(d, x, -60, z, w, h + 50, r);
        }
        d.commit();
        for (int x = -6; x <= 6; x += 6) {
            for (int z = -6; z <= 6; z += 6) {
                arena.setLater(x, 5, z, Deco.st("light[level=15]"));
            }
        }
    }

    static void tower(Deco d, int x, int y0, int z, int w, int h, java.util.Random r) {
        String[] lit = {"yellow_stained_glass", "light_blue_stained_glass", "orange_stained_glass", "black_stained_glass", "black_stained_glass"};
        for (int y = y0; y <= y0 + h; y++) {
            for (int i = 0; i < w; i++) {
                for (int[] p : new int[][] {{x + i, z}, {x + i, z + w - 1}, {x, z + i}, {x + w - 1, z + i}}) {
                    boolean frame = y % 3 == 0 || i == 0 || i == w - 1;
                    d.set(p[0], y, p[1], frame ? "gray_concrete" : lit[Math.floorMod(p[0] * 7 + y * 13 + p[1] * 3, lit.length)]);
                }
            }
            if (y % 6 == 0) {
                d.fill(x + 1, y, z + 1, x + w - 2, y, z + w - 2, "glowstone");
            }
        }
        d.fill(x, y0 + h + 1, z, x + w - 1, y0 + h + 1, z + w - 1, "gray_concrete");
        d.set(x + w / 2, y0 + h + 2, z + w / 2, "redstone_lamp[lit=true]");
    }

    /** Blocky 3x5 letters in coloured glass on a glowstone backing, along +x from (x, y, z). */
    static void neon(Deco d, int x, int y, int z, String text, String glass) {
        java.util.Map<Character, String[]> font = java.util.Map.of(
            'D', new String[] {"11.", "1.1", "1.1", "1.1", "11."},
            'A', new String[] {".1.", "1.1", "111", "1.1", "1.1"},
            'N', new String[] {"1.1", "111", "111", "1.1", "1.1"},
            'C', new String[] {"111", "1..", "1..", "1..", "111"},
            'E', new String[] {"111", "1..", "11.", "1..", "111"},
            'G', new String[] {"111", "1..", "1.1", "1.1", "111"},
            'M', new String[] {"1.1", "111", "111", "1.1", "1.1"});
        int cx = 0;
        boolean alongX = Math.abs(z) > Math.abs(x);
        for (char ch : text.toCharArray()) {
            String[] g = font.get(ch);
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 3; col++) {
                    int px = alongX ? x + cx + col : x, pz = alongX ? z : z + cx + col;
                    int py = y + 4 - row;
                    d.set(px, py, pz, g[row].charAt(col) == '1' ? glass : "black_concrete");
                    if (alongX) {
                        d.set(px, py, pz + (z < 0 ? -1 : 1), "glowstone");
                    } else {
                        d.set(px + (x < 0 ? -1 : 1), py, pz, "glowstone");
                    }
                }
            }
            cx += 4;
        }
    }
}
