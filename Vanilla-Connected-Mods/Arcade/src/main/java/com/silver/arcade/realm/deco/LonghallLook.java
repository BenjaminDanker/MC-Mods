package com.silver.arcade.realm.deco;

import com.silver.arcade.realm.Arena;

/**
 * Simon Says: the Jarl's Longhall. A Nordic timber hall on a stone footing: two rows of carved pillars, a steep
 * roof with crossed gable horns, a long stone hearth, hanging shields. Above each of the four pads (x -2, 0, 2, 4 on
 * z 4) hangs a great shield in that pad's colour, and behind them the Gamemaster sits enthroned as jarl. The players
 * stand at z 0.5 facing +z; nothing stands between them and the pads.
 */
public final class LonghallLook {
    private LonghallLook() {
    }

    public static void build(Arena arena, int tier) {
        Deco d = new Deco(arena, 8081L);
        d.keepOut(-3, 0, -2, 4, 0, 4);
        d.keepOut(-3, 1, -2, 5, 4, 4);
        int x0 = -12, x1 = 13, z0 = -16, z1 = 20, wallTop = 7;
        int cx2 = x0 + x1; // twice the centre
        // ---- floor: stone flags with a timber walk ----
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                d.set(x, 0, z, edge ? "cobblestone" : (Math.abs(2 * x - cx2) <= 9 ? "spruce_planks" : "stone_bricks|mossy_stone_bricks|stone_bricks|cracked_stone_bricks"));
                d.set(x, -1, z, "cobblestone");
            }
        }
        // ---- walls: cobble footing, vertical spruce, log frame ----
        for (int y = 1; y <= wallTop; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z : new int[] {z0, z1}) {
                    d.set(x, y, z, y <= 1 ? "cobblestone" : Math.floorMod(x - x0, 5) == 0 ? "stripped_dark_oak_log" : "spruce_planks");
                }
            }
            for (int z = z0; z <= z1; z++) {
                for (int x : new int[] {x0, x1}) {
                    d.set(x, y, z, y <= 1 ? "cobblestone" : Math.floorMod(z - z0, 4) == 0 ? "stripped_dark_oak_log" : "spruce_planks");
                }
            }
        }
        // shields along the side walls with lanterns between
        String[] colours = {"red", "blue", "lime", "yellow", "white", "black"};
        for (int z = z0 + 2; z <= z1 - 2; z += 4) {
            String c = colours[Math.floorMod(z, colours.length)];
            for (int x : new int[] {x0 + 1, x1 - 1}) {
                shield(d, x, 4, z, c, x < 0 ? "x+" : "x-");
                d.set(x, 2, z + 2, "lantern");
                d.set(x, 1, z + 2, "spruce_fence");
            }
        }
        // ---- pillars: two rows of carved posts holding a gallery beam ----
        for (int z = z0 + 4; z <= z1 - 4; z += 4) {
            for (int x : new int[] {x0 + 4, x1 - 4}) {
                if (z >= -3 && z <= 5 && x > -4 && x < 6) {
                    continue;
                }
                for (int y = 1; y <= wallTop + 3; y++) {
                    d.set(x, y, z, y % 4 == 2 ? "chiseled_tuff_bricks" : "stripped_spruce_log");
                }
                d.set(x, wallTop + 4, z, "gold_block");
                d.set(x + (x < 0 ? 1 : -1), 4, z, "lantern");
                d.set(x + (x < 0 ? 1 : -1), 3, z, "spruce_trapdoor[facing=" + (x < 0 ? "east" : "west") + ",half=top,open=false]");
            }
        }
        // ---- roof: steep pitch along z, dark oak shingles with a gold ridge ----
        int half = (x1 - x0) / 2 + 2;
        for (int z = z0 - 1; z <= z1 + 1; z++) {
            for (int i = 0; i <= half; i++) {
                int y = wallTop + 1 + i;
                int xl = x0 - 1 + i, xr = x1 + 1 - i;
                if (xl > xr) {
                    break;
                }
                d.set(xl, y, z, "dark_oak_stairs[facing=east]");
                d.set(xr, y, z, "dark_oak_stairs[facing=west]");
                if (xl + 1 <= xr - 1) {
                    d.set(xl + 1, y, z, "dark_oak_planks");
                    d.set(xr - 1, y, z, "dark_oak_planks");
                }
                if (xr - xl <= 2) {
                    d.fill(xl, y, z, xr, y, z, "gold_block");
                }
            }
        }
        // gable ends filled, with crossed horns over the peak
        for (int z : new int[] {z0, z1}) {
            for (int i = 0; i <= half; i++) {
                int y = wallTop + 1 + i;
                for (int x = x0 + i; x <= x1 - i; x++) {
                    d.set(x, y, z, Math.floorMod(x, 3) == 0 ? "stripped_dark_oak_log" : "spruce_planks");
                }
            }
            int peak = wallTop + 1 + (x1 - x0) / 2;
            for (int i = 0; i < 6; i++) {
                d.set((cx2 / 2) - 1 - i, peak + i, z, "dark_oak_log[axis=x]");
                d.set((cx2 / 2) + 1 + i, peak + i, z, "dark_oak_log[axis=x]");
            }
            d.set((cx2 / 2) - 6, peak + 6, z, "gold_block");
            d.set((cx2 / 2) + 6, peak + 6, z, "gold_block");
        }
        // cross beams with hanging lanterns
        for (int z = z0 + 4; z <= z1 - 4; z += 4) {
            for (int x = x0 + 1; x <= x1 - 1; x++) {
                d.set(x, wallTop + 3, z, "stripped_dark_oak_log[axis=x]");
            }
            for (int x : new int[] {x0 + 7, x1 - 7}) {
                d.set(x, wallTop + 2, z, "iron_chain[axis=y]");
                d.set(x, wallTop + 1, z, "lantern[hanging=true]");
            }
        }
        // ---- the four great shields above the pads ----
        String[] pad = {"red", "blue", "lime", "yellow"};
        for (int i = 0; i < 4; i++) {
            int x = -2 + i * 2;
            for (int y = 5; y <= 7; y++) {
                d.set(x, y, 5, pad[i] + "_concrete");
            }
            d.set(x, 8, 5, "gold_block");
            d.set(x, 9, 5, "lantern");
        }
        d.fill(-4, 1, 5, 6, 3, 5, "dark_oak_planks");
        d.fill(-4, 4, 5, 6, 4, 5, "gold_block");
        for (int x = -3; x <= 5; x += 2) {
            d.set(x, 5, 5, "dark_oak_fence");
        }
        // ---- the jarl's throne behind, with the Gamemaster on it (a statue), on a raised dais ----
        d.fill(-6, 1, 9, 7, 1, 17, "polished_deepslate");
        d.fill(-4, 2, 11, 5, 2, 17, "gold_block");
        Props.statue(d, 0, 2, 14, 0, -1, 14);
        for (int y = 3; y <= 14; y++) {
            for (int x = -4; x <= 5; x++) {
                if (y > 11 && Math.abs(2 * x - 1) > 9 - (y - 11) * 2) {
                    continue;
                }
                d.set(x, y, 17, x == -4 || x == 5 || y == 3 || y >= 13 ? "gold_block" : "red_wool");
            }
        }
        for (int x : new int[] {-5, 6}) {
            for (int y = 2; y <= 9; y++) {
                d.set(x, y, 13, "dark_oak_log");
            }
            d.set(x, 10, 13, "gold_block");
            d.set(x, 11, 13, "campfire[lit=true]");
        }
        // ---- the long hearth behind the players ----
        d.fill(-2, 1, -12, 3, 1, -6, "stone_bricks");
        d.fill(-1, 1, -11, 2, 1, -7, "magma_block");
        for (int z = -11; z <= -7; z += 2) {
            d.set(0, 2, z, "campfire[lit=true]");
            d.set(1, 2, z + 1, "campfire[lit=true]");
        }
        // benches along the hearth
        for (int z = -12; z <= -6; z++) {
            d.set(-5, 1, z, "spruce_stairs[facing=east]");
            d.set(6, 1, z, "spruce_stairs[facing=west]");
        }
        // banners on the gable wall behind the throne
        for (int x = -10; x <= 11; x += 3) {
            if (x > -6 && x < 7) {
                continue;
            }
            d.set(x, 6, z1 - 1, "purple_wall_banner[facing=north]");
            d.set(x, 7, z1 - 1, "gold_block");
        }
        Props.die(d, -9.5, 2.5, 14.5, 4, 25, 0, 0, "white_concrete", "red_concrete");
        Props.die(d, 10.5, 2.0, -12.5, 3, -10, 0, 0, "white_concrete", "black_concrete");
        d.commit();
    }

    /** A round 3x3 wall shield (wool face, gold boss) facing +x ("x+") or -x ("x-"). */
    private static void shield(Deco d, int x, int y, int z, String colour, String facing) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dz = -1; dz <= 1; dz++) {
                d.set(x, y + dy, z + dz, dy == 0 && dz == 0 ? "gold_block" : colour + "_wool");
            }
        }
    }

    /** A big round shield (radius 2.5) on z, centred at x, y: coloured face, gold rim and boss. */
    private static void bigShield(Deco d, int cx, int cy, int z, String colour) {
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int y = cy - 3; y <= cy + 3; y++) {
                double r = Math.hypot(x - cx, y - cy);
                if (r > 1.6 && Math.abs(x - cx) > 1) {
                    continue; // shields are 3 wide so neighbours don't merge
                }
                if (r <= 3.2) {
                    d.set(x, y, z, r > 2.6 ? "gold_block" : r < 0.6 ? "gold_block" : colour + "_concrete");
                }
            }
        }
    }
}
