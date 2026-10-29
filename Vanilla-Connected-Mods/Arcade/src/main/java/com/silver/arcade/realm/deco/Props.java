package com.silver.arcade.realm.deco;

/** Shared show-off pieces: the Gamemaster's statues, giant dice, chandeliers, lamp posts, banners. */
public final class Props {
    private Props() {
    }

    /**
     * A block statue of the Gamemaster: purple-and-gold robe, pale face, gold eyes and circlet, a die on the chest, the
     * right hand raised with a golden die. Base centre (bx, by, bz) is the block he stands on top of; (fx, fz) the way
     * he faces; h his height in blocks (about 16 at full size; 10 still reads).
     */
    public static void statue(Deco d, int bx, int by, int bz, int fx, int fz, double h) {
        statue(d, bx, by, bz, fx, fz, h, "purple_concrete", "gold_block");
    }

    public static void statue(Deco d, int bx, int by, int bz, int fx, int fz, double h, String robe, String gold) {
        double k = h / 16.0;
        int rx = -fz, rz = fx; // his right hand side
        int span = (int) Math.ceil(4.5 * k) + 1;
        for (int ox = -span; ox <= span; ox++) {
            for (int oz = -span; oz <= span; oz++) {
                for (int oy = 0; oy <= (int) Math.ceil(19 * k); oy++) {
                    // local: a = towards his right, c = forward, u = up (block centres)
                    double a = (ox * rx + oz * rz), c = (ox * fx + oz * fz), u = (oy + 0.5) / k;
                    a /= k;
                    c /= k;
                    String s = statuePart(a, u, c, robe, gold);
                    if (s != null) {
                        d.set(bx + ox, by + 1 + oy, bz + oz, s);
                    }
                }
            }
        }
    }

    private static String statuePart(double a, double u, double c, String robe, String gold) {
        double aa = Math.abs(a);
        // robe: a flared cone to the hips
        if (u < 8.2) {
            double hw = 3.3 - 0.13 * u, hd = 2.3 - 0.09 * u;
            if ((a / hw) * (a / hw) + (c / hd) * (c / hd) <= 1) {
                if (u < 0.9 || (aa < 0.7 && c > 0)) {
                    return gold;
                }
                return robe;
            }
            return null;
        }
        // torso
        if (u < 11.6) {
            if (aa <= 2.3 && Math.abs(c) <= 1.4) {
                if (u < 8.9) {
                    return gold; // belt
                }
                if (c > 0.6 && aa <= 1.1 && u >= 9.2 && u <= 11.2) {
                    // the die on his chest: white with a centre pip
                    return (aa < 0.45 && Math.abs(u - 10.2) < 0.45) ? "black_concrete" : "white_concrete";
                }
                return robe;
            }
        }
        // left arm hangs, right arm raised high
        if (a < -2.2 && a > -3.4 && Math.abs(c) <= 0.8 && u >= 6.8 && u < 11.6) {
            return u < 7.8 ? "smooth_quartz" : robe;
        }
        if (a > 2.2 && a < 3.4 && Math.abs(c) <= 0.8 && u >= 10.0 && u < 17.4) {
            return u > 16.4 ? "smooth_quartz" : robe;
        }
        if (a > 1.6 && a < 4.0 && Math.abs(c) <= 1.2 && u >= 17.4 && u < 19.8) {
            return gold; // the golden die he holds up
        }
        // head with hair behind and on top, gold eyes, circlet with a white stone
        if (u >= 11.6 && u < 15.0 && aa <= 1.6 && Math.abs(c) <= 1.6) {
            if (c < -0.8 || u > 14.3) {
                return "polished_blackstone";
            }
            if (c > 0.8 && u >= 12.9 && u < 13.7 && aa > 0.35 && aa < 1.15) {
                return gold;
            }
            return "smooth_quartz";
        }
        if (u >= 15.0 && u < 15.9 && aa <= 1.9 && Math.abs(c) <= 1.9 && (aa > 1.2 || Math.abs(c) > 1.2)) {
            return (c > 1.2 && aa < 0.5) ? "white_concrete" : gold;
        }
        return null;
    }

    /** A statue on a stepped plinth (quartz with a gold band), plinth top at by + steps. */
    public static void statueOnPlinth(Deco d, int bx, int by, int bz, int fx, int fz, double h, int steps) {
        for (int i = 0; i < steps; i++) {
            int r = (int) Math.ceil(h / 16.0 * 3.4) + steps - i;
            d.fill(bx - r, by + i, bz - r, bx + r, by + i, bz + r, i == steps - 1 ? "gold_block" : "chiseled_quartz_block");
        }
        statue(d, bx, by + steps - 1, bz, fx, fz, h);
    }

