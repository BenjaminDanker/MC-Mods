package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copy the picture: a pattern of coloured wool shows on the wall for a few seconds, then hides. Recreate it on your
 * canvas below (each hit turns a square to the next colour). Get it exactly right to clear.
 */
public final class PatternCopy extends BaseRoom {
    private static final BlockState[] PALETTE = {Blocks.WOOL.white().defaultBlockState(), Blocks.WOOL.red().defaultBlockState(),
        Blocks.WOOL.blue().defaultBlockState(), Blocks.WOOL.yellow().defaultBlockState(), Blocks.WOOL.lime().defaultBlockState()};
    private final Random random = new Random();
    private int[][] pattern;
    private int[][] canvas;
    private int size;
    private int colours;
    private long hideAt;
    private boolean hidden;

    public String id() { return "pattern"; }
    public String title() { return "Copy the Picture"; }
    public String explain() {
        return "A picture appears on the wall for a few seconds. When it hides, paint it on the canvas below: each hit turns a "
            + "square to the next colour. Get every square right.";
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        size = Math.min(5, 3 + tier / 3);
        colours = Math.min(PALETTE.length, 2 + tier / 2);
        return seconds(30 + size * size * 2);
    }

    @Override
    protected void build() {
        pattern = new int[size][size];
        canvas = new int[size][size];
        boolean any = false;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                pattern[x][y] = random.nextInt(colours);
                any |= pattern[x][y] != 0;
            }
        }
        if (!any) {
            pattern[0][0] = 1;
        }
        arena.fill(-4, 0, -3, 4, 0, 5, Blocks.SMOOTH_STONE.defaultBlockState());
        arena.fill(-4, 1, 6, 4, 2 + 2 * size + 1, 6, Blocks.DARK_OAK_PLANKS.defaultBlockState());
        drawPicture(true);
        drawCanvas();
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.GalleryLook.build(arena, tier, size));
        hideAt = 40 + Math.max(60, 140 - tier * 8) + size * size * 3L;
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 0.5 + i++, 1, 0.5, 0F);
        }
    }

    /** The picture sits above the canvas on the same wall (z 5); its top row at the top. */
    private void drawPicture(boolean show) {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                arena.set(x - size / 2, 3 + size + y, 5, show ? PALETTE[pattern[x][y]] : Blocks.DARK_OAK_PLANKS.defaultBlockState());
            }
        }
    }

    private void drawCanvas() {
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                arena.set(x - size / 2, 2 + y, 5, PALETTE[canvas[x][y]]);
            }
        }
    }

    @Override
    public void tick(long tickInRoom) {
        if (!hidden && tickInRoom >= hideAt) {
            hidden = true;
            drawPicture(false);
            players.values().forEach(p -> Gamemaster.bar(p, "Now paint it."));
        }
    }

    @Override
    public boolean hit(ServerPlayer player, BlockPos pos) {
        return paint(pos);
    }

    @Override
    public boolean use(ServerPlayer player, BlockPos pos) {
        return paint(pos);
    }

    private boolean paint(BlockPos pos) {
        int x = pos.getX() - arena.origin().getX() + size / 2;
        int y = pos.getY() - arena.origin().getY() - 2;
        if (pos.getZ() - arena.origin().getZ() != 5 || x < 0 || x >= size || y < 0 || y >= size) {
            return false;
        }
        if (!hidden || finished()) {
            return true; // no painting while the picture still shows
        }
        canvas[x][y] = (canvas[x][y] + 1) % colours;
        drawCanvas();
        if (java.util.Arrays.deepEquals(canvas, pattern)) {
            players.values().forEach(this::clear);
            players.values().forEach(p -> Gamemaster.bar(p, "A perfect copy."));
        }
        return true;
    }
}
