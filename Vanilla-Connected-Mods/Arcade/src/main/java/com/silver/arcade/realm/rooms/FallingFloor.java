package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * The floor falls: every block you stand on turns red and drops away a moment later. Keep moving and stay up until the
 * time runs out. Everyone who is still up then clears.
 */
public final class FallingFloor extends BaseRoom {
    private static final int HALF = 8;
    /** Block (packed x,z) -> tick it drops. */
    private final Map<Long, Long> dropping = new HashMap<>();
    private int survive;

    public String id() { return "falling_floor"; }
    public String title() { return "The Floor Is Falling"; }
    public String explain() {
        return "Every block you step on turns red and falls a moment later. Keep moving, don't fall, and last until the time runs out.";
    }

    int delayTicks() { return Math.max(6, 20 - tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        survive = seconds(Math.min(60, 20 + 4 * tier));
        return survive + 10;
    }

    @Override
    protected void build() {
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                arena.set(x, 0, z, ((x + z) & 1) == 0 ? Blocks.CONCRETE.white().defaultBlockState() : Blocks.CONCRETE.lightBlue().defaultBlockState());
                // From tier 5 a second floor below catches one fall.
                if (tier >= 5) {
                    arena.set(x, -6, z, Blocks.CONCRETE.gray().defaultBlockState());
                }
            }
        }
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            double[] at = spot(i, HALF - 1);
            arena.put(list.get(i), at[0], 1, at[1] + 2, 0F);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        if (tickInRoom < 40) {
            return; // a moment to look around
        }
        int bottom = tier >= 5 ? -6 : 0;
        for (ServerPlayer player : undecided()) {
            if (heightOf(player) < bottom - 2) {
                out(player, "You fell.");
                continue;
            }
            BlockPos below = player.blockPosition().below();
            int dx = below.getX() - arena.origin().getX();
            int dy = below.getY() - arena.origin().getY();
            int dz = below.getZ() - arena.origin().getZ();
            if ((dy == 0 || dy == bottom) && Math.abs(dx) <= HALF && Math.abs(dz) <= HALF && player.onGround()) {
                long key = pack(dx, dy, dz);
                if (!dropping.containsKey(key) && !arena.level().getBlockState(below).isAir()) {
                    dropping.put(key, tickInRoom + delayTicks());
                    arena.level().setBlock(below, Blocks.CONCRETE.red().defaultBlockState(), 2);
                }
            }
        }
        for (var entry : dropping.entrySet()) {
            if (entry.getValue() <= tickInRoom) {
                long key = entry.getKey();
                arena.clearBlock(unpackX(key), unpackY(key), unpackZ(key));
                entry.setValue(Long.MAX_VALUE); // kept, so a fallen block never "drops" twice
            }
        }
        if (tickInRoom >= survive) {
            for (ServerPlayer player : undecided()) {
                clear(player);
                Gamemaster.bar(player, "Still standing!");
            }
        }
    }

    private static long pack(int x, int y, int z) {
        return ((long) (x + 512) << 32) | ((long) (y + 512) << 16) | (z + 512);
    }

    private static int unpackX(long key) { return (int) (key >>> 32) - 512; }
    private static int unpackY(long key) { return (int) ((key >>> 16) & 0xFFFF) - 512; }
    private static int unpackZ(long key) { return (int) (key & 0xFFFF) - 512; }
}
