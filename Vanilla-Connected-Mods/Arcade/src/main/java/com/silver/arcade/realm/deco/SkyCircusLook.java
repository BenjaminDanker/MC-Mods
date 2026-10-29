package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Parkour: the Sky Circus. The course runs from the start pad (x, z -2..2) along +z through the open sky. Around it,
 * well out of reach, float striped hot-air balloons, a lit Ferris wheel and a circus big top on its own island;
 * a gilded triumph arch with confetti crowns the gold finish, a big top entrance frames the start, and the
 * Gamemaster stands colossal beyond the finish as ringmaster. Invisible light hangs above the footholds so the course
 * reads at night. Nothing is placed within 18 blocks sideways of the course or under it.
 */
public final class SkyCircusLook {
    private SkyCircusLook() {
    }

    /** jumps: {x, y, z} of each foothold; finish: {x, y, z} centre of the gold pad. */
    public static void build(Arena arena, int tier, int[][] jumps, int[] finish) {
        Deco d = new Deco(arena, 9091L + jumps.length);
        int minX = 0, maxX = 0, len = finish[2] + 4;
        for (int[] j : jumps) {
            minX = Math.min(minX, j[0]);
            maxX = Math.max(maxX, j[0]);
        }
        // keep the course corridor and everything under it clear
        d.keepOut(minX - 4, -90, -3, maxX + 4, 30, len + 1);
        // ---- light above the footholds ----
        for (int i = 0; i < jumps.length; i += 2) {
            int[] j = jumps[i];
            arena.setLater(j[0], j[1] + 4, j[2], Deco.st("light[level=13]"));
        }
        // ---- start: a big top entrance behind the start pad ----
        for (int x = -6; x <= 6; x++) {
            for (int y = 0; y <= 9; y++) {
                int z = -4 - (int) Math.round(0.0);
                double arch = Math.hypot(x, Math.max(0, y - 2) * 1.1);
                if (arch > 6.5) {
                    continue;
                }
                boolean opening = Math.abs(x) <= 2 && y >= 1 && y <= 4;
                if (!opening) {
                    d.set(x, y, -4, ((x + 100) / 2) % 2 == 0 ? "red_wool" : "white_wool");
                }
            }
        }
        for (int x = -7; x <= 7; x++) {
            d.set(x, 0, -3, "gold_block");
        }
        // big top behind the entrance: a striped cone
        for (int y = 0; y <= 16; y++) {
            double r = 12 - y * 0.7;
            if (r < 0.5) {
                break;
            }
            for (int x = -13; x <= 13; x++) {
                for (int z = -30; z <= -5; z++) {
                    double dd = Math.hypot(x + 0.5, z + 17.5);
                    if (dd <= r && dd > r - 1.3) {
                        double a = Math.atan2(z + 17.5, x + 0.5);
                        d.set(x, y, z, ((int) Math.floor((a + Math.PI) / (Math.PI / 8))) % 2 == 0 ? "red_wool" : "white_wool");
                    }
                }
            }
        }
        d.fill(-13, -1, -30, 13, -1, -5, "smooth_quartz");
        d.set(0, 17, -18, "gold_block");
        for (int y = 18; y <= 21; y++) {
            d.set(0, y, -18, "purple_stained_glass_pane");
        }
        d.set(0, 22, -18, "lantern");
        for (int i = 0; i < 16; i++) {
            double a = Math.PI / 8 * i;
            int x = (int) Math.round(12.5 * Math.cos(a)), z = -18 + (int) Math.round(12.5 * Math.sin(a));
            d.set(x, 0, z, "pearlescent_froglight");
        }
        // ---- finish: a triumph arch with confetti ----
        int fx = finish[0], fy = finish[1], fz = finish[2];
        for (int sx : new int[] {-4, 4}) {
            for (int y = fy - 6; y <= fy + 7; y++) {
                arena.setLater(fx + sx, y, fz, Deco.st(y == fy + 7 ? "gold_block" : y % 3 == 0 ? "gold_block" : "white_concrete"));
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int y = fy + 8; y <= fy + 10 - Math.abs(x) / 2; y++) {
                arena.setLater(fx + x, y, fz, Deco.st(y == fy + 8 ? "gold_block" : (x & 1) == 0 ? "purple_concrete" : "magenta_concrete"));
            }
            arena.setLater(fx + x, fy + 7, fz, Deco.st("redstone_lamp[lit=true]"));
        }
        arena.setLater(fx, fy + 12, fz, Deco.st("gold_block"));
        arena.setLater(fx, fy + 13, fz, Deco.st("lantern"));
        String[] confetti = {"red_concrete", "yellow_concrete", "lime_concrete", "light_blue_concrete", "magenta_concrete", "orange_concrete"};
        for (int i = 0; i < 40; i++) {
            int x = fx + d.rnd.nextInt(21) - 10, y = fy + 6 + d.rnd.nextInt(14), z = fz + 4 + d.rnd.nextInt(10);
            if (Math.abs(x - fx) > 5 || y > fy + 12) {
                arena.setLater(x, y, z, Deco.st(confetti[d.rnd.nextInt(confetti.length)]));
            }
        }
        for (int sx : new int[] {-4, 4}) {
            arena.setLater(fx + sx, fy - 7, fz, Deco.st("lantern[hanging=true]"));
        }
        // ---- backdrop on both sides: balloons, a Ferris wheel, the ringmaster ----
        int left = minX - 30, right = maxX + 30;
        String[][] stripes = {{"red_wool", "white_wool"}, {"blue_wool", "yellow_wool"}, {"purple_wool", "gold_block"},
            {"lime_wool", "white_wool"}, {"orange_wool", "light_blue_wool"}};
        int k = 0;
        for (int z = 10; z < len + 20; z += 28) {
            int side = (k % 2 == 0) ? left - 6 - d.rnd.nextInt(12) : right + 6 + d.rnd.nextInt(12);
            balloon(d, side, 6 + d.rnd.nextInt(14), z + d.rnd.nextInt(10), 5 + d.rnd.nextInt(3), stripes[k % stripes.length]);
            k++;
        }
        for (int z = 24; z < len + 20; z += 34) {
            int side = (k % 2 == 0) ? right + 10 + d.rnd.nextInt(14) : left - 10 - d.rnd.nextInt(14);
            balloon(d, side, 18 + d.rnd.nextInt(12), z + d.rnd.nextInt(10), 4 + d.rnd.nextInt(3), stripes[k % stripes.length]);
            k++;
        }
        ferrisWheel(d, right + 22, 4, Math.min(len / 2 + 5, 60), 15);
        // ringmaster beyond the finish
        int sz = Math.min(len + 34, 195);
        d.fill(fx - 8, fy - 3, sz - 8, fx + 8, fy - 3, sz + 8, "gold_block");
        d.ellipsoid(fx + 0.5, fy - 6, sz + 0.5, 9.5, 4, 9.5, 0, "smooth_quartz");
        Props.statue(d, fx, fy - 3, sz, 0, -1, 30);
        for (int[] c : new int[][] {{-8, -8}, {8, -8}, {-8, 8}, {8, 8}}) {
            Props.post(d, fx + c[0], fy - 2, sz + c[1], 5, "gold_block", "lantern");
        }
        // giant dice adrift
        Props.die(d, left - 14.5, 2, len * 0.3, 7, 30, 40, 20, "white_concrete", "black_concrete");
        Props.die(d, right + 8.5, -10, len * 0.75, 6, -20, 15, 35, "white_concrete", "red_concrete");
        d.commit();
    }

