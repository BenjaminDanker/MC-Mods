package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/**
 * Musical chairs, a versus room: walk around while the music plays. When it stops, gold seats appear at random spots;
 * get onto one. There is one seat fewer than players and a seat holds one. Whoever has no seat is out. The last one seated wins.
 */
public final class MusicalChairs extends BaseRoom {
    private static final int RADIUS = 7;
    private final Random random = new Random();
    private final List<int[]> seats = new ArrayList<>();
    private boolean music;
    private long phaseEnds = 40;

    public String id() { return "chairs"; }
    public String title() { return "Musical Chairs"; }
    public String explain() {
        return "Walk while the music plays. When it stops, gold seats appear: get onto one. One seat fewer than players, one player per seat. No seat, no game.";
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(150);
    }

    @Override
    public int[] bounds() {
        return new int[] {-40, -20, -40, 40, 40, 40};
    }

    int grabTicks() { return Math.max(30, 70 - tier * 4); }

    @Override
    protected void build() {
        for (int x = -RADIUS - 2; x <= RADIUS + 2; x++) {
            for (int z = -RADIUS - 2; z <= RADIUS + 2; z++) {
                arena.set(x, 0, z, floorAt(x, z));
            }
        }
        List<ServerPlayer> list = List.copyOf(players.values());
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.TeaPartyLook.build(arena, tier, RADIUS));
        for (int i = 0; i < list.size(); i++) {
            double a = Math.PI * 2 * i / list.size();
            arena.put(list.get(i), Math.cos(a) * RADIUS + 0.5, 1, Math.sin(a) * RADIUS + 0.5, 0F);
        }
    }

    /**
     * The tea party's tablecloth: muted red and white gingham in 2-block checks, a lace edge of pink and white round
     * it (the last two rings), so the gold seats are the only bright, shiny blocks on the floor.
     */
    private static net.minecraft.world.level.block.state.BlockState floorAt(int x, int z) {
        int m = Math.max(Math.abs(x), Math.abs(z));
        if (m >= RADIUS + 1) {
            return (m == RADIUS + 2 ? Math.floorMod(x + z, 2) == 0 : Math.floorMod(x + z, 2) != 0)
                ? st("white_terracotta") : st("pink_terracotta");
        }
        boolean a = Math.floorMod(Math.floorDiv(x, 2), 2) == 0, b = Math.floorMod(Math.floorDiv(z, 2), 2) == 0;
        if (a && b) {
            return st("red_terracotta");
        }
        if (a || b) {
            return Blocks.TERRACOTTA.defaultBlockState();
        }
        return st("white_terracotta");
    }

    /** Seats scattered on the floor, at least 3 apart. */
    private void layout(int count) {
        for (int[] seat : seats) {
            arena.set(seat[0], 0, seat[1], floorAt(seat[0], seat[1]));
        }
        seats.clear();
        int guard = 0;
        while (seats.size() < count && guard++ < 500) {
            int x = random.nextInt(2 * RADIUS + 1) - RADIUS;
            int z = random.nextInt(2 * RADIUS + 1) - RADIUS;
            boolean far = seats.stream().allMatch(s -> Math.abs(s[0] - x) + Math.abs(s[1] - z) >= 3);
            if (far) {
                seats.add(new int[] {x, z});
                arena.set(x, 0, z, Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
    }

    @Override
    public void tick(long tickInRoom) {
        List<ServerPlayer> playing = undecided();
        if (players.size() > 1 && playing.size() == 1) {
            clear(playing.get(0));
            Gamemaster.bar(playing.get(0), "The last seat is yours!");
            return;
        }
        if (playing.isEmpty() || tickInRoom < phaseEnds) {
            if (music && tickInRoom % 5 == 0) {
                arena.level().playSound(null, arena.at(0, 3, 0), SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.RECORDS, 2F,
                    0.6F + random.nextInt(12) * 0.1F);
            }
            return;
        }
        if (!music) {
            // The grab is over: everyone off a seat is out (one player per seat); the seats go away again.
            if (!seats.isEmpty()) {
                Set<Long> taken = new HashSet<>();
                for (ServerPlayer player : playing) {
                    BlockPos below = player.blockPosition().below();
                    boolean seated = arena.level().getBlockState(below).is(Blocks.GOLD_BLOCK) && taken.add(below.asLong());
                    if (!seated) {
                        out(player, "No seat!");
                    }
                }
                layout(0);
            }
            music = true;
            phaseEnds = tickInRoom + 80 + random.nextInt(120);
            players.values().forEach(p -> Gamemaster.bar(p, "Music! Keep moving."));
        } else {
            music = false;
            layout(Math.max(1, playing.size() - 1));
            phaseEnds = tickInRoom + grabTicks();
            players.values().forEach(p -> Gamemaster.title(p, "SIT!", ""));
        }
    }
}
