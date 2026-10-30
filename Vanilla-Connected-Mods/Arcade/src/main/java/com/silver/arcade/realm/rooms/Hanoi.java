package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Tower of Hanoi: move the whole stack of rings from the left post to the right one. Hit a post's gold base to lift
 * its top ring, then another post's base to drop it there. A ring never goes on a smaller one.
 */
public final class Hanoi extends BaseRoom {
    private static final DyeColor[] COLOURS = {DyeColor.RED, DyeColor.ORANGE, DyeColor.YELLOW, DyeColor.LIME, DyeColor.CYAN, DyeColor.BLUE};
    @SuppressWarnings("unchecked")
    private final Deque<Integer>[] posts = new Deque[] {new ArrayDeque<>(), new ArrayDeque<>(), new ArrayDeque<>()};
    private int rings;
    private int held = -1;
    private int moves;

    public String id() { return "hanoi"; }
    public String title() { return "The Tower"; }
    public String explain() {
        return "Move the whole stack from the left post to the right post. Right-click a post's gold base to lift its top ring, then "
            + "another base to put it down. A ring can never sit on a smaller one.";
    }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        rings = Math.min(6, 3 + tier / 3);
        return seconds(40 + ((1 << rings) - 1) * 3);
    }

    /** Facing the posts (+z), +x is the player's left: post 0 (the start) on the left, post 2 (the goal) on the right. */
    private int postX(int p) { return (1 - p) * (2 * rings + 4); }

    @Override
    protected void build() {
        int half = 2 * rings + 4 + rings + 2;
        // The toy-shop floor: dark spruce boards with a border of red, yellow and blue building blocks, so the gold
        // bases (the buttons) shine.
        for (int x = -half; x <= half; x++) {
            for (int z = -4; z <= 3; z++) {
                boolean edge = Math.abs(x) == half || z == -4;
                arena.set(x, 0, z, st(edge ? new String[] {"red_concrete", "yellow_concrete", "blue_concrete"}[Math.floorMod(x + z, 3)]
                    : Math.floorMod(x, 2) == 0 ? "spruce_planks" : "dark_oak_planks"));
            }
        }
        for (int p = 0; p < 3; p++) {
            arena.set(postX(p), 0, 2, Blocks.GOLD_BLOCK.defaultBlockState());
            // a lit ring round each gold base (floor level, not clickable) and a pale birch post that shows up
            // against the dark ring wall
            arena.set(postX(p) - 1, 0, 2, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            arena.set(postX(p) + 1, 0, 2, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            arena.set(postX(p), 0, 1, Blocks.PEARLESCENT_FROGLIGHT.defaultBlockState());
            arena.fill(postX(p), 1, 3, postX(p), rings + 2, 3, Blocks.BIRCH_FENCE.defaultBlockState());
        }
        for (int r = rings; r >= 1; r--) {
            posts[0].push(r);
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.ToyshopLook.build(arena, tier, rings));
        draw();
        int n = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 0.5 + n++, 1, -2.5, 0F);
        }
    }

    /** Ring r is a bar of 2r+1 wool at its height on the wall behind its post (z 4). */
    private void draw() {
        for (int p = 0; p < 3; p++) {
            for (int y = 1; y <= rings + 1; y++) {
                for (int x = -rings; x <= rings; x++) {
                    arena.set(postX(p) + x, y, 4, Blocks.DARK_OAK_PLANKS.defaultBlockState());
                }
            }
            int y = 1;
            var bottomUp = new java.util.ArrayList<>(posts[p]);
            java.util.Collections.reverse(bottomUp);
            for (int r : bottomUp) {
                for (int x = -r; x <= r; x++) {
                    arena.set(postX(p) + x, y, 4, Blocks.WOOL.pick(COLOURS[(r - 1) % COLOURS.length]).defaultBlockState());
                }
                y++;
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
        if (pos.getY() != arena.origin().getY() || pos.getZ() - arena.origin().getZ() != 2) {
            return false;
        }
        int p = -1;
        for (int i = 0; i < 3; i++) {
            if (pos.getX() - arena.origin().getX() == postX(i)) {
                p = i;
            }
        }
        if (p < 0) {
            return false;
        }
        if (finished()) {
            return true;
        }
        if (held < 0) {
            if (posts[p].isEmpty()) {
                return true;
            }
            held = p;
            Gamemaster.bar(player, "Lifted ring " + posts[p].peek() + ". Hit another base to put it down.");
            return true;
        }
        int ring = posts[held].peek();
        if (p != held) {
            if (!posts[p].isEmpty() && posts[p].peek() < ring) {
                Gamemaster.bar(player, "That ring is too big to go there.");
                held = -1;
                return true;
            }
            posts[p].push(posts[held].pop());
            moves++;
            arena.level().playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1F, 1F);
            draw();
        }
        held = -1;
        if (posts[2].size() == rings) {
            players.values().forEach(this::clear);
            players.values().forEach(q -> Gamemaster.bar(q, "Done in " + moves + " moves (the best is " + ((1 << rings) - 1) + ")."));
        }
        return true;
    }
}
