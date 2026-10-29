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

    public String id() { return "lava_rising"; }
    public String title() { return "The Rising Floor"; }
    public String explain() {
        return "Magma is rising up the shaft. Climb the ledges round the walls to the gold landing at the top before it reaches you.";
    }

    int steps() { return Math.min(40, 14 + tier * 2); }
    int riseTicks() { return Math.max(14, 40 - tier * 2); }

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
        arena.fill(-HALF, 0, -HALF, HALF, 0, HALF, Blocks.BLACKSTONE.defaultBlockState());
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
            arena.set(x, i, z, random.nextInt(4) == 0 ? Blocks.POLISHED_BASALT.defaultBlockState() : Blocks.BLACKSTONE_SLAB.defaultBlockState()
                .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP));
        }
        top = steps() + 1;
        int[] last = ledges.get(ledges.size() - 1);
        arena.fill(last[0] - 1, top, last[2] - 1, last[0] + 1, top, last[2] + 1, Blocks.GOLD_BLOCK.defaultBlockState());
        // The shaft: glass walls so the climb is seen, with blackstone bands.
        for (int y = 1; y <= top + 3; y++) {
            var wall = y % 6 == 0 ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState() : Blocks.TINTED_GLASS.defaultBlockState();
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
