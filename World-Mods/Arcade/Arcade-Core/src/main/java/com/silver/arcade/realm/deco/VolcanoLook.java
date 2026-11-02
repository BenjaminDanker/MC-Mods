package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * The Floor Is Falling: over the Volcano. The checker floor (x, z -half..half, y 0; a catch floor at y -6 from tier
 * 5) hangs over a lava lake deep in a volcano's crater. Basalt and blackstone crater walls rise all around at a
 * distance, glowing with magma veins; a gold-rimmed obsidian ring on chains holds the floor up (well away from the
 * edge), fiery "lavafalls" of orange glass pour down the walls, and a blackstone Gamemaster with gold trim towers on
 * the rim, arms raised over the pit. Nothing is within 5 blocks of the floor's edge, above, beside or under it.
 */
public final class VolcanoLook {
    private VolcanoLook() {
    }

    public static void build(Arena arena, int tier, int half) {
        Deco d = new Deco(arena, 14141L);
        int clear = half + 5;
        d.keepOut(-clear, -12, -clear, clear, 14, clear);
        int lava = -38;
        // ---- crater: a bowl of basalt/blackstone from the lava up past the floor level ----
        for (int x = -60; x <= 60; x++) {
            for (int z = -60; z <= 60; z++) {
                double r = Math.hypot(x + 0.5, z + 0.5);
                if (r > 60) {
                    continue;
                }
                double wob = 2.5 * Math.sin(x * 0.21) * Math.cos(z * 0.17) + 1.5 * Math.sin((x + z) * 0.11);
                // crater inner wall height rises with radius: lava edge at r~26, rim at r~44 up to y~+20
                int top = (int) Math.round(lava + Math.max(0, (r - 24 + wob)) * 1.55);
                top = Math.min(top, 22 + (int) wob);
                if (r < 24 + wob) {
                    // the lava lake (on a basalt floor)
                    d.set(x, lava - 1, z, "basalt[axis=y]");
                    d.set(x, lava, z, "magma_block|magma_block|magma_block|shroomlight|ochre_froglight"); // molten-looking, no fluid
                    continue;
                }
                for (int y = Math.max(lava - 1, top - 2); y <= top; y++) { // only the skin
                    int k = Math.floorMod(x * 7 + y * 13 + z * 3, 23);
                    String s = k == 0 ? "magma_block" : k < 6 ? "blackstone" : k < 9 ? "smooth_basalt" : "basalt[axis=y]";
                    if (y == top && r > 40) {
                        s = k < 5 ? "magma_block" : "blackstone";
                    }
                    d.set(x, y, z, s);
                }
            }
        }
        // ---- lavafalls (orange glass and shroomlight sheets) down the inner wall ----
        for (int i = 0; i < 6; i++) {
            double a = Math.PI * 2 * i / 6 + 0.3;
            for (int w = -1; w <= 1; w++) {
                double aa = a + w * 0.03;
                for (int y = lava + 1; y <= 14; y++) {
                    double r = 24 + (y - lava) / 1.55 - 0.6;
                    int x = (int) Math.floor(r * Math.cos(aa)), z = (int) Math.floor(r * Math.sin(aa));
                    d.set(x, y, z, (y + w) % 4 == 0 ? "shroomlight" : "orange_stained_glass");
                }
            }
        }
        // ---- the hanging ring: an obsidian-and-gold ring on chains from four arches (5 blocks clear of the floor) ----
        double ringR = clear + 2.5;
        d.tube(0.5, 0.5, ringR + 1, ringR - 0.5, -1, -1, "obsidian");
        d.tube(0.5, 0.5, ringR + 1.2, ringR + 0.3, 0, 0, "gold_block");
        for (int i = 0; i < 16; i++) {
            double a = Math.PI / 8 * i;
            int x = (int) Math.floor(0.5 + ringR * Math.cos(a)), z = (int) Math.floor(0.5 + ringR * Math.sin(a));
            d.set(x, 1, z, i % 2 == 0 ? "crying_obsidian" : "gold_block");
            d.set(x, 2, z, i % 2 == 0 ? "soul_lantern" : "lantern");
            for (int y = 3; y <= 16; y++) {
                if (i % 4 == 0) {
                    d.set(x, y, z, "iron_chain[axis=y]");
                }
            }
        }
        // chains up to four great arches over the crater
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 2 * i + Math.PI / 4;
            for (double t = 0; t <= 1.0001; t += 0.01) {
                double r = ringR + t * 30;
                double y = 17 + 10 * Math.sin(t * Math.PI) - t * 2;
                int x = (int) Math.floor(0.5 + r * Math.cos(a)), z = (int) Math.floor(0.5 + r * Math.sin(a));
                d.set(x, (int) y, z, "polished_blackstone_bricks");
                d.set(x, (int) y + 1, z, t * 100 % 10 < 1 ? "lantern" : "gold_block");
            }
        }
        // ---- the Gamemaster on the rim, cast in blackstone and gold ----
        Props.statue(d, 0, 18, 44, 0, -1, 30, "blackstone", "gold_block");
        d.fill(-8, 16, 38, 8, 18, 50, "polished_blackstone_bricks");
        d.fill(-8, 18, 38, 8, 18, 38, "gold_block");
        // braziers along the rim
        for (int i = 0; i < 12; i++) {
            double a = Math.PI / 6 * i;
            int x = (int) Math.floor(46 * Math.cos(a)), z = (int) Math.floor(46 * Math.sin(a));
            d.fill(x, 15, z, x, 22, z, "polished_blackstone_bricks");
            d.set(x, 23, z, "gold_block");
            d.set(x, 24, z, "campfire[lit=true]");
        }
        Props.die(d, -34.5, 26, -30.5, 8, 30, 20, 15, "obsidian", "gold_block");
        Props.die(d, 36.5, 20, -20.5, 6, -25, 35, 5, "obsidian", "magma_block");
        // ---- light over the floor so it reads at night (the lava lights the pit) ----
        for (int x = -6; x <= 6; x += 6) {
            for (int z = -6; z <= 6; z += 6) {
                arena.setLater(x, 6, z, Deco.st("light[level=15]"));
            }
        }
        d.commit();
    }
}
