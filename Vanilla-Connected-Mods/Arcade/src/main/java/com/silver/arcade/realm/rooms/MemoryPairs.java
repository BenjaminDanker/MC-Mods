package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Pairs: a wall of face-down tiles. Hit two to turn them over; a matching pair stays up, a miss turns back over a
 * moment later. Find every pair before the time runs out.
 */
public final class MemoryPairs extends BaseRoom {
    private static final Block[] FACES = {Blocks.EMERALD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.GOLD_BLOCK, Blocks.REDSTONE_BLOCK,
        Blocks.LAPIS_BLOCK, Blocks.IRON_BLOCK, Blocks.RAW_GOLD_BLOCK, Blocks.AMETHYST_BLOCK, Blocks.PUMPKIN, Blocks.MELON,
        Blocks.HAY_BLOCK, Blocks.BOOKSHELF, Blocks.SLIME_BLOCK, Blocks.HONEYCOMB_BLOCK, Blocks.PRISMARINE, Blocks.PURPUR_BLOCK,
        Blocks.TNT, Blocks.SEA_LANTERN};
    private final Random random = new Random();
    private int columns;
    private int rows;
    private int[] faces;
    private boolean[] up;
    private int first = -1;
    private int second = -1;
    private long hideAt;
    private long now;

    public String id() { return "pairs"; }
    public String title() { return "Pairs"; }
    public String explain() {
        return "Hit a tile to turn it over, then another. A matching pair stays face up; a miss turns back. Find every pair.";
    }

    int pairs() { return Math.min(FACES.length, 6 + tier); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(40 + pairs() * 9);
    }

    @Override
    protected void build() {
        int cells = pairs() * 2;
        columns = (int) Math.ceil(Math.sqrt(cells * 1.5));
        rows = (cells + columns - 1) / columns;
        List<Integer> deck = new ArrayList<>();
        for (int i = 0; i < pairs(); i++) {
            deck.add(i);
            deck.add(i);
        }
        Collections.shuffle(deck, random);
        faces = deck.stream().mapToInt(Integer::intValue).toArray();
        up = new boolean[faces.length];
        arena.fill(-3, 0, -3, columns + 2, 0, 4, Blocks.POLISHED_ANDESITE.defaultBlockState());
        arena.fill(-1, 1, 6, columns, rows + 2, 6, Blocks.DEEPSLATE_BRICKS.defaultBlockState());
        for (int i = 0; i < faces.length; i++) {
            draw(i);
        }
        com.silver.arcade.realm.deco.TombLook.build(arena, tier, columns, rows);
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, columns / 2.0 + i++, 1, 1.5, 0F);
        }
    }

    private int xOf(int i) { return i % columns; }
    private int yOf(int i) { return 2 + rows - 1 - i / columns; }

    private void draw(int i) {
        arena.set(xOf(i), yOf(i), 5, up[i] || i == first || i == second ? FACES[faces[i]].defaultBlockState()
            : Blocks.CONCRETE.gray().defaultBlockState());
    }

    @Override
    public void tick(long tickInRoom) {
        now = tickInRoom;
        if (second >= 0 && tickInRoom >= hideAt) {
            int a = first;
            int b = second;
            first = -1;
            second = -1;
            draw(a);
            draw(b);
        }
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
        int x = pos.getX() - arena.origin().getX();
        int y = pos.getY() - arena.origin().getY();
        if (pos.getZ() - arena.origin().getZ() != 5 || x < 0 || x >= columns) {
            return false;
        }
        int row = 2 + rows - 1 - y;
        int i = row * columns + x;
        if (row < 0 || row >= rows || i < 0 || i >= faces.length) {
            return false;
        }
        if (finished() || up[i] || i == first || second >= 0) {
            return true;
        }
        arena.level().playSound(null, pos, SoundEvents.BOOK_PAGE_TURN, SoundSource.BLOCKS, 1F, 1F);
        if (first < 0) {
            first = i;
            draw(i);
            return true;
        }
        if (faces[first] == faces[i]) {
            up[first] = true;
            up[i] = true;
            int a = first;
            first = -1;
            draw(a);
            draw(i);
            arena.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1F, 1.2F);
            boolean all = true;
            for (boolean u : up) {
                all &= u;
            }
            if (all) {
                players.values().forEach(this::clear);
                players.values().forEach(p -> Gamemaster.bar(p, "Every pair found!"));
            }
        } else {
            second = i;
            draw(i);
            hideAt = now + 20;
        }
        return true;
    }
}
