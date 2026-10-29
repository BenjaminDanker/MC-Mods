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
 * A co-op room for two, in two sealed booths: the watcher sees the colours light up, the presser has the pads. Only
 * talking (chat) gets the sequence from one booth to the other. One wrong pad and both lose.
 */
public final class SplitSimon extends BaseRoom {
    private static final BlockState[] PADS = {Blocks.CONCRETE.red().defaultBlockState(), Blocks.CONCRETE.blue().defaultBlockState(),
        Blocks.CONCRETE.lime().defaultBlockState(), Blocks.CONCRETE.yellow().defaultBlockState()};
    private static final String[] NAMES = {"red", "blue", "green", "yellow"};
    private final Random random = new Random();
    private final List<Integer> sequence = new ArrayList<>();
    private ServerPlayer watcher;
    private ServerPlayer presser;
    private int shown = -1;
    private long nextFlashAt = 60;
    private int input;

    public String id() { return "split_simon"; }
    public String title() { return "Two Booths"; }
    public String explain() {
        return "Two booths, two of you. One sees the colours light up; the other has the pads. Talk to each other in chat: "
            + "the watcher calls the colours, the presser hits them in order. One wrong pad and you both lose.";
    }

    int length() { return Math.min(16, 4 + tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(30 + length() * 8);
    }

    @Override
    protected void build() {
        // Watcher's booth around x -6, presser's around x +6; stone walls and roofs, nothing to see across.
        booth(-6);
        booth(6);
        for (int i = 0; i < 4; i++) {
            arena.set(-8 + i + (i >= 2 ? 1 : 0), 2, 3, Blocks.CONCRETE.gray().defaultBlockState()); // watcher's lamps
            arena.set(4 + i + (i >= 2 ? 1 : 0), 1, 3, PADS[i]);                                     // presser's pads
        }
        for (int i = 0; i < length(); i++) {
            sequence.add(random.nextInt(4));
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.AbyssLook.build(arena, tier));
        List<ServerPlayer> list = new ArrayList<>(players.values());
        if (list.isEmpty()) {
            return; // a preview: the booths only
        }
        watcher = list.get(0);
        presser = list.get(list.size() > 1 ? 1 : 0);
        arena.put(watcher, -5.5, 1, 0.5, 0F);
        arena.put(presser, 6.5, 1, 0.5, 0F);
        Gamemaster.bar(watcher, "You watch. Call out the colours.");
        Gamemaster.bar(presser, "You press. Listen to your partner.");
    }

    private void booth(int cx) {
        // Inside a diving bell: riveted copper walls (cut copper bands), a copper-and-deepslate deck, a dark panel
        // behind the lamps or pads so the colours pop. Same sealed box: solid opaque walls, floor and roof.
        for (int x = cx - 3; x <= cx + 3; x++) {
            for (int z = -2; z <= 4; z++) {
                arena.set(x, 0, z, st(Math.floorMod(x + z, 2) == 0 ? "waxed_cut_copper" : "polished_deepslate"));
            }
        }
        for (int y = 1; y <= 4; y++) {
            String band = y == 1 ? "waxed_exposed_cut_copper" : y == 4 ? "waxed_cut_copper" : "waxed_copper_block";
            arena.fill(cx - 3, y, -2, cx - 3, y, 4, st(band));
            arena.fill(cx + 3, y, -2, cx + 3, y, 4, st(band));
            arena.fill(cx - 3, y, -3, cx + 3, y, -3, st(band));
            arena.fill(cx - 3, y, 5, cx + 3, y, 5, st(band));
        }
        arena.fill(cx - 3, 5, -3, cx + 3, 5, 5, st("waxed_weathered_copper"));
        // the panel behind the lamps / pads (z 4, never a lamp or pad cell)
        for (int x = cx - 2; x <= cx + 2; x++) {
            for (int y = 1; y <= 3; y++) {
                arena.set(x, y, 4, st(y == 3 || Math.abs(x - cx) == 2 ? "gold_block" : "polished_blackstone"));
            }
        }
        arena.set(cx, 4, 1, Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING, true));
    }

    private int lampX(int i) { return -8 + i + (i >= 2 ? 1 : 0); }
    private int padX(int i) { return 4 + i + (i >= 2 ? 1 : 0); }

    @Override
    public void tick(long tickInRoom) {
        if (shown >= sequence.size() || tickInRoom < nextFlashAt) {
            return;
        }
        if (shown >= 0) {
            arena.set(lampX(sequence.get(shown)), 2, 3, Blocks.CONCRETE.gray().defaultBlockState());
        }
        shown++;
        if (shown >= sequence.size()) {
            Gamemaster.bar(watcher, "That's all of it.");
            return;
        }
        int lamp = sequence.get(shown);
        arena.set(lampX(lamp), 2, 3, PADS[lamp]);
        // Slow enough to call out; it doesn't wait for the presser.
        nextFlashAt = tickInRoom + Math.max(30, 50 - tier * 2);
        if (shown + 1 < sequence.size()) {
            Gamemaster.bar(watcher, "Colour " + (shown + 1) + " of " + sequence.size());
        }
    }

    @Override
    public void rejoined(ServerPlayer player) {
        super.rejoined(player);
        if (watcher.getUUID().equals(player.getUUID())) {
            watcher = player;
        }
        if (presser.getUUID().equals(player.getUUID())) {
            presser = player;
        }
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
        int dy = pos.getY() - arena.origin().getY();
        int dz = pos.getZ() - arena.origin().getZ();
        int pad = -1;
        for (int i = 0; i < 4; i++) {
            if (dx == padX(i) && dy == 1 && dz == 3) {
                pad = i;
            }
        }
        if (pad < 0) {
            return false;
        }
        if (player != presser || finished()) {
            return true;
        }
        arena.level().playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1F, 0.6F + pad * 0.2F);
        if (input >= sequence.size() || pad != sequence.get(input)) {
            Gamemaster.say(presser, "Wrong. It was " + (input < sequence.size() ? NAMES[sequence.get(input)] : "nothing") + ".");
            Gamemaster.say(watcher, "Wrong pad.");
            players.values().forEach(this::fail);
            return true;
        }
        input++;
        Gamemaster.bar(presser, input + " of " + sequence.size());
        if (input >= sequence.size()) {
            players.values().forEach(this::clear);
        }
        return true;
    }
}
