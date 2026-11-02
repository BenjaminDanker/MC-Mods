package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Mental Math: the Clockwork Observatory. A round copper hall under a gold-ribbed dome studded with lit copper bulbs,
 * open at the top to the sky; a golden orrery turns above the calcite dais (x -4..4, z -4..4), a great clock faces
 * the players (+z), giant abacuses line the sides and a compass rose of gold and copper spreads over the floor.
 */
public final class ObservatoryLook {
    private ObservatoryLook() {
    }

    static final double CX = 0.5, CZ = 0.5;
    static final int R = 19, WALL = 12;

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 5051L);
        d.keepOut(-4, 1, -4, 4, 4, 4);
        // ---- floor: compass rose ----
        for (int x = -R; x <= R; x++) {
            for (int z = -R; z <= R; z++) {
                double r = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);
                if (r > R + 0.5) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(z + 0.5 - CZ, x + 0.5 - CX));
                double ray = Math.abs(((ang % 45) + 45) % 45 - 22.5); // 22.5 at a ray's centre line
                String s;
                if (r < 6.5) {
                    s = "polished_deepslate";
                } else if (Math.abs(r - 7) < 0.6 || Math.abs(r - 15) < 0.6) {
                    s = "waxed_cut_copper";
                } else if (ray > 22.5 - 120 / r && r < 15) {
                    s = Math.floorMod((int) Math.round(ang / 45), 2) == 0 ? "gold_block" : "waxed_exposed_copper";
                } else if (Math.abs(r - 11) < 0.5 && Math.floorMod((int) Math.round(ang / 15), 2) == 0) {
                    s = "pearlescent_froglight";
                } else {
                    s = r < 15 ? "polished_deepslate" : "deepslate_tiles";
                }
                d.set(x, 0, z, s);
            }
        }
        // ---- walls with copper pilasters, lit bulbs and gold capitals ----
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);
                if (r <= R + 0.5 || r > R + 1.6) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(z + 0.5 - CZ, x + 0.5 - CX));
                boolean pilaster = Math.floorMod((int) Math.floor(ang / 15.0 + 0.25), 2) == 0 && ((ang / 15.0 + 0.25) % 1 + 1) % 1 < 0.33;
                for (int y = 0; y <= WALL; y++) {
                    String s;
                    if (y == 0) {
                        s = "deepslate_tiles";
                    } else if (y == WALL || y == 1) {
                        s = "gold_block";
                    } else if (pilaster) {
                        s = y == 6 ? "waxed_copper_bulb[lit=true]" : "waxed_copper_block";
                    } else {
                        s = y % 4 == 0 ? "waxed_cut_copper" : "waxed_oxidized_cut_copper";
                    }
                    d.set(x, y, z, s);
                }
            }
        }
        // ---- the dome: gold ribs, copper panels studded with lit bulbs, open oculus ----
        for (int x = -R - 1; x <= R + 1; x++) {
            for (int z = -R - 1; z <= R + 1; z++) {
                double r = Math.hypot(x + 0.5 - CX, z + 0.5 - CZ);
                if (r > R + 1.5) {
                    continue;
                }
                double ang = Math.toDegrees(Math.atan2(z + 0.5 - CZ, x + 0.5 - CX));
                double rib = Math.abs(((ang % 30) + 30) % 30 - 15);
                for (int y = WALL + 1; y <= WALL + R + 1; y++) {
                    double rr = Math.sqrt(r * r + (y - WALL) * (y - WALL) * 1.0);
                    if (rr < R || rr > R + 1.4 || r < 4.5) {
                        continue;
                    }
                    String s;
                    if (rib > 15 - 22 / Math.max(r, 3)) {
                        s = "gold_block";
                    } else if (Math.floorMod(y, 4) == 1 && Math.floorMod((int) Math.round(ang / 7.5), 2) == 0) {
                        s = "waxed_copper_bulb[lit=true]";
                    } else {
                        s = Math.floorMod(y, 4) == 3 ? "waxed_cut_copper" : "waxed_exposed_cut_copper";
                    }
                    d.set(x, y, z, s);
                }
            }
        }
        d.tube(CX, CZ, 5.6, 4.4, WALL + R - 1, WALL + R, "gold_block"); // the oculus rim
        // ---- the orrery: a golden sun with planets on gold arms, hung from the oculus rim ----
        int sy = WALL + 6;
        d.ellipsoid(CX, sy + 0.5, CZ, 2.4, 2.4, 2.4, 0, "shroomlight");
        d.ellipsoid(CX, sy + 0.5, CZ, 2.9, 0.6, 2.9, 0, "gold_block");
        for (int y = sy + 3; y <= WALL + R - 2; y++) {
            d.set(0, y, 0, "iron_chain[axis=y]");
        }
        double[][] planets = {{6, 0, -1, 1.1}, {10, 70, 1, 1.6}, {13.5, 200, 0, 2.0}, {8, 300, -2, 1.0}};
        String[] colours = {"red_terracotta", "blue_concrete", "gold_block", "lime_terracotta"};
        for (int i = 0; i < planets.length; i++) {
            double pr = planets[i][0], pa = Math.toRadians(planets[i][1]);
            int py = sy + (int) planets[i][2];
            double px = CX + pr * Math.cos(pa), pz = CZ + pr * Math.sin(pa);
            d.line(CX, py + 0.5, CZ, px, py + 0.5, pz, "gold_block");
            d.ellipsoid(px, py + 0.5, pz, planets[i][3], planets[i][3], planets[i][3], 0, colours[i]);
            if (i == 2) { // a ringed one
                d.tube(px, pz, 3.6, 2.4, py, py, "smooth_sandstone_slab[type=top]");
            }
            if (i == 1) { // with a moon
                d.ellipsoid(px + 2.5, py + 2.5, pz, 0.7, 0.7, 0.7, 0, "white_concrete");
            }
        }
        // ---- the great clock facing the players ----
        int cy = 7, cz = 15;
        for (int x = -7; x <= 7; x++) {
            for (int y = -7; y <= 7; y++) {
                double r = Math.hypot(x, y);
                if (r > 7.3) {
                    continue;
                }
                String s = r > 6.3 ? "gold_block" : "white_concrete";
                double a = Math.toDegrees(Math.atan2(y, x));
                if (r > 5.0 && r <= 6.3 && Math.abs(((a % 30) + 30) % 30 - 15) > 13.5) {
                    s = "gold_block"; // hour marks
                }
                d.set(x, cy + y, cz, s);
                d.set(x, cy + y, cz + 1, "waxed_copper_block");
            }
        }
        d.line(0.5, cy + 0.5, cz - 0.5, 0.5, cy + 5.5, cz - 0.5, "black_concrete"); // minute hand to 12
        d.line(0.5, cy + 0.5, cz - 0.5, 3.6, cy - 1.5, cz - 0.5, "black_concrete"); // hour hand towards 4
        d.set(0, cy, cz - 1, "gold_block");
        d.set(-4, cy + 3, cz - 4, "light[level=15]");
        d.set(5, cy + 3, cz - 4, "light[level=15]");
        d.set(0, cy - 4, cz - 4, "light[level=15]");
        d.fill(-8, 1, cz - 1, 8, 1, cz + 1, "waxed_cut_copper");
        d.fill(-8, 1, cz - 2, 8, 1, cz - 2, "waxed_cut_copper_stairs[facing=south]");
        // ---- abacuses on the sides ----
        for (int sx : new int[] {-14, 14}) {
            int x = sx;
            d.fill(x, 1, -6, x, 1, 6, "dark_oak_planks");
            d.fill(x, 9, -6, x, 9, 6, "dark_oak_planks");
            d.fill(x, 2, -6, x, 8, -6, "dark_oak_log");
            d.fill(x, 2, 6, x, 8, 6, "dark_oak_log");
            String[] beads = {"red_concrete", "orange_concrete", "yellow_concrete", "lime_concrete", "light_blue_concrete", "purple_concrete", "magenta_concrete"};
            for (int row = 0; row < 7; row++) {
                int y = 2 + row;
                int n = 1 + d.rnd.nextInt(5);
                for (int z = -5; z <= 5; z++) {
                    boolean bead = (z >= -5 && z < -5 + n) || (z > 5 - (5 - n)) ;
                    d.set(x, y, z, bead ? beads[row] : "iron_chain[axis=z]");
                }
            }
            d.set(x, 10, -6, "gold_block");
            d.set(x, 10, 6, "gold_block");
        }
        // ---- telescope on a copper mount, aimed up through the oculus ----
        d.fill(-11, 1, -11, -9, 1, -9, "gold_block");
        d.set(-10, 2, -10, "waxed_copper_block");
        d.set(-10, 3, -10, "waxed_copper_block");
        d.line(-12.5, 3.5, -12.5, -6.5, 12.5, -6.5, "waxed_copper_block");
        d.line(-11.5, 4.5, -11.5, -6.5, 12.5, -6.5, "waxed_copper_block");
        d.set(-6, 13, -6, "gold_block");
        d.set(-13, 3, -13, "gold_block");
        // ---- lamp posts round the dais and a statue of him at the back ----
        for (int i = 0; i < 8; i++) {
            double a = Math.toRadians(22.5 + 45 * i);
            int x = (int) Math.floor(CX + 7.5 * Math.cos(a)), z = (int) Math.floor(CZ + 7.5 * Math.sin(a));
            Props.post(d, x, 1, z, 3, "waxed_copper_bars", "waxed_copper_lantern");
        }
        Props.statueOnPlinth(d, 0, 1, -14, 0, 1, 13, 2);
        Props.die(d, 12.5, 3.5, -10.5, 6, 30, 20, 10, "white_concrete", "black_concrete");
        Props.die(d, -13.5, 2.5, 9.5, 4, -20, 0, 0, "white_concrete", "red_concrete");
        d.commit();
    }
}
