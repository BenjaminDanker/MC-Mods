package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The floor is rising: glowing magma climbs a tall shaft, one layer at a time. Jump up the ledges that spiral round the
 * walls and reach the gold landing at the top before it catches you. Touch the magma and you're out.
 */
public final class LavaRising extends BaseRoom {
    private static final int HALF = 4;
    private final Random random = new Random();
    private final List<int[]> ledges = new ArrayList<>();
    private int top;
    private int lava = -1;
    private long nextRise = 120;

    public String id() { return "rising_floor"; }
    public String title() { return "The Rising Floor"; }
    public String explain() {
        return "Magma is rising up the shaft. Climb the ledges round the walls to the gold landing at the top before it reaches you.";
    }

    // 25 levels: a taller spiral and faster lava (2.1 s per block down to 0.6 s)
    int steps() { return Math.min(40, 12 + (3 * tier) / 2); }
    int riseTicks() { return Math.max(12, 44 - (3 * tier) / 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return 120 + (steps() + 4) * riseTicks() + 200;
    }

    @Override
    public int[] bounds() {
        return new int[] {-HALF - 6, -4, -HALF - 6, HALF + 6, steps() + 14, HALF + 6};
    }

    @Override
    protected void build() {
        // The furnace floor: polished blackstone bricks with a copper-grate cross (full blocks, no magma: the magma
        // only comes with the rising floor).
        for (int fx = -HALF; fx <= HALF; fx++) {
            for (int fz = -HALF; fz <= HALF; fz++) {
                arena.set(fx, 0, fz, st(fx == 0 || fz == 0 ? "waxed_copper_grate" : "polished_blackstone_bricks"));
            }
        }
        // Ledges walk round the walls clockwise, one block up and two along each time.
        int x = -HALF + 1;
        int z = -HALF + 1;
        int dx = 2;
        int dz = 0;
        for (int i = 1; i <= steps(); i++) {
            int nx = x + dx;
            int nz = z + dz;
            if (Math.abs(nx) > HALF - 1 || Math.abs(nz) > HALF - 1) {
                int t = dx;
                dx = -dz;
                dz = t;
                nx = x + dx;
                nz = z + dz;
            }
            x = nx;
            z = nz;
            ledges.add(new int[] {x, i, z});
            // copper ledges (same shapes: a full block or an upper slab), a waxed copper block now and then
            arena.set(x, i, z, random.nextInt(4) == 0 ? st("waxed_copper_block") : st("waxed_cut_copper_slab[type=top,waterlogged=false]"));
        }
        top = steps() + 1;
        // The landing is the next step along the spiral (one up, one gap over, like every ledge), not on top of the last
        // ledge: there it would be in the climber's face and two blocks above the ledge before.
        int[] last = ledges.get(ledges.size() - 1);
        // A full 3x3 landing whose near edge is one gap from the last ledge (centre three blocks along).
        int ux = Integer.signum(dx);
        int uz = Integer.signum(dz);
        int lx = last[0] + 3 * ux;
        int lz = last[2] + 3 * uz;
        if (Math.abs(lx) > HALF - 1 || Math.abs(lz) > HALF - 1) {
            int t = ux;
            ux = -uz;
            uz = t;
            lx = last[0] + 3 * ux;
            lz = last[2] + 3 * uz;
        }
        lx = Math.max(-HALF + 1, Math.min(HALF - 1, lx));
        lz = Math.max(-HALF + 1, Math.min(HALF - 1, lz));
        arena.fill(lx - 1, top, lz - 1, lx + 1, top, lz + 1, Blocks.GOLD_BLOCK.defaultBlockState());
        // The shaft: glass walls so the climb is seen, with blackstone bands.
        for (int y = 1; y <= top + 3; y++) {
            var wall = y % 6 == 0 ? st("waxed_cut_copper") : st("orange_stained_glass");
            for (int i = -HALF - 1; i <= HALF + 1; i++) {
                arena.set(i, y, -HALF - 1, wall);
                arena.set(i, y, HALF + 1, wall);
                arena.set(-HALF - 1, y, i, wall);
                arena.set(HALF + 1, y, i, wall);
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.FoundryLook.build(arena, tier, steps()));
        int n = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, -HALF + 1.5 + n++ * 0.5, 1, -HALF + 1.5, 0F);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        if (tickInRoom >= nextRise && lava < top - 1) {
            lava++;
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    if (arena.level().getBlockState(arena.at(x, lava, z)).isAir() || lava == 0) {
                        arena.set(x, lava, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                }
            }
            nextRise = tickInRoom + riseTicks();
        }
        for (ServerPlayer player : undecided()) {
            double h = heightOf(player);
            if (h <= lava + 1.05 && lava >= 1) {
                out(player, "The magma caught you.");
                continue;
            }
            if (h >= top + 0.9 && player.onGround()) {
                clear(player);
                Gamemaster.bar(player, "Above it all!");
            }
        }
    }
}
