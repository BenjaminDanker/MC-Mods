package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Block Party: the floor is a patchwork of colours. A colour is called; when the time runs out, every other colour
 * vanishes. Be standing on the called colour. Survive every round to clear.
 */
public final class BlockParty extends BaseRoom {
    static final int HALF = 8;
    private final Random random = new Random();
    private final DyeColor[] palette = DyeColor.values();
    private int[][] floor;
    private int round;
    private int called = -1;
    private long phaseEnds = 60;
    private boolean dropped;

    public String id() { return "block_party"; }
    public String title() { return "Block Party"; }
    public String explain() {
        return "I call a colour. When the music stops, every other colour disappears. Be standing on mine. Last all the rounds to win.";
    }

    int rounds() { return Math.min(10, 3 + tier / 2); }
    int colours() { return Math.min(16, 4 + tier); }
    int callTicks() { return Math.max(24, 80 - tier * 5); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return 60 + rounds() * (callTicks() + 60) + 100;
    }

    private static Block concrete(DyeColor colour) {
        return Blocks.CONCRETE.pick(colour);
    }

    private void paint() {
        floor = new int[2 * HALF + 1][2 * HALF + 1];
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                int c = random.nextInt(colours());
                floor[x + HALF][z + HALF] = c;
                arena.set(x, 0, z, concrete(palette[c]).defaultBlockState());
            }
        }
    }

    @Override
    protected void build() {
        paint();
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            double[] at = spot(i, HALF - 1);
            arena.put(list.get(i), at[0], 1, at[1] + 2, 0F);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        for (ServerPlayer player : undecided()) {
            if (heightOf(player) < -3) {
                out(player, "Wrong colour!");
            }
        }
        if (undecided().isEmpty() || tickInRoom < phaseEnds) {
            return;
        }
        if (called < 0) {
            // Call a colour that is on the floor.
            called = floor[random.nextInt(floor.length)][random.nextInt(floor.length)];
            DyeColor colour = palette[called];
            for (ServerPlayer player : players.values()) {
                Gamemaster.title(player, colour.getName().replace('_', ' ').toUpperCase(), "Round " + (round + 1) + " of " + rounds());
                player.getInventory().clearContent();
                for (int slot = 0; slot < 9; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(concrete(colour)));
                }
            }
            phaseEnds = tickInRoom + callTicks();
        } else if (!dropped) {
            for (int x = -HALF; x <= HALF; x++) {
                for (int z = -HALF; z <= HALF; z++) {
                    if (floor[x + HALF][z + HALF] != called) {
                        arena.clearBlock(x, 0, z);
                    }
                }
            }
            dropped = true;
            phaseEnds = tickInRoom + 50;
        } else {
            round++;
            if (round >= rounds()) {
                undecided().forEach(p -> {
                    clear(p);
                    Gamemaster.bar(p, "You danced through it all!");
                });
                return;
            }
            paint();
            called = -1;
            dropped = false;
            phaseEnds = tickInRoom + 30;
        }
    }
}
