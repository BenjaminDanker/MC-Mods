package com.silver.arcade.realm.rooms;

import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** A fresh maze each time: find the gold. Bigger every tier; from tier 5 it is dark and you get no map from above. */
public final class Maze extends BaseRoom {
    private final Random random = new Random();
    private int cells;
    private BlockPos exit;

    public String id() { return "maze"; }
    public String title() { return "The Maze"; }
    public String explain() {
        return "Somewhere in here is a gold block. Stand on it. Together or alone, everyone has to reach it before time runs out.";
    }

    int cells() { return Math.min(30, 4 + tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        int c = cells();
        return seconds(30 + c * c * 0.6);
    }

    @Override
    protected void build() {
        cells = cells();
        boolean[][] wall = MazeGen.generate(cells, cells, random);
        int size = wall.length;
        BlockState floor = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState stone = tier >= 5 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        arena.fill(0, 0, 0, size - 1, 0, size - 1, floor);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (wall[x][z]) {
                    arena.fill(x, 1, z, x, 3, z, stone);
                } else if (tier < 5 && (x + z) % 8 == 1) {
                    arena.set(x, 0, z, Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
        // A roof keeps anyone from walking on the walls.
        arena.fill(0, 4, 0, size - 1, 4, size - 1, Blocks.BARRIER.defaultBlockState());
        exit = arena.at(size - 2, 0, size - 2);
        arena.set(size - 2, 0, size - 2, Blocks.GOLD_BLOCK.defaultBlockState());
        for (ServerPlayer player : players.values()) {
            arena.put(player, 1.5, 1, 1.5, -45F);
            if (tier >= 5) {
                player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, timeLimitTicks(tier), 0, false, false));
            }
        }
    }

    @Override
    public void tick(long tickInRoom) {
        for (ServerPlayer player : players.values()) {
            if (!decided(player.getUUID()) && player.blockPosition().below().equals(exit)) {
                clear(player);
                player.removeEffect(MobEffects.DARKNESS);
            }
        }
    }

    @Override
    public void fell(ServerPlayer player) {
        arena.put(player, 1.5, 1, 1.5, -45F);
    }

    @Override
    public java.util.Set<java.util.UUID> cleared() {
        for (ServerPlayer player : players.values()) {
            player.removeEffect(MobEffects.DARKNESS);
        }
        return super.cleared();
    }
}
