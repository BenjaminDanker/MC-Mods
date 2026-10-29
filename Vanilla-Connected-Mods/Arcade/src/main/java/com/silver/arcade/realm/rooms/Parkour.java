package com.silver.arcade.realm.rooms;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A generated jump course over the void, gold block at the end. Gaps widen, heights vary and footholds shrink (blocks,
 * then slabs and walls, then single fences) as tiers climb. A checkpoint every eight jumps.
 */
public final class Parkour extends BaseRoom {
    private final Random random = new Random();
    private final Map<UUID, int[]> checkpoint = new HashMap<>();
    private int[][] jumps;
    private BlockPos finish;

    public String id() { return "parkour"; }
    public String title() { return "Parkour"; }
    public String explain() {
        return "Jump from foothold to foothold to the gold block. Fall and you go back to the last emerald checkpoint. "
            + "Mind the time.";
    }

    int count() { return Math.min(60, 10 + tier * 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(20 + count() * 3.0);
    }

    private BlockState foothold() {
        int roll = random.nextInt(10);
        if (tier >= 8 && roll < 3) {
            return Blocks.OAK_FENCE.defaultBlockState();
        }
        if (tier >= 5 && roll < 5) {
            return Blocks.COBBLESTONE_WALL.defaultBlockState();
        }
        if (tier >= 3 && roll < 6) {
            return Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        }
        return Blocks.STONE_BRICKS.defaultBlockState();
    }

    /** How high a player stands on this foothold, above the block's own y. */
    static double heightOf(BlockState block) {
        if (block.is(Blocks.SMOOTH_STONE_SLAB)) {
            return 0.5;
        }
        if (block.is(Blocks.OAK_FENCE) || block.is(Blocks.COBBLESTONE_WALL)) {
            return 1.5;
        }
        return 1.0;
    }

    /** Spectator box, set once the course is laid out. */
    private int[] box = super.bounds();

    @Override
    public int[] bounds() {
        return box;
    }

    @Override
    protected void build() {
        arena.fill(-2, 0, -2, 2, 0, 2, Blocks.STONE_BRICKS.defaultBlockState());
        int n = count();
        jumps = new int[n][3];
        int x = 0;
        int y = 0;
        int z = 3;
        double top = 1.0; // the start platform's walking height (block top above y)
        for (int i = 0; i < n; i++) {
            int gap = 1 + random.nextInt(Math.min(3, 1 + tier / 3));
            boolean check = i > 0 && i % 8 == 0;
            BlockState block = check ? Blocks.EMERALD_BLOCK.defaultBlockState() : foothold();
            double height = heightOf(block);
            int rise = random.nextInt(3) - 1;
            // Reachable jumps only: at most one block higher than where you stand, never higher on the longest gaps,
            // and long gaps get a little shorter when the foothold is a thin post.
            double maxUp = gap >= 3 ? 0.0 : 1.0;
            int candidate = Math.max(-4, Math.min(12, y + rise));
            while (candidate + height - top > maxUp) {
                candidate--;
            }
            if (gap >= 3 && height > 1.0) {
                gap = 2;
            }
            y = candidate;
            z += gap + 1;
            x += random.nextInt(3) - 1;
            arena.set(x, y, z, block);
            top = y + height;
            jumps[i] = new int[] {x, y, z};
        }
        arena.fill(x - 1, y, z + 2, x + 1, y, z + 4, Blocks.GOLD_BLOCK.defaultBlockState());
        finish = arena.at(x, y, z + 3);
        int minX = 0;
        int maxX = 0;
        for (int[] jump : jumps) {
            minX = Math.min(minX, jump[0]);
            maxX = Math.max(maxX, jump[0]);
        }
        int[] finishAt = {x, y, z + 3};
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.SkyCircusLook.build(arena, tier, jumps, finishAt));
        box = new int[] {minX - 70, -24, -34, maxX + 70, 60, Math.min(z + 50, 200)};
        for (ServerPlayer player : players.values()) {
            checkpoint.put(player.getUUID(), new int[] {0, 0, 0});
            arena.put(player, 0.5, 1, 0.5, 0F);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        for (ServerPlayer player : players.values()) {
            if (decided(player.getUUID())) {
                continue;
            }
            BlockPos below = player.blockPosition().below();
            if (arena.level().getBlockState(below).is(Blocks.GOLD_BLOCK) && Math.abs(below.getZ() - finish.getZ()) <= 1) {
                clear(player);
            } else if (arena.level().getBlockState(below).is(Blocks.EMERALD_BLOCK)) {
                checkpoint.put(player.getUUID(), new int[] {below.getX() - arena.origin().getX(), below.getY() - arena.origin().getY(),
                    below.getZ() - arena.origin().getZ()});
            }
            if (player.getY() < arena.origin().getY() - 8) {
                fell(player);
            }
        }
    }

    @Override
    public void fell(ServerPlayer player) {
        int[] c = checkpoint.getOrDefault(player.getUUID(), new int[] {0, 0, 0});
        arena.put(player, c[0] + 0.5, c[1] + 1, c[2] + 0.5, 0F);
    }
}