    /** A hot-air balloon: a striped envelope, a lit burner ring, ropes and a basket. */
    static void balloon(Deco d, int x, int y, int z, int r, String[] stripe) {
        for (int ix = x - r - 1; ix <= x + r + 1; ix++) {
            for (int iy = y - r - 1; iy <= y + r + 2; iy++) {
                for (int iz = z - r - 1; iz <= z + r + 1; iz++) {
                    double dx = ix + 0.5 - x, dy = (iy + 0.5 - y) / (iy < y ? 1.25 : 1.0), dz = iz + 0.5 - z;
                    double dd = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dd > r || dd < r - 1.2) {
                        continue;
                    }
                    double a = Math.atan2(dz, dx);
                    boolean equator = Math.abs(iy - y) == 0;
                    String s = equator ? "pearlescent_froglight" : (((int) Math.floor((a + Math.PI) / (Math.PI / 5))) % 2 == 0 ? stripe[0] : stripe[1]);
                    d.set(ix, iy, iz, s);
                }
            }
        }
        int by = y - (int) Math.round(r * 1.25) - 4;
        d.fill(x - 1, by, z - 1, x + 1, by, z + 1, "spruce_planks");
        d.walls(x - 1, by + 1, z - 1, x + 1, by + 1, z + 1, "spruce_fence");
        d.set(x, by + 1, z, "lantern");
        for (int[] c : new int[][] {{-1, -1}, {1, -1}, {-1, 1}, {1, 1}}) {
            for (int yy = by + 2; yy < by + 5; yy++) {
                d.set(x + c[0], yy, z + c[1], "iron_chain[axis=y]");
            }
        }
        d.set(x, by + 3, z, "shroomlight");
    }

    /** A Ferris wheel in the y-z plane at x, hub at (y, z), radius r, with lit cars. */
    static void ferrisWheel(Deco d, int x, int baseY, int z, int r) {
        int hubY = baseY + r + 3;
        for (int a = 0; a < 360; a += 2) {
            double t = Math.toRadians(a);
            int yy = hubY + (int) Math.round(r * Math.sin(t)), zz = z + (int) Math.round(r * Math.cos(t));
            d.set(x, yy, zz, "gold_block");
            d.set(x - 3, yy, zz, "gold_block");
        }
        for (int s = 0; s < 12; s++) {
            double t = Math.toRadians(s * 30);
            int yy = hubY + (int) Math.round(r * Math.sin(t)), zz = z + (int) Math.round(r * Math.cos(t));
            d.line(x + 0.5, hubY + 0.5, z + 0.5, x + 0.5, yy + 0.5, zz + 0.5, "iron_bars");
            d.line(x - 2.5, hubY + 0.5, z + 0.5, x - 2.5, yy + 0.5, zz + 0.5, "iron_bars");
            for (int i = -3; i <= 0; i++) {
                d.set(x + i, yy, zz, "gold_block");
            }
            String[] car = {"red_concrete", "yellow_concrete", "blue_concrete", "lime_concrete", "magenta_concrete", "orange_concrete"};
            d.fill(x - 2, yy - 2, zz - 1, x - 1, yy - 1, zz + 1, car[s % car.length]);
            d.set(x - 1, yy - 3, zz, "lantern[hanging=true]");
        }
        d.fill(x - 3, hubY, z, x, hubY, z, "redstone_lamp[lit=true]");
        for (int i = 0; i <= r + 3; i++) {
            d.set(x + 1, baseY + i, z - (r + 3 - i) / 2, "white_concrete");
            d.set(x + 1, baseY + i, z + (r + 3 - i) / 2, "white_concrete");
            d.set(x - 4, baseY + i, z - (r + 3 - i) / 2, "white_concrete");
            d.set(x - 4, baseY + i, z + (r + 3 - i) / 2, "white_concrete");
        }
        d.fill(x - 6, baseY - 1, z - 12, x + 3, baseY - 1, z + 12, "smooth_quartz");
        d.walls(x - 6, baseY - 1, z - 12, x + 3, baseY - 1, z + 12, "gold_block");
        for (int zz = z - 12; zz <= z + 12; zz += 4) {
            d.set(x + 3, baseY, zz, "lantern");
            d.set(x - 6, baseY, zz, "lantern");
        }
    }
}
