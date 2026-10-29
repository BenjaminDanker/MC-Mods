package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sliding puzzle: coloured tiles in a frame with one gap. Hit a tile next to the gap to slide it in. Put them in
 * the order of the finished picture shown beside the frame.
 */
public final class TileSlide extends BaseRoom {
    private static final DyeColor[] ORDER = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.GREEN,
        DyeColor.CYAN, DyeColor.LIGHT_BLUE, DyeColor.BLUE, DyeColor.PURPLE, DyeColor.MAGENTA, DyeColor.PINK, DyeColor.WHITE,
        DyeColor.LIGHT_GRAY, DyeColor.GRAY, DyeColor.BROWN};
    private final Random random = new Random();
    private SlideBoard board;

    public String id() { return "slide"; }
    public String title() { return "The Sliding Picture"; }
    public String explain() {
        return "Hit a tile next to the gap to slide it in. Make the frame match the finished picture beside it.";
    }

    int size() { return tier >= 7 ? 4 : 3; }
    int moves() { return Math.min(size() == 3 ? 40 : 70, 8 + tier * 4); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(60 + moves() * 4);
    }

    private static BlockState tile(int n) {
        return n == 0 ? Blocks.AIR.defaultBlockState() : Blocks.CONCRETE.pick(ORDER[n - 1]).defaultBlockState();
    }

    @Override
    protected void build() {
        board = SlideBoard.scrambled(size(), moves(), random);
        int n = size();
        arena.fill(-4, 0, -2, n + 8, 0, 4, Blocks.SMOOTH_STONE.defaultBlockState());
        arena.fill(-1, 1, 6, n + 7, n + 3, 6, Blocks.DARK_PRISMARINE.defaultBlockState()); // backboard
        // The finished picture, to the right (x n+2 .. 2n+1), smaller frame of the same layout.
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int want = (x == n - 1 && y == n - 1) ? 0 : y * n + x + 1;
                arena.set(n + 3 + x, 2 + (n - 1 - y), 5, want == 0 ? Blocks.STAINED_GLASS.black().defaultBlockState() : tile(want));
            }
        }
        draw();
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 1.5 + i++, 1, 1.5, 0F);
        }
    }

    /** Frame cell (x, y) is at block (x, 2 + n-1-y, 5): row 0 at the top. */
    private void draw() {
        int n = board.size;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                arena.set(x, 2 + (n - 1 - y), 5, tile(board.tiles[x][y]));
            }
        }
    }

    @Override
    public void tick(long tickInRoom) {
    }

    @Override
    public boolean hit(ServerPlayer player, BlockPos pos) {
        return press(pos);
    }

    @Override
    public boolean use(ServerPlayer player, BlockPos pos) {
        return press(pos);
    }

    private boolean press(BlockPos pos) {
        int n = board.size;
        int x = pos.getX() - arena.origin().getX();
        int y = n - 1 - (pos.getY() - arena.origin().getY() - 2);
        if (pos.getZ() - arena.origin().getZ() != 5 || x < 0 || x >= n || y < 0 || y >= n) {
            return false;
        }
        if (finished()) {
            return true;
        }
        if (board.slide(x, y)) {
            draw();
            arena.level().playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8F, 1.3F);
            if (board.solved()) {
                players.values().forEach(this::clear);
                players.values().forEach(p -> Gamemaster.bar(p, "Picture perfect."));
            }
        }
        return true;
    }
}
