package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Red light, green light: run for the gold line while the light is green. When it turns red, freeze: anyone caught
 * moving goes back to the start. Reaching the line clears.
 */
public final class RedLight extends BaseRoom {
    private static final int LENGTH = 48;
    private final Random random = new Random();
    private final Map<UUID, Vec3> frozenAt = new HashMap<>();
    private boolean green;
    private long switchAt = 60;
    private long redSince;

    public String id() { return "red_light"; }
    public String title() { return "Red Light, Green Light"; }
    public String explain() {
        return "Run for the gold line while the light is green. When it turns red, freeze. Anyone I catch moving goes back to the start.";
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(90);
    }

    /** Green lasts shorter as tiers climb; a short grace after red lets runners stop. */
    int greenTicks() { return Math.max(20, 70 - tier * 4) + random.nextInt(40); }
    int redTicks() { return 30 + random.nextInt(30); }
    int graceTicks() { return Math.max(4, 10 - tier / 2); }

    @Override
    protected void build() {
        arena.fill(-6, 0, -2, 6, 0, LENGTH + 3, Blocks.SMOOTH_SANDSTONE.defaultBlockState());
        arena.fill(-7, 1, -2, -7, 2, LENGTH + 3, Blocks.SANDSTONE_WALL.defaultBlockState());
        arena.fill(7, 1, -2, 7, 2, LENGTH + 3, Blocks.SANDSTONE_WALL.defaultBlockState());
        arena.fill(-6, 0, LENGTH, 6, 0, LENGTH + 3, Blocks.GOLD_BLOCK.defaultBlockState());
        arena.fill(-2, 1, LENGTH + 5, 2, 5, LENGTH + 5, Blocks.CONCRETE.lime().defaultBlockState());
        List<ServerPlayer> list = List.copyOf(players.values());
        for (int i = 0; i < list.size(); i++) {
            start(list.get(i), i);
        }
    }

    private void start(ServerPlayer player, int lane) {
        arena.put(player, -5 + (lane % 6) * 2 + 0.5, 1, 0.5 - (lane / 6), 0F);
    }

    private void light(boolean on) {
        green = on;
        arena.fill(-2, 1, LENGTH + 5, 2, 5, LENGTH + 5, on ? Blocks.CONCRETE.lime().defaultBlockState() : Blocks.CONCRETE.red().defaultBlockState());
        for (ServerPlayer player : players.values()) {
            Gamemaster.title(player, on ? "Green light" : "RED LIGHT", "");
        }
        arena.level().playSound(null, arena.at(0, 3, LENGTH), on ? SoundEvents.NOTE_BLOCK_CHIME.value() : SoundEvents.NOTE_BLOCK_BASS.value(),
            SoundSource.BLOCKS, 3F, on ? 1.4F : 0.6F);
    }

    @Override
    public void tick(long tickInRoom) {
        if (tickInRoom >= switchAt) {
            light(!green);
            switchAt = tickInRoom + (green ? greenTicks() : redTicks());
            redSince = tickInRoom;
            frozenAt.clear();
        }
        List<ServerPlayer> running = undecided();
        for (int i = 0; i < running.size(); i++) {
            ServerPlayer player = running.get(i);
            if (heightOf(player) < -2) {
                start(player, i);
                continue;
            }
            if (player.getZ() - arena.origin().getZ() >= LENGTH) {
                clear(player);
                Gamemaster.bar(player, "Made it!");
                continue;
            }
            if (!green && tickInRoom - redSince >= graceTicks()) {
                Vec3 there = frozenAt.computeIfAbsent(player.getUUID(), id -> player.position());
                if (player.position().distanceToSqr(there) > 0.04) {
                    Gamemaster.bar(player, "I saw that. Back to the start.");
                    start(player, i);
                    frozenAt.remove(player.getUUID()); // their frozen spot is taken again next tick, at the start
                }
            }
        }
    }

    @Override
    public void fell(ServerPlayer player) {
        start(player, 0);
    }
}
