package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * King of the Hill: the Sun Temple. The stepped pyramid (base radius 7, gold crown on top) and its sandstone apron
 * (x, z -11..11, y 0) are left exactly as they are (steps stay climbable); around them a jungle: four mossy temple
 * ruins with serpent heads, a ring of great stone sun pillars, a waterfall of blue glass off a jungle cliff, giant
 * kapok trees with vines, and a towering sun-god Gamemaster in jade and gold behind the hill. A 7-block gap of open
 * air round the apron keeps the fight on the hill.
 */
public final class TempleLook {
    private TempleLook() {
    }

    public static void build(Arena arena, int tier, int base) {
        Deco d = new Deco(arena, 24242L);
        int apron = base + 4, c = apron + 7;
        d.keepOut(-c, -40, -c, c, base + 6, c);
        // ---- jungle floor ----
        for (int x = -75; x <= 75; x++) {
            for (int z = -75; z <= 75; z++) {
                double r = Math.hypot(x, z);
                if (r > 75 || Math.max(Math.abs(x), Math.abs(z)) <= c) {
                    continue;
                }
                int h = -1 + (int) Math.round(Math.max(0, r - 30) * 0.15);
                d.set(x, h, z, Math.floorMod(x * 3 + z * 7, 7) == 0 ? "moss_block" : (Math.floorMod(x + z, 5) == 0 ? "podzol" : "grass_block"));
                d.set(x, h - 1, z, "dirt");
                d.set(x, h - 2, z, "dirt");
                if (Math.floorMod(x * 11 + z * 13, 6) == 0) {
                    d.set(x, h + 1, z, "fern|short_grass|short_grass");
                }
            }
        }
        // ---- sun pillars in a ring around the gap ----
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12;
            int x = (int) Math.round((c + 4) * Math.cos(a)), z = (int) Math.round((c + 4) * Math.sin(a));
            for (int y = 0; y <= 10; y++) {
                d.set(x, y, z, y % 4 == 0 ? "chiseled_stone_bricks" : (Math.floorMod(x + y, 3) == 0 ? "mossy_stone_bricks" : "stone_bricks"));
            }
            d.set(x, 11, z, "gold_block");
            d.set(x, 12, z, "campfire[lit=true]");
        }
        // ---- four mossy ruins with serpent heads at the diagonals ----
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4;
            int x = (int) Math.round((c + 22) * Math.cos(a)), z = (int) Math.round((c + 22) * Math.sin(a));
            ziggurat(d, x, 0, z, 6, 4);
            serpent(d, x, 7, z, -(int) Math.signum(Math.cos(a)), -(int) Math.signum(Math.sin(a)));
        }
        // ---- the waterfall cliff (north) ----
        for (int x = -20; x <= 20; x++) {
            for (int z = -c - 40; z <= -c - 26; z++) {
                int top = 30 - Math.abs(x) / 3 + (z + c + 33) / 2;
                for (int y = -1; y <= top; y++) {
                    if (y >= top - 2 || z == -c - 26 || Math.abs(x) == 20) {
                        d.set(x, y, z, y == top ? "grass_block" : "mossy_cobblestone|stone|mossy_stone_bricks");
                    }
                }
            }
        }
        for (int y = -1; y <= 30; y++) {
            for (int x = -4; x <= 4; x++) {
                d.set(x, y, -c - 25, (y + x) % 5 == 0 ? "white_stained_glass" : "light_blue_stained_glass");
            }
        }
        d.ellipsoid(0.5, -1, -c - 20, 9, 1, 6, 0, "light_blue_stained_glass");
        // ---- kapok trees ----
        for (int i = 0; i < 14; i++) {
            double a = d.rnd.nextDouble() * Math.PI * 2, r = c + 30 + d.rnd.nextDouble() * 30;
            int x = (int) (r * Math.cos(a)), z = (int) (r * Math.sin(a));
            if (z < -c - 15 && Math.abs(x) < 25) {
                continue;
            }
            kapok(d, x, 0, z, 18 + d.rnd.nextInt(10));
        }
        // ---- the sun god behind the hill (+z) ----
        int sz = c + 26;
        d.fill(-9, 0, sz - 7, 9, 3, sz + 7, "mossy_stone_bricks|stone_bricks");
        Props.statue(d, 0, 3, sz, 0, -1, 34, "green_terracotta", "gold_block");
        // a sun disc behind his head
        for (int x = -12; x <= 12; x++) {
            for (int y = -12; y <= 12; y++) {
                double r = Math.hypot(x, y);
                if (r <= 12 && r > 8 || (r <= 8 && Math.abs(((Math.toDegrees(Math.atan2(y, x)) % 30) + 30) % 30 - 15) > 12)) {
                    d.set(x, 3 + 30 + y, sz + 4, r > 10.8 ? "orange_concrete" : "gold_block");
                }
            }
        }
        d.commit();
        for (int x = -8; x <= 8; x += 8) {
            for (int z = -8; z <= 8; z += 8) {
                arena.setLater(x, base + 7, z, Deco.st("light[level=15]"));
            }
        }
        for (int i = 0; i < 8; i++) { // round the hill's foot, so every step is lit
            double a = Math.PI / 4 * i;
            arena.setLater((int) Math.round(9 * Math.cos(a)), 3, (int) Math.round(9 * Math.sin(a)), Deco.st("light[level=15]"));
        }
    }

    static void ziggurat(Deco d, int x, int y, int z, int r, int levels) {
        for (int l = 0; l < levels; l++) {
            int rr = r - l;
            for (int xx = x - rr; xx <= x + rr; xx++) {
                for (int zz = z - rr; zz <= z + rr; zz++) {
                    boolean edge = xx == x - rr || xx == x + rr || zz == z - rr || zz == z + rr;
                    d.set(xx, y + l * 2, zz, edge ? "mossy_stone_bricks" : "stone_bricks");
                    d.set(xx, y + l * 2 + 1, zz, edge ? (Math.floorMod(xx + zz, 3) == 0 ? "chiseled_stone_bricks" : "mossy_stone_bricks") : "stone_bricks");
                }
            }
        }
        d.fill(x - 1, y + levels * 2, z - 1, x + 1, y + levels * 2 + 2, z + 1, "chiseled_stone_bricks");
        d.set(x, y + levels * 2 + 3, z, "gold_block");
        for (int i = 0; i < 8; i++) {
            d.set(x - r + d.rnd.nextInt(2 * r + 1), y + 1 + d.rnd.nextInt(levels * 2), z - r - 1, "vine[north=false,south=true,east=false,west=false,up=false]");
        }
    }

    static void serpent(Deco d, int x, int y, int z, int fx, int fz) {
        int hx = x + fx * 7, hz = z + fz * 7;
        d.fill(hx - 1, y - 6, hz - 1, hx + 1, y - 4, hz + 1, "prismarine_bricks");
        d.fill(hx - 1, y - 3, hz - 1, hx + 1, y - 2, hz + 1, "dark_prismarine");
        d.set(hx + fx * 2, y - 4, hz + fz * 2, "gold_block");
        d.set(hx + fx * 2, y - 2, hz + fz * 2, "gold_block");
        d.set(hx + fx, y - 3, hz + fz, "red_concrete");
    }

    static void kapok(Deco d, int x, int y, int z, int h) {
        d.cyl(x + 0.5, z + 0.5, 1.6, y, y + h, "jungle_log");
        for (int[] b : new int[][] {{-3, 0}, {3, 0}, {0, -3}, {0, 3}}) {
            d.line(x + 0.5, y + 0.5, z + 0.5, x + 0.5 + b[0], y - 0.5, z + 0.5 + b[1], "jungle_log");
        }
        d.ellipsoid(x + 0.5, y + h + 1, z + 0.5, 7, 3, 7, 0, "jungle_leaves[persistent=true]");
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10;
            int vx = (int) Math.floor(x + 0.5 + 6 * Math.cos(a)), vz = (int) Math.floor(z + 0.5 + 6 * Math.sin(a));
            for (int yy = y + h - 1; yy > y + h - 6 - (i % 3) * 2; yy--) {
                d.set(vx, yy, vz, "jungle_leaves[persistent=true]");
            }
            if (i % 3 == 0) {
                d.set(vx, y + h - 7 - (i % 3) * 2, vz, "lantern[hanging=true]");
            }
        }
    }
}
