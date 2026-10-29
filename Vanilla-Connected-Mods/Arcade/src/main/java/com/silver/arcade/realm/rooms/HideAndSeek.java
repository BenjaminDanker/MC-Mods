package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;

/**
 * Hide and seek, a versus room: one seeker, everyone else hides in a cluttered yard of huts, crates and hedges.
 * Name tags are hidden. The seeker waits blindfolded, then has the clock to tag every hider. Hiders still free at the
 * bell clear; the seeker clears by finding them all.
 */
public final class HideAndSeek extends BaseRoom {
    private static final int HALF = 16;
    private final Random random = new Random();
    private ServerPlayer seeker;
    private long hideEnds;

    public String id() { return "hide_seek"; }
    public String title() { return "Hide and Seek"; }
    public String explain() {
        return "One of you seeks, the rest hide. The seeker waits with eyes shut, then has to tag every hider before the bell. "
            + "Name tags are off, so look carefully.";
    }

    @Override
    public boolean pvp() {
        return true;
    }

    @Override
    public boolean hideNameTags() {
        return true;
    }

    int hideTicks() { return seconds(25); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return hideTicks() + seconds(Math.max(60, 120 - tier * 4));
    }

    @Override
    protected void build() {
        // The graveyard lawn: grass, moss and podzol with a mossy cobbled cross path; the yard wall is old mossy and
        // cracked stone with dark buttresses and a chiseled cap (same 4-high solid wall).
        for (int x = -HALF; x <= HALF; x++) {
            for (int z = -HALF; z <= HALF; z++) {
                int k = Math.floorMod(x * 7 + z * 13, 11);
                arena.set(x, 0, z, (Math.abs(x) <= 1 || Math.abs(z) <= 1)
                    ? (k < 4 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState())
                    : (k == 0 ? Blocks.PODZOL.defaultBlockState() : (k == 5 ? Blocks.MOSS_BLOCK.defaultBlockState() : Blocks.GRASS_BLOCK.defaultBlockState())));
            }
        }
        for (int i = -HALF - 1; i <= HALF + 1; i++) {
            for (int y = 1; y <= 4; y++) {
                for (int[] w : new int[][] {{i, -HALF - 1}, {i, HALF + 1}, {-HALF - 1, i}, {HALF + 1, i}}) {
                    boolean buttress = Math.floorMod(i, 4) == 0;
                    int k = Math.floorMod(w[0] * 5 + w[1] * 3 + y * 7, 6);
                    arena.set(w[0], y, w[1], y == 4 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState()
                        : buttress ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : k < 2 ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : (k == 2 ? Blocks.CRACKED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState()));
                }
            }
        }
        // Clutter to hide behind: little huts (open on one side), crate stacks and hedge rows; the middle stays clear.
        for (int n = 0; n < 14; n++) {
            int x = random.nextInt(2 * HALF - 6) - HALF + 3;
            int z = random.nextInt(2 * HALF - 6) - HALF + 3;
            if (Math.abs(x) < 4 && Math.abs(z) < 4) {
                continue;
            }
            switch (random.nextInt(3)) {
                case 0 -> hut(x, z);
                case 1 -> arena.fill(x, 1, z, x + 1, 1 + random.nextInt(3), z + 1, Blocks.BARREL.defaultBlockState());
                default -> arena.fill(x, 1, z, x + 4, 2, z, Blocks.DARK_OAK_LEAVES.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT, true));
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.ManorLook.build(arena, tier, HALF));
        List<ServerPlayer> list = new ArrayList<>(players.values());
        if (list.isEmpty()) {
            return;
        }
        seeker = list.get(random.nextInt(list.size()));
        for (int i = 0; i < list.size(); i++) {
            double[] at = spot(i, 4);
            arena.put(list.get(i), at[0], 1, at[1], 0F);
            effect(list.get(i), MobEffects.RESISTANCE, 20 * 300, 4);
        }
        effect(seeker, MobEffects.BLINDNESS, hideTicks() + 20, 0);
        effect(seeker, MobEffects.SLOWNESS, hideTicks(), 9);
        Gamemaster.title(seeker, "You seek", "Eyes shut... count to " + hideTicks() / 20);
        for (ServerPlayer player : list) {
            if (player != seeker) {
                Gamemaster.title(player, "Hide!", seeker.getName().getString() + " is seeking");
            }
        }
        hideEnds = hideTicks();
    }

    /** A little crypt (same footprint and openings as the old hut): deepslate walls, tiled roof, a lantern inside. */
    private void hut(int x, int z) {
        arena.fill(x, 1, z, x + 3, 2, z + 3, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        arena.fill(x, 3, z, x + 3, 3, z + 3, Blocks.DEEPSLATE_TILES.defaultBlockState());
        for (int[] c : new int[][] {{x, z}, {x + 3, z}, {x, z + 3}, {x + 3, z + 3}}) {
            arena.set(c[0], 1, c[1], Blocks.CHISELED_DEEPSLATE.defaultBlockState());
            arena.set(c[0], 2, c[1], Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
        }
        arena.fill(x + 1, 1, z + 1, x + 2, 2, z + 2, Blocks.AIR.defaultBlockState());
        arena.fill(x + 1, 1, z, x + 2, 2, z, Blocks.AIR.defaultBlockState()); // doorway
        arena.set(x + 1, 3, z, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
        arena.set(x + 2, 3, z, Blocks.CHISELED_DEEPSLATE.defaultBlockState());
    }

    @Override
    public void tick(long tickInRoom) {
        if (seeker == null) {
            return;
        }
        if (tickInRoom == hideEnds) {
            players.values().forEach(p -> Gamemaster.title(p, "Ready or not!", ""));
        }
        List<ServerPlayer> hiders = undecided().stream().filter(p -> p != seeker).toList();
        if (tickInRoom > hideEnds && hiders.isEmpty() && !decided(seeker.getUUID())) {
            clear(seeker);
            Gamemaster.bar(seeker, "Found them all!");
        }
    }

    @Override
    public boolean playerHit(ServerPlayer attacker, ServerPlayer victim) {
        if (attacker == seeker && victim != seeker && !decided(victim.getUUID()) && arena != null) {
            fail(victim);
            victim.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
            Gamemaster.bar(victim, "Found!");
            Gamemaster.bar(seeker, "Found " + victim.getName().getString() + "!");
        }
        return false; // tags don't hurt or push
    }

    @Override
    public java.util.Set<java.util.UUID> cleared() {
        // The bell: hiders still free clear; the seeker failed to find them.
        if (seeker != null && !decided(seeker.getUUID())) {
            fail(seeker);
        }
        undecided().forEach(this::clear);
        return super.cleared();
    }

    @Override
    public void rejoined(ServerPlayer player) {
        super.rejoined(player);
        if (seeker != null && seeker.getUUID().equals(player.getUUID())) {
            seeker = player;
        }
    }
}
