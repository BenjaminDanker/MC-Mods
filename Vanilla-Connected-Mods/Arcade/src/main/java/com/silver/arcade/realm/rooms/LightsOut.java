package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/** Lights Out on a wall of lamps: hit a lamp to flip it and its neighbours; turn every lamp off. */
public final class LightsOut extends BaseRoom {
    private final Random random = new Random();
    private LightsOutBoard board;

    public String id() { return "lights_out"; }
    public String title() { return "Lights Out"; }
    public String explain() {
        return "Hit a lamp on the wall and it flips, along with the lamps above, below and beside it. Put every lamp out.";
    }

    int size() { return Math.min(5, 3 + tier / 3); }
    int presses() { return Math.min(size() * size(), 2 + tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(45 + presses() * 10);
    }

    @Override
    protected void build() {
        board = LightsOutBoard.scrambled(size(), presses(), random);
        arena.fill(-4, 0, -3, 4, 0, 5, Blocks.POLISHED_DEEPSLATE.defaultBlockState());
        arena.fill(-4, 1, 6, 4, 7, 6, Blocks.DEEPSLATE_TILES.defaultBlockState());
        draw();
        com.silver.arcade.realm.deco.GeodeLook.build(arena, tier);
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 0.5 + i++, 1, 0.5, 0F);
        }
    }

    /** Lamp (x, y) sits on the wall at z 5, centred, bottom row at height 2. */
    private void draw() {
        int n = board.size;
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < n; y++) {
                arena.set(x - n / 2, 2 + y, 5, board.lit(x, y) ? Blocks.SHROOMLIGHT.defaultBlockState() : Blocks.CONCRETE.black().defaultBlockState());
            }
        }
    }

    @Override
    public void tick(long tickInRoom) {
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
        int n = board.size;
        int x = pos.getX() - arena.origin().getX() + n / 2;
        int y = pos.getY() - arena.origin().getY() - 2;
        if (pos.getZ() - arena.origin().getZ() != 5 || x < 0 || y < 0 || x >= n || y >= n) {
            return false;
        }
        if (finished()) {
            return true;
        }
        board.press(x, y);
        draw();
        arena.level().playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.8F, 1.2F);
        if (board.solved()) {
            players.values().forEach(this::clear);
            players.values().forEach(p -> Gamemaster.bar(p, "Darkness. Well done."));
        }
        return true;
    }
}
