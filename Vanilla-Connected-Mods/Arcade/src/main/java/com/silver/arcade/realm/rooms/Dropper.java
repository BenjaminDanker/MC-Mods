package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The dropper: jump down a tall shaft past the ledges and land in the small pool at the bottom. Landing anywhere else
 * sends you back up to try again (no fall damage here); land in the water before the time runs out to clear.
 */
public final class Dropper extends BaseRoom {
    private static final int HEIGHT = 56;
    private static final int HALF = 4;
    private final Random random = new Random();

    public String id() { return "dropper"; }
    public String title() { return "The Drop"; }
    public String explain() {
        return "Jump into the shaft and steer past the ledges into the little pool at the bottom. Miss it and you start over at the top.";
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(Math.max(50, 80 - tier)); // 25 levels: a bit less time as levels climb
    }

    @Override
    public int[] bounds() {
        return new int[] {-HALF - 30, -40, -HALF - 30, HALF + 30, HEIGHT + 30, HALF + 30};
    }

    @Override
    protected void build() {
        // Walls of the shaft.
        for (int y = 0; y <= HEIGHT; y++) {
            for (int i = -HALF - 1; i <= HALF + 1; i++) {
                // Inside the lighthouse: quartz with a gold-and-sky-blue helix winding down and a lit band every 8 blocks.
                arena.set(i, y, -HALF - 1, wall(y, i + HALF + 1));
                arena.set(HALF + 1, y, i, wall(y, i + HALF + 1 + (2 * HALF + 2)));
                arena.set(-i, y, HALF + 1, wall(y, i + HALF + 1 + 2 * (2 * HALF + 2)));
                arena.set(-HALF - 1, y, -i, wall(y, i + HALF + 1 + 3 * (2 * HALF + 2)));
            }
        }
        arena.fill(-HALF, -1, -HALF, HALF, -1, HALF, Blocks.SMOOTH_QUARTZ.defaultBlockState()); // under the pool
        arena.fill(-HALF, 0, -HALF, HALF, 0, HALF, Blocks.SMOOTH_QUARTZ.defaultBlockState());
        // The pool: 3x3 at first, 2x2 from level 4, 1x1 from level 9; anywhere on the floor.
        int pool = tier >= 9 ? 1 : tier >= 4 ? 2 : 3;
        int px = random.nextInt(2 * HALF - pool + 2) - HALF;
        int pz = random.nextInt(2 * HALF - pool + 2) - HALF;
        // The floor: a quartz-and-sky-blue compass mosaic, with a gold ring round the pool so it shows from the top.
        for (int fx = -HALF; fx <= HALF; fx++) {
            for (int fz = -HALF; fz <= HALF; fz++) {
                int ring = Math.max(Math.max(px - fx, fx - (px + pool - 1)), Math.max(pz - fz, fz - (pz + pool - 1)));
                arena.set(fx, 0, fz, st(ring == 1 ? "gold_block" : (fx == 0 || fz == 0) ? "light_blue_concrete"
                    : Math.floorMod(fx + fz, 2) == 0 ? "smooth_quartz" : "chiseled_quartz_block"));
            }
        }
        arena.fill(px, 0, pz, px + pool - 1, 0, pz + pool - 1, Blocks.WATER.defaultBlockState());
        // Ledges: more and wider as tiers climb, never closing the shaft.
        int ledges = Math.min(20, 3 + (2 * tier) / 3);
        for (int i = 0; i < ledges; i++) {
            int y = 6 + random.nextInt(HEIGHT - 12);
            int w = 1 + random.nextInt(Math.min(5, 2 + tier / 4));
            int x = random.nextInt(2 * HALF + 1 - w) - HALF;
            int z = random.nextInt(2 * HALF + 1 - w) - HALF;
            // red-and-white striped like a buoy (same full blocks)
            for (int lx = x; lx < x + w; lx++) {
                for (int lz = z; lz < z + w; lz++) {
                    arena.set(lx, y, lz, st(Math.floorMod(lx + lz, 2) == 0 ? "red_concrete" : "white_concrete"));
                }
            }
        }
        // The top: the start ledge, level with the rim of the shaft.
        for (int tx = -HALF - 3; tx <= HALF + 3; tx++) {
            for (int tz = -HALF - 3; tz <= -HALF - 2; tz++) {
                arena.set(tx, HEIGHT, tz, st(Math.abs(tx) == HALF + 3 || Math.floorMod(tx, 3) == 0 ? "gold_block" : "quartz_bricks"));
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.LighthouseLook.build(arena, tier, HALF, HEIGHT));
        players.values().forEach(this::top);
    }

    /** The shaft wall at height y and position t around it (0 .. perimeter): solid either way. */
    private static net.minecraft.world.level.block.state.BlockState wall(int y, int t) {
        if (y % 8 == 0) {
            return Blocks.SEA_LANTERN.defaultBlockState();
        }
        int k = Math.floorMod(t + y * 2, 20);
        if (k < 2) {
            return Blocks.GOLD_BLOCK.defaultBlockState();
        }
        if (k < 4) {
            return Blocks.CONCRETE.lightBlue().defaultBlockState();
        }
        return y % 8 == 1 || y % 8 == 7 ? Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState() : Blocks.QUARTZ_BRICKS.defaultBlockState();
    }

    private void top(ServerPlayer player) {
        player.resetFallDistance();
        arena.put(player, 0.5, HEIGHT + 1, -HALF - 2.5, 0F);
    }

    @Override
    public void tick(long tickInRoom) {
        for (ServerPlayer player : undecided()) {
            player.resetFallDistance(); // no fall damage in the drop
            // isInWater can lag a tick behind a fast landing; the pool block at the feet counts too
            if (player.isInWater() || player.level().getFluidState(player.blockPosition()).is(net.minecraft.tags.FluidTags.WATER)) {
                clear(player);
                Gamemaster.bar(player, "Splash! Cleared.");
                top(player);
                continue;
            }
            if (player.onGround() && heightOf(player) < HEIGHT - 2) {
                Gamemaster.bar(player, "Missed. Again!");
                top(player);
            }
        }
    }

    @Override
    public void fell(ServerPlayer player) {
        top(player);
    }
}
