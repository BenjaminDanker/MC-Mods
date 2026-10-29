package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Treasure hunt: chests are buried under a field of sand. Hit a sand block to dig it; the hole's floor shows how close
 * the nearest treasure is (blue cold, through green and yellow, to red burning). Find them all before your digs run out.
 */
public final class TreasureHunt extends BaseRoom {
    private static final int HALF = 8;
    private static final BlockState[] HEAT = {Blocks.WOOL.blue().defaultBlockState(), Blocks.WOOL.lightBlue().defaultBlockState(),
        Blocks.WOOL.lime().defaultBlockState(), Blocks.WOOL.yellow().defaultBlockState(), Blocks.WOOL.orange().defaultBlockState(),
        Blocks.WOOL.red().defaultBlockState()};
    private final Random random = new Random();
    private final List<int[]> treasures = new ArrayList<>();
    private int found;
    private int digs;

    public String id() { return "treasure"; }
    public String title() { return "Buried Treasure"; }
    public String explain() {
        return "Treasure is buried under the sand. Hit sand to dig. The colour at the bottom of each hole says how close the nearest "
            + "treasure is: blue is cold, red is burning. Find it all before your digs run out.";
    }

    int chests() { return Math.min(4, 1 + tier / 3); }
    int maxDigs() { return Math.max(10, 18 + chests() * 6 - tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(60 + chests() * 30);
    }

    @Override
    protected void build() {
        while (treasures.size() < chests()) {
            int x = random.nextInt(2 * HALF + 1) - HALF;
            int z = random.nextInt(2 * HALF + 1) - HALF;
            if (treasures.stream().noneMatch(t -> Math.abs(t[0] - x) + Math.abs(t[1] - z) < 4)) {
                treasures.add(new int[] {x, z});
            }
        }
        arena.fill(-HALF - 2, -2, -HALF - 2, HALF + 2, -2, HALF + 2, Blocks.SANDSTONE.defaultBlockState());
        arena.fill(-HALF - 2, -1, -HALF - 2, HALF + 2, 0, HALF + 2, Blocks.SMOOTH_SANDSTONE.defaultBlockState()); // no trench round the field
        arena.fill(-HALF, -1, -HALF, HALF, 0, HALF, Blocks.SAND.defaultBlockState());
        arena.fill(-HALF - 2, 0, -HALF - 2, HALF + 2, 0, -HALF - 1, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        // The field's rim is carved sandstone with gold corners and lapis studs; the players' strip is a red, orange
        // and yellow carpet of tiles. The diggable field itself stays plain sand.
        for (int x = -HALF - 2; x <= HALF + 2; x++) {
            for (int z = -HALF - 2; z <= HALF + 2; z++) {
                int m = Math.max(Math.abs(x), Math.abs(z));
                if (m <= HALF) {
                    continue;
                }
                String b;
                if (z <= -HALF - 1 && Math.abs(x) <= HALF + 1) {
                    b = Math.abs(x) == HALF + 1 || z == -HALF - 2 ? "red_terracotta"
                        : (Math.floorMod(x, 2) == 0 ? "orange_terracotta" : "yellow_terracotta");
                } else if (Math.abs(x) == HALF + 2 && Math.abs(z) == HALF + 2) {
                    b = "gold_block";
                } else if (m == HALF + 2) {
                    b = Math.floorMod(x + z, 4) == 0 ? "lapis_block" : "chiseled_sandstone";
                } else {
                    b = "cut_sandstone";
                }
                arena.set(x, 0, z, st(b));
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.OasisLook.build(arena, tier));
        int n = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 0.5 + n++, 1, -HALF - 1.5, 0F);
        }
    }

    @Override
    public void tick(long tickInRoom) {
    }

    @Override
    public boolean hit(ServerPlayer player, BlockPos pos) {
        int x = pos.getX() - arena.origin().getX();
        int y = pos.getY() - arena.origin().getY();
        int z = pos.getZ() - arena.origin().getZ();
        if (y != 0 || Math.abs(x) > HALF || Math.abs(z) > HALF || !arena.level().getBlockState(pos).is(Blocks.SAND)) {
            return false;
        }
        if (finished()) {
            return true;
        }
        digs++;
        arena.level().playSound(null, pos, SoundEvents.SAND_BREAK, SoundSource.BLOCKS, 1F, 1F);
        int[] hit = null;
        int nearest = Integer.MAX_VALUE;
        for (int[] t : treasures) {
            int d = Math.abs(t[0] - x) + Math.abs(t[1] - z);
            if (d == 0) {
                hit = t;
            }
            nearest = Math.min(nearest, d);
        }
        arena.clearBlock(x, 0, z);
        if (hit != null) {
            treasures.remove(hit);
            found++;
            arena.set(x, -1, z, Blocks.CHEST.defaultBlockState());
            arena.level().playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1F, 1F);
            players.values().forEach(p -> Gamemaster.bar(p, "Treasure! " + found + " of " + chests()));
            if (treasures.isEmpty()) {
                players.values().forEach(this::clear);
                return true;
            }
        } else {
            int heat = nearest <= 1 ? 5 : nearest <= 2 ? 4 : nearest <= 4 ? 3 : nearest <= 6 ? 2 : nearest <= 9 ? 1 : 0;
            arena.set(x, -1, z, HEAT[heat]);
        }
        int left = maxDigs() - digs;
        if (hit == null) {
            players.values().forEach(p -> Gamemaster.bar(p, left + " digs left"));
        }
        if (left <= 0 && !treasures.isEmpty()) {
            players.values().forEach(this::fail);
            players.values().forEach(p -> Gamemaster.say(p, "Out of digs. The sand keeps its secrets."));
        }
        return true;
    }
}
