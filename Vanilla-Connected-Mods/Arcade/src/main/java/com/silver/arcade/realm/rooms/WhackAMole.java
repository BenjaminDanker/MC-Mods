package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;

/** Whack-a-mole: jack o'lanterns pop up out of holes for a moment; hit them before they duck. Reach the score. */
public final class WhackAMole extends BaseRoom {
    private final Random random = new Random();
    private int size;
    private long[] downAt;
    private int score;
    private long nextPop = 40;

    public String id() { return "whack"; }
    public String title() { return "Whack-a-Mole"; }
    public String explain() {
        return "Lanterns pop up out of the holes. Right-click them before they duck back down. Reach the score before time runs out.";
    }

    int target() { return Math.min(40, 12 + tier * 2); }
    int upTicks() { return Math.max(12, 34 - tier * 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        size = tier >= 6 ? 4 : 3;
        return seconds(45 + target());
    }

    @Override
    protected void build() {
        downAt = new long[size * size];
        // A freshly mown fair lawn: light and dark stripes (grass and moss), a packed-earth path where the players
        // stand, and the mole holes stay coarse dirt.
        for (int x = -2; x <= size * 2 + 1; x++) {
            for (int z = -3; z <= size * 2 + 3; z++) {
                arena.set(x, 0, z, st(z <= -1 ? (Math.floorMod(x, 3) == 0 ? "mud_bricks" : "packed_mud")
                    : Math.floorMod(x, 4) < 2 ? "grass_block" : "moss_block"));
            }
        }
        for (int i = 0; i < size * size; i++) {
            arena.set(x(i), 0, z(i), Blocks.COARSE_DIRT.defaultBlockState());
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.HarvestLook.build(arena, tier, size));
        int n = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, size + n++ * 0.6, 1, -1.5, 0F);
        }
    }

    private int x(int i) { return (i % size) * 2; }
    private int z(int i) { return (i / size) * 2 + 1; }

    @Override
    public void tick(long tickInRoom) {
        for (int i = 0; i < downAt.length; i++) {
            if (downAt[i] > 0 && tickInRoom >= downAt[i]) {
                downAt[i] = 0;
                arena.clearBlock(x(i), 1, z(i));
            }
        }
        if (tickInRoom >= nextPop) {
            int i = random.nextInt(downAt.length);
            if (downAt[i] == 0) {
                downAt[i] = tickInRoom + upTicks();
                arena.set(x(i), 1, z(i), Blocks.JACK_O_LANTERN.defaultBlockState());
            }
            nextPop = tickInRoom + Math.max(6, 22 - tier) + random.nextInt(10);
        }
    }

    @Override
    public boolean hit(ServerPlayer player, BlockPos pos) {
        return whack(pos);
    }

    @Override
    public boolean use(ServerPlayer player, BlockPos pos) {
        return whack(pos);
    }

    private boolean whack(BlockPos pos) {
        int dy = pos.getY() - arena.origin().getY();
        for (int i = 0; i < downAt.length; i++) {
            if (pos.getX() - arena.origin().getX() == x(i) && pos.getZ() - arena.origin().getZ() == z(i) && (dy == 1 || dy == 0)) {
                if (dy == 1 && downAt[i] > 0 && !finished()) {
                    downAt[i] = 0;
                    arena.clearBlock(x(i), 1, z(i));
                    score++;
                    arena.level().playSound(null, pos, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 1.8F);
                    players.values().forEach(p -> Gamemaster.bar(p, score + " / " + target()));
                    if (score >= target()) {
                        players.values().forEach(this::clear);
                    }
                }
                return true;
            }
        }
        return false;
    }
}
