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
 * Simon says: four coloured pads light in a sequence; hit them back in order. The sequence grows each tier and plays
 * faster. In a duo, the partners take turns: each repeats every other step.
 */
public final class Memory extends BaseRoom {
    private static final BlockState[] PADS = {Blocks.CONCRETE.red().defaultBlockState(), Blocks.CONCRETE.blue().defaultBlockState(),
        Blocks.CONCRETE.lime().defaultBlockState(), Blocks.CONCRETE.yellow().defaultBlockState()};
    private static final float[] PITCH = {0.6F, 0.8F, 1.0F, 1.2F};
    private final Random random = new Random();
    private final List<Integer> sequence = new ArrayList<>();
    private final List<ServerPlayer> order = new ArrayList<>();
    private int shown = -1;
    private long nextFlashAt;
    private int input;
    private boolean showing = true;

    public String id() { return "memory"; }
    public String title() { return "Simon Says"; }
    public String explain() {
        return "Watch the four pads light up, then hit them in the same order. With a partner, you take turns: one does the "
            + "first step, the other the second, and so on. One wrong pad and the room is lost for both.";
    }

    int length() { return Math.min(20, 3 + tier); }
    int flashTicks() { return Math.max(8, 16 - tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(length() * (flashTicks() / 20.0 * 2 + 2.5) + 10);
    }

    @Override
    protected void build() {
        arena.fill(-3, 0, -2, 4, 0, 4, Blocks.POLISHED_ANDESITE.defaultBlockState());
        for (int i = 0; i < 4; i++) {
            arena.set(-2 + i * 2, 1, 4, PADS[i]);
            arena.set(-2 + i * 2, 2, 4, PADS[i]);
        }
        com.silver.arcade.realm.deco.LonghallLook.build(arena, tier);
        for (int i = 0; i < length(); i++) {
            sequence.add(random.nextInt(4));
        }
        order.addAll(players.values());
        for (int i = 0; i < order.size(); i++) {
            arena.put(order.get(i), -0.5 + i * 2, 1, 0.5, 0F);
        }
    }

    @Override
    public void tick(long tickInRoom) {
        if (!showing || tickInRoom < nextFlashAt) {
            return;
        }
        if (shown >= 0 && shown < sequence.size()) {
            int pad = sequence.get(shown);
            arena.set(-2 + pad * 2, 1, 4, PADS[pad]);
            arena.set(-2 + pad * 2, 2, 4, PADS[pad]);
        }
        shown++;
        if (shown >= sequence.size()) {
            showing = false;
            order.forEach(p -> Gamemaster.bar(p, "Your turn" + (order.size() > 1 ? ": " + order.get(0).getName().getString() + " starts" : "")));
            return;
        }
        int pad = sequence.get(shown);
        arena.set(-2 + pad * 2, 1, 4, Blocks.SEA_LANTERN.defaultBlockState());
        arena.set(-2 + pad * 2, 2, 4, Blocks.SEA_LANTERN.defaultBlockState());
        arena.level().playSound(null, arena.at(1, 2, 4), SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1F, PITCH[pad]);
        nextFlashAt = tickInRoom + flashTicks() * 2L;
    }

    @Override
    public void rejoined(ServerPlayer player) {
        super.rejoined(player);
        order.replaceAll(p -> p.getUUID().equals(player.getUUID()) ? player : p);
    }

    @Override
    public boolean hit(ServerPlayer player, BlockPos pos) {
        return press(player, pos);
    }

    @Override
    public boolean use(ServerPlayer player, BlockPos pos) {
        return press(player, pos);
    }

    private boolean press(ServerPlayer player, BlockPos pos) {
        int dx = pos.getX() - arena.origin().getX();
        int dz = pos.getZ() - arena.origin().getZ();
        if (dz != 4 || dx < -2 || dx > 4 || (dx + 2) % 2 != 0) {
            return false;
        }
        if (showing || finished()) {
            return true;
        }
        ServerPlayer turn = order.get(input % order.size());
        if (player != turn) {
            Gamemaster.bar(player, "Not your turn.");
            return true;
        }
        int pad = (dx + 2) / 2;
        arena.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1F, PITCH[pad]);
        if (pad != sequence.get(input)) {
            Gamemaster.say(player, "Wrong pad.");
            order.forEach(this::fail);
            return true;
        }
        input++;
        if (input >= sequence.size()) {
            order.forEach(this::clear);
        }
        return true;
    }
}
