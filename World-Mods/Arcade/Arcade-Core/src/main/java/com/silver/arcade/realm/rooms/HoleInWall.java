package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/**
 * Hole in the wall: walls slide down the stage towards you, each with a gap somewhere. Stand where the gap will pass.
 * Anyone the wall meets is swept off. Let every wall through to clear.
 */
public final class HoleInWall extends BaseRoom {
    private static final int HALF = 4;
    private static final int START_Z = 16;
    private final Random random = new Random();
    private Set<Integer> holes = new HashSet<>();
    private int wallZ = Integer.MIN_VALUE;
    private int walls;
    private long nextStep = 80;

    public String id() { return "hole_in_the_wall"; }
    public String title() { return "Hole in the Wall"; }
    public String explain() {
        return "Walls slide towards you, each with a gap. Stand where the gap will pass you. The wall sweeps away anyone in its path.";
    }

    // 25 levels: more walls, faster, and the hole narrows from 3 wide to 2 (level 4) to 1 (level 12)
    int wallCount() { return Math.min(14, 4 + (2 * tier) / 5); }
    int stepTicks() { return Math.max(3, 10 - (7 * tier) / 20); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        // Per wall: its run from START_Z past the stage, the step that spawns it and the 30-tick pause after it.
        return 80 + wallCount() * ((START_Z + 5) * stepTicks() + 40) + 300;
    }

    @Override
    protected void build() {
        // The contestants' stage: a glossy black-and-white checker with a gold front edge; the runway is black with
        // light strips down both sides (full blocks), so the purple walls stand out all the way.
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -1; z <= 2; z++) {
                arena.set(x, 0, z, st(z == -1 ? "gold_block" : Math.floorMod(x + z, 2) == 0 ? "white_concrete" : "black_concrete"));
            }
        }
        for (int x = -HALF - 1; x <= HALF + 1; x++) {
            for (int z = 3; z <= START_Z + 1; z++) {
                arena.set(x, 0, z, st(Math.abs(x) == HALF + 1 ? (z % 2 == 0 ? "pearlescent_froglight" : "gold_block")
                    : z % 4 == 0 ? "gray_concrete" : "black_concrete")); // the wall's runway
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.StudioLook.build(arena, tier));
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            arena.put(list.get(i), -HALF + 0.5 + (i * 2) % (2 * HALF + 1), 1, 0.5, 0F); // facing the oncoming walls
        }
    }

    private void drawWall(boolean on) {
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = 1; y <= 3; y++) {
                if (on && !(holes.contains(x) && y <= 2)) {
                    arena.set(x, y, wallZ, Blocks.CONCRETE.purple().defaultBlockState());
                } else if (!on) {
                    arena.clearBlock(x, y, wallZ);
                }
            }
        }
    }

    @Override
    public void tick(long tickInRoom) {
        for (ServerPlayer player : undecided()) {
            if (heightOf(player) < -0.5) {
                out(player, "You fell off the stage."); // nothing underneath: off the stage is out at once
            }
        }
        if (tickInRoom < nextStep || undecided().isEmpty()) {
            return;
        }
        nextStep = tickInRoom + stepTicks();
        if (wallZ == Integer.MIN_VALUE) {
            if (walls >= wallCount()) {
                undecided().forEach(p -> {
                    clear(p);
                    Gamemaster.bar(p, "Not a scratch!");
                });
                return;
            }
            walls++;
            holes = new HashSet<>();
            int width = tier >= 12 ? 1 : tier >= 4 ? 2 : 3;
            int at = random.nextInt(2 * HALF + 2 - width) - HALF;
            for (int i = 0; i < width; i++) {
                holes.add(at + i);
            }
            wallZ = START_Z;
            drawWall(true);
            return;
        }
        drawWall(false);
        wallZ--;
        if (wallZ < -2) {
            wallZ = Integer.MIN_VALUE;
            nextStep = tickInRoom + 30;
            return;
        }
        drawWall(true);
        for (ServerPlayer player : undecided()) {
            int px = player.getBlockX() - arena.origin().getX();
            int pz = player.getBlockZ() - arena.origin().getZ();
            if (pz == wallZ && !holes.contains(px)) {
                out(player, "Swept away!");
            } else if (heightOf(player) < -2) {
                out(player, "You fell.");
            }
        }
    }
}