    /**
     * A die of edge n (5 or more reads best) centred at (cx, cy, cz), turned by yaw/pitch/roll in degrees. Faces show
     * 1..6 (opposites add to 7).
     */
    public static void die(Deco d, double cx, double cy, double cz, double n, double yaw, double pitch, double roll, String body, String pip) {
        double[][] m = rot(yaw, pitch, roll);
        double half = n / 2.0;
        int r = (int) Math.ceil(half * 1.75) + 1;
        for (int x = (int) Math.floor(cx - r); x <= cx + r; x++) {
            for (int y = (int) Math.floor(cy - r); y <= cy + r; y++) {
                for (int z = (int) Math.floor(cz - r); z <= cz + r; z++) {
                    double px = x + 0.5 - cx, py = y + 0.5 - cy, pz = z + 0.5 - cz;
                    // into the die's frame (transpose of m)
                    double lx = m[0][0] * px + m[1][0] * py + m[2][0] * pz;
                    double ly = m[0][1] * px + m[1][1] * py + m[2][1] * pz;
                    double lz = m[0][2] * px + m[1][2] * py + m[2][2] * pz;
                    if (Math.abs(lx) > half || Math.abs(ly) > half || Math.abs(lz) > half) {
                        continue;
                    }
                    double ax = Math.abs(lx), ay = Math.abs(ly), az = Math.abs(lz);
                    int face;
                    double u, v;
                    if (ax >= ay && ax >= az) {
                        face = lx > 0 ? 1 : 6;
                        u = ly;
                        v = lz;
                    } else if (ay >= az) {
                        face = ly > 0 ? 5 : 2;
                        u = lx;
                        v = lz;
                    } else {
                        face = lz > 0 ? 3 : 4;
                        u = lx;
                        v = ly;
                    }
                    double depth = half - Math.max(ax, Math.max(ay, az));
                    boolean surface = depth < 1.0;
                    d.set(x, y, z, surface && pipAt(face, u / half, v / half) ? pip : body);
                }
            }
        }
    }

    private static boolean pipAt(int face, double u, double v) {
        double[][] spots = switch (face) {
            case 1 -> new double[][] {{0, 0}};
            case 2 -> new double[][] {{-0.5, -0.5}, {0.5, 0.5}};
            case 3 -> new double[][] {{-0.5, -0.5}, {0, 0}, {0.5, 0.5}};
            case 4 -> new double[][] {{-0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}};
            case 5 -> new double[][] {{-0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {0, 0}};
            default -> new double[][] {{-0.5, -0.5}, {0.5, 0.5}, {-0.5, 0.5}, {0.5, -0.5}, {-0.5, 0}, {0.5, 0}};
        };
        for (double[] s : spots) {
            if (Math.abs(u - s[0]) < 0.2 && Math.abs(v - s[1]) < 0.2) {
                return true;
            }
        }
        return false;
    }

    static double[][] rot(double yaw, double pitch, double roll) {
        double a = Math.toRadians(yaw), b = Math.toRadians(pitch), c = Math.toRadians(roll);
        double[][] ry = {{Math.cos(a), 0, Math.sin(a)}, {0, 1, 0}, {-Math.sin(a), 0, Math.cos(a)}};
        double[][] rx = {{1, 0, 0}, {0, Math.cos(b), -Math.sin(b)}, {0, Math.sin(b), Math.cos(b)}};
        double[][] rz = {{Math.cos(c), -Math.sin(c), 0}, {Math.sin(c), Math.cos(c), 0}, {0, 0, 1}};
        return mul(ry, mul(rx, rz));
    }

    private static double[][] mul(double[][] p, double[][] q) {
        double[][] o = new double[3][3];
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                for (int k = 0; k < 3; k++) {
                    o[i][j] += p[i][k] * q[k][j];
                }
            }
        }
        return o;
    }

    /**
     * A gaudy chandelier hanging from (x, top, z): a chain down to a gold hub at y, arms of chain out to radius r with
     * gold caps carrying lit candles and a hanging lantern under each.
     */
    public static void chandelier(Deco d, int x, int top, int z, int y, int r, int arms) {
        for (int yy = y + 1; yy <= top; yy++) {
            d.set(x, yy, z, "iron_chain[axis=y]");
        }
        d.set(x, y, z, "gold_block");
        d.set(x, y - 1, z, "lantern[hanging=true]");
        for (int i = 0; i < arms; i++) {
            double ang = 2 * Math.PI * i / arms;
            int ex = x + (int) Math.round(r * Math.cos(ang)), ez = z + (int) Math.round(r * Math.sin(ang));
            d.line(x + 0.5, y + 0.5, z + 0.5, ex + 0.5, y + 0.5, ez + 0.5, "gold_block");
            d.set(x, y, z, "gold_block");
            d.set(ex, y + 1, ez, "candle[candles=4,lit=true]");
            d.set(ex, y - 1, ez, "lantern[hanging=true]");
        }
    }

    /** A lamp post: a wall column of the given height with a lantern on top. */
    public static void post(Deco d, int x, int y, int z, int height, String wall, String lamp) {
        for (int i = 0; i < height; i++) {
            d.set(x, y + i, z, wall);
        }
        d.set(x, y + height, z, lamp);
    }
}
