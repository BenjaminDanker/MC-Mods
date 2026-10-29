package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Waves: the Grand Colosseum. The roofed fighting pit (x, z -half..half, walls y 1..2, roof y 6) stands at the centre of
 * a sandstone colosseum: a ring of tiered stands rises all round (seen through the gap under the pit's roof), with
 * arcaded outer walls, purple velarium awnings on gilded masts, braziers, banners, and an imperial box where the
 * Gamemaster presides as emperor. A three-block moat of open air keeps the stands away from the pit's walls.
 */
public final class ColosseumLook {
    private ColosseumLook() {
    }

    public static void build(Arena arena, int tier, int half) {
        Deco d = new Deco(arena, 16161L);
        d.keepOut(-half - 3, -4, -half - 3, half + 3, 7, half + 3);
        double r0 = half + 6, r1 = half + 28;
        // ---- the arena floor ring (sand) below the pit level, then the stands rising outward ----
        for (int x = (int) -r1 - 4; x <= r1 + 4; x++) {
            for (int z = (int) -r1 - 4; z <= r1 + 4; z++) {
                double r = Math.hypot(x + 0.5, z + 0.5);
                double a = Math.atan2(z + 0.5, x + 0.5);
                if (r > r1 + 3.5) {
                    continue;
                }
                if (r < r0) {
                    if (r > half + 3.5) {
                        d.set(x, -4, z, "smooth_sandstone|smooth_sandstone|orange_terracotta|cut_red_sandstone");
                        d.set(x, -5, z, "sandstone");
                    }
                    continue;
                }
                if (r <= r1) {
                    int step = (int) ((r - r0) / 1.4);
                    int y = -3 + step;
                    for (int yy = y - 2; yy <= y; yy++) {
                        d.set(x, yy, z, yy == y ? (step % 4 == 0 ? "smooth_sandstone" : "cut_sandstone") : "sandstone");
                    }
                    // seats: stairs facing in on every other step
                    if (step % 2 == 1) {
                        String facing = Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? "west" : "east") : (Math.sin(a) > 0 ? "north" : "south");
                        d.set(x, y + 1, z, "sandstone_stairs[facing=" + facing + "]");
                    }
                    continue;
                }
                // the outer arcade wall
                int top = (int) (-3 + (r1 - r0) / 1.4) + 6;
                for (int y = -12; y <= top; y++) {
                    int arcIndex = (int) Math.floor((a + Math.PI) / (Math.PI / 24));
                    double frac = ((a + Math.PI) / (Math.PI / 24)) - arcIndex;
                    boolean opening = (y % 8 >= 2 && y % 8 <= 5 && Math.abs(frac - 0.5) < 0.3) || (y % 8 == 6 && Math.abs(frac - 0.5) < 0.18);
                    if (opening) {
                        continue;
                    }
                    d.set(x, y, z, y % 8 == 0 ? "chiseled_sandstone" : (y == top ? "gold_block" : "smooth_sandstone"));
                }
            }
        }
        // ---- velarium masts and awnings (purple and gold) over the upper stands ----
        int top = (int) (-3 + (r1 - r0) / 1.4) + 6;
        for (int i = 0; i < 24; i++) {
            double a = Math.PI * 2 * i / 24;
            int x = (int) Math.floor(r1 * Math.cos(a)), z = (int) Math.floor(r1 * Math.sin(a));
            for (int y = top + 1; y <= top + 8; y++) {
                d.set(x, y, z, y == top + 8 ? "gold_block" : "stripped_dark_oak_log");
            }
            d.set(x, top + 9, z, "lantern");
            // a sail sloping in from the mast
            for (double t = 0; t <= 1; t += 0.05) {
                double r = r1 - t * 10;
                for (double da = -0.11; da <= 0.11; da += 0.02) {
                    int sx = (int) Math.floor(r * Math.cos(a + da)), sz = (int) Math.floor(r * Math.sin(a + da));
                    d.set(sx, top + 7 - (int) (t * 4), sz, i % 2 == 0 ? "purple_wool" : "yellow_wool");
                }
            }
        }
        // ---- braziers round the arena floor's edge ----
        for (int i = 0; i < 12; i++) {
            double a = Math.PI * 2 * i / 12 + 0.13;
            int x = (int) Math.floor((r0 - 1.2) * Math.cos(a)), z = (int) Math.floor((r0 - 1.2) * Math.sin(a));
            d.set(x, -3, z, "polished_blackstone_wall");
            d.set(x, -2, z, "gold_block");
            d.set(x, -1, z, "campfire[lit=true]");
        }
        // ---- the imperial box on the north side with the emperor (him) ----
        int bz = (int) -(r0 + 10);
        d.fill(-7, 2, bz - 6, 7, 2, bz + 3, "purple_wool");
        d.fill(-8, 1, bz - 7, 8, 1, bz + 4, "gold_block");
        d.fill(-8, 3, bz + 4, 8, 3, bz + 4, "gold_block");
        for (int x : new int[] {-8, 8}) {
            for (int y = 3; y <= 14; y++) {
                d.set(x, y, bz + 4, y % 4 == 0 ? "gold_block" : "quartz_pillar");
            }
        }
        d.fill(-9, 15, bz - 7, 9, 15, bz + 5, "purple_wool");
        d.fill(-9, 15, bz + 5, 9, 15, bz + 5, "gold_block");
        Props.statue(d, 0, 2, bz - 2, 0, 1, 12);
        for (int x = -6; x <= 6; x += 3) {
            d.set(x, 14, bz + 4, "lantern[hanging=true]");
        }
        // banners hanging on the arena wall all round
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16 + 0.2;
            int x = (int) Math.floor((r0 + 0.5) * Math.cos(a)), z = (int) Math.floor((r0 + 0.5) * Math.sin(a));
            for (int y = -3; y <= 0; y++) {
                d.set(x, y, z, i % 2 == 0 ? "purple_wool" : "red_wool");
            }
            d.set(x, 1, z, "gold_block");
        }
        // gilded crown on the pit's roof edge (above the roof, out of reach)
        for (int x = -half; x <= half; x += 2) {
            d.set(x, 8, -half, "gold_block");
            d.set(x, 8, half, "gold_block");
            d.set(-half, 8, x, "gold_block");
            d.set(half, 8, x, "gold_block");
        }
        for (int[] c : new int[][] {{-half, -half}, {half, -half}, {-half, half}, {half, half}}) {
            d.set(c[0], 9, c[1], "gold_block");
            d.set(c[0], 10, c[1], "lantern");
        }
        d.erase(0, 0, 0); // nothing at the centre
        Props.die(d, r0 + 4.5, -1, r0 + 4.5, 5, 25, 20, 10, "white_concrete", "red_concrete");
        d.commit();
        // light the stands for the night
        for (int i = 0; i < 16; i++) {
            double a = Math.PI * 2 * i / 16;
            arena.setLater((int) Math.floor((r0 + 9) * Math.cos(a)), 10, (int) Math.floor((r0 + 9) * Math.sin(a)), Deco.st("light[level=15]"));
        }
    }
}
