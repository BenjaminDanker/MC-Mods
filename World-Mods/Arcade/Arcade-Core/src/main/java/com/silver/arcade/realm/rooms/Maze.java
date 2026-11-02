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
    public int[] bounds() {
        int size = 2 * cells + 1;
        int reach = 18 + 2 * cells / 10;
        return new int[] {-reach, -8, -reach, size + reach, 44, size + reach};
    }

    @Override
    protected void build() {
        cells = cells();
        boolean[][] wall = MazeGen.generate(cells, cells, random);
        int size = wall.length;
        BlockState floor = Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        BlockState stone = tier >= 5 ? Blocks.DEEPSLATE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
        // Garden walks: moss and mossy brick paving, a chiseled stone at every crossing of the maze grid.
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                int k = Math.floorMod(x * 13 + z * 7, 9);
                arena.set(x, 0, z, (x % 2 == 1 && z % 2 == 1) ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                    : (k < 2 ? Blocks.MOSS_BLOCK.defaultBlockState() : k < 4 ? Blocks.STONE_BRICKS.defaultBlockState() : floor));
            }
        }
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (wall[x][z]) {
                    // Clipped-hedge look: stone courses (moss and cracks for variety) under a moss top; same solid walls.
                    int k = Math.floorMod(x * 31 + z * 17, 7);
                    BlockState low = tier >= 5 ? (k == 0 ? Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState() : stone)
                        : (k < 2 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : k == 2 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : stone);
                    arena.fill(x, 1, z, x, 2, z, low);
                    arena.set(x, 3, z, tier >= 5 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.MOSS_BLOCK.defaultBlockState());
                } else if (tier < 5 && (x + z) % 8 == 1) {
                    arena.set(x, 0, z, Blocks.SEA_LANTERN.defaultBlockState());
                }
            }
        }
        // A roof keeps anyone from walking on the walls.
        arena.fill(0, 4, 0, size - 1, 4, size - 1, Blocks.BARRIER.defaultBlockState());
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.GardenLook.build(arena, tier, size));
        exit = arena.at(size - 2, 0, size - 2);
        arena.set(size - 2, 0, size - 2, Blocks.GOLD_BLOCK.defaultBlockState());
        // The goal glows: the open cells round the gold block are lit pearlescent tiles.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gx = size - 2 + dx, gz = size - 2 + dz;
                if ((dx != 0 || dz != 0) && !wall[gx][gz]) {
                    arena.set(gx, 0, gz, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
                }
            }
        }
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
