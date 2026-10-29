package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Spleef: the Ice Palace. The snow disc (radius up to 9 at y 0) floats over a frozen chasm inside a ring of ice
 * cliffs; a crystal ice palace with blue-ice spires and gold domes rises behind it, giant snowmen with lantern eyes
 * stand guard, an aurora of stained glass ribbons hangs across the sky and a statue of the Gamemaster carved in packed
 * ice and gold looks on. A clear ring of open air (5 blocks) surrounds the disc so nobody can save themselves on the
 * scenery. No snow blocks are used (the shovels only break the floor's snow).
 */
public final class IcePalaceLook {
    private IcePalaceLook() {
    }

    public static void build(Arena arena, int tier, int radius) {
        Deco d = new Deco(arena, 17171L);
        int clear = 9 + 5;
        d.keepOut(-clear, -10, -clear, clear, 10, clear);
        // ---- ice cliff ring (from below up to a little above the disc), fringed with icicles ----
        for (int x = -46; x <= 46; x++) {
            for (int z = -46; z <= 46; z++) {
                double r = Math.hypot(x + 0.5, z + 0.5);
                double wob = 2.2 * Math.sin(Math.atan2(z, x) * 7) + 1.5 * Math.cos(x * 0.3 + z * 0.2);
                if (r < 22 + wob || r > 44) {
                    continue;
                }
                int top = (int) Math.round(-8 + (r - 22 - wob) * 0.8 + wob);
                top = Math.min(top, 6 + (int) wob);
                for (int y = top - 3; y <= top; y++) {
                    int k = Math.floorMod(x * 11 + y * 5 + z * 7, 9);
                    d.set(x, y, z, y == top ? (k < 2 ? "packed_ice" : "white_concrete") : k < 3 ? "blue_ice" : k < 6 ? "packed_ice" : "ice");
                }
                if (r < 24 + wob && Math.floorMod(x * 3 + z * 5, 4) == 0) {
                    for (int i = 1; i <= 3; i++) {
                        d.set(x, top - 3 - i, z, "pointed_dripstone[thickness=" + (i == 3 ? "tip" : "frustum") + ",vertical_direction=down]");
                    }
                }
            }
        }
        // frozen chasm floor far below with blue glow
        for (int x = -26; x <= 26; x++) {
            for (int z = -26; z <= 26; z++) {
                if (Math.hypot(x, z) < 26) {
                    d.set(x, -30, z, Math.floorMod(x * 7 + z * 3, 11) == 0 ? "sea_lantern" : "blue_ice");
                }
            }
        }
        // ---- ice spires on the ring ----
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2 * i / 10 + 0.25;
            double r = 28 + (i % 3) * 3;
            spire(d, (int) Math.round(r * Math.cos(a)), 0, (int) Math.round(r * Math.sin(a)), 14 + (i % 4) * 5);
        }
        // ---- the palace behind (+z) ----
        palace(d, 0, 4, 58);
        // ---- giant snowmen on the ring ----
        snowman(d, -30, 4, -14, 1, 1);
        snowman(d, 30, 4, -14, -1, 1);
        // ---- the ice statue of the Gamemaster on the near ring (-z), facing the disc ----
        d.fill(-6, 3, -38, 6, 5, -30, "packed_ice");
        d.fill(-6, 6, -38, 6, 6, -30, "gold_block");
        Props.statue(d, 0, 6, -34, 0, 1, 26, "packed_ice", "gold_block");
        // ---- aurora: ribbons of green/cyan/purple glass high above ----
        String[] glass = {"lime_stained_glass", "cyan_stained_glass", "light_blue_stained_glass", "magenta_stained_glass", "purple_stained_glass"};
        for (int band = 0; band < 3; band++) {
            for (int x = -70; x <= 70; x++) {
                int z = (int) Math.round(10 + band * 14 + 10 * Math.sin(x * 0.07 + band));
                int y0 = 44 + band * 6 + (int) Math.round(4 * Math.sin(x * 0.05 + band * 2));
                for (int h = 0; h < 6; h++) {
                    d.set(x, y0 + h, z, glass[Math.min(glass.length - 1, (h + band) % glass.length)]);
                }
            }
        }
        // ---- lanterns round the cliff edge and light over the disc ----
        for (int i = 0; i < 20; i++) {
            double a = Math.PI * 2 * i / 20;
            int x = (int) Math.round(25 * Math.cos(a)), z = (int) Math.round(25 * Math.sin(a));
            int top = 0;
            for (int y = 10; y >= -12; y--) {
                if (d.has(x, y, z)) {
                    top = y;
                    break;
                }
            }
            d.set(x, top + 1, z, "soul_lantern");
        }
        Props.die(d, 36.5, 12, 30.5, 6, 25, 20, 10, "white_concrete", "light_blue_concrete");
        d.commit();
        for (int x = -6; x <= 6; x += 6) {
            for (int z = -6; z <= 6; z += 6) {
                arena.setLater(x, 5, z, Deco.st("light[level=15]"));
            }
        }
    }

    static void spire(Deco d, int x, int y, int z, int h) {
        for (int i = 0; i < h; i++) {
            double r = 2.6 * (1 - (double) i / h) + 0.4;
            d.cyl(x + 0.5, z + 0.5, r, y + i, y + i, i % 4 == 3 ? "blue_ice" : "packed_ice");
        }
        d.set(x, y + h, z, "sea_lantern");
        d.set(x, y + h + 1, z, "end_rod[facing=up]");
        d.cyl(x + 0.5, z + 0.5, 3.4, y - 6, y - 1, "packed_ice");
    }

    static void palace(Deco d, int cx, int y, int cz) {
        d.fill(cx - 24, y - 6, cz - 12, cx + 24, y - 1, cz + 12, "packed_ice");
        d.fill(cx - 24, y - 1, cz - 12, cx + 24, y - 1, cz + 12, "white_concrete");
        // main hall
        for (int x = cx - 14; x <= cx + 14; x++) {
            for (int z = cz - 8; z <= cz + 8; z++) {
                boolean wall = x == cx - 14 || x == cx + 14 || z == cz - 8 || z == cz + 8;
                if (!wall) {
                    continue;
                }
                for (int yy = y; yy <= y + 16; yy++) {
                    boolean window = z == cz - 8 && Math.floorMod(x - cx, 4) == 2 && yy >= y + 4 && yy <= y + 12;
                    d.set(x, yy, z, window ? "light_blue_stained_glass" : (yy % 6 == 0 ? "blue_ice" : "packed_ice"));
                }
            }
        }
        d.fill(cx - 13, y + 1, cz - 7, cx + 13, y + 1, cz + 7, "sea_lantern");
        // gold dome
        d.ellipsoid(cx + 0.5, y + 17, cz + 0.5, 12, 11, 7.5, 1.4, "gold_block");
        d.fill(cx, y + 28, cz, cx, y + 32, cz, "gold_block");
        d.set(cx, y + 33, cz, "end_rod[facing=up]");
        // grand doorway
        for (int x = cx - 4; x <= cx + 4; x++) {
            for (int yy = y; yy <= y + 9; yy++) {
                if (Math.hypot(x - cx, Math.max(0, yy - y - 5)) <= 4) {
                    d.erase(x, yy, cz - 8);
                    d.set(x, yy, cz - 7, "light_blue_stained_glass");
                }
            }
        }
        // towers
        for (int tx : new int[] {cx - 20, cx + 20}) {
            for (int tz : new int[] {cz - 9, cz + 9}) {
                d.cyl(tx + 0.5, tz + 0.5, 3.5, y, y + 26, "packed_ice|packed_ice|blue_ice");
                d.ellipsoid(tx + 0.5, y + 28, tz + 0.5, 4.2, 5, 4.2, 0, "gold_block");
                spire(d, tx, y + 32, tz, 8);
                d.set(tx, y + 18, tz - 4, "sea_lantern");
            }
        }
    }

    static void snowman(Deco d, int x, int y, int z, int fx, int fz) {
        d.ellipsoid(x + 0.5, y + 3, z + 0.5, 4, 3.6, 4, 0, "white_concrete");
        d.ellipsoid(x + 0.5, y + 8.5, z + 0.5, 3, 2.8, 3, 0, "white_concrete");
        d.ellipsoid(x + 0.5, y + 12.5, z + 0.5, 2.3, 2.2, 2.3, 0, "white_concrete");
        // lantern eyes and a carrot nose toward the disc (-x side faces +x when fx = 1)
        int ex = x + fx * 2, ez = z + fz * 2;
        d.set(ex, y + 13, ez + fx, "jack_o_lantern[facing=" + (fx > 0 ? "east" : "west") + "]");
        d.set(ex + fz, y + 13, ez - fx, "jack_o_lantern[facing=" + (fx > 0 ? "east" : "west") + "]");
        d.set(x + fx * 3, y + 12, z + fz * 3, "orange_concrete");
        // top hat in gold and black
        d.cyl(x + 0.5, z + 0.5, 2.8, y + 14, y + 14, "black_concrete");
        d.cyl(x + 0.5, z + 0.5, 1.8, y + 15, y + 17, "black_concrete");
        d.cyl(x + 0.5, z + 0.5, 1.8, y + 15, y + 15, "gold_block");
        // coal buttons
        for (int i = 0; i < 3; i++) {
            d.set(x + fx * 3, y + 7 + i * 1, z + fz * 2, "coal_block");
        }
    }
}
