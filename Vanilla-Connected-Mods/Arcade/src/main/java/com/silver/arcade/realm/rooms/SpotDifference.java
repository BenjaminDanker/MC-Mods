package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Spot the difference: two pictures side by side, the same but for a few blocks. Hit each difference on the right-hand
 * picture. Three wrong hits and the room is lost.
 */
public final class SpotDifference extends BaseRoom {
    private static final Block[] PALETTE = {Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.orange(), Blocks.DYED_TERRACOTTA.yellow(), Blocks.DYED_TERRACOTTA.lime(),
        Blocks.DYED_TERRACOTTA.lightBlue(), Blocks.DYED_TERRACOTTA.red(), Blocks.DYED_TERRACOTTA.brown(), Blocks.DYED_TERRACOTTA.green()};
    private final Random random = new Random();
    private int size;
    private int[][] left;
    private int[][] right;
    private final Set<Integer> differences = new HashSet<>();
    private final Set<Integer> found = new HashSet<>();
    private int mistakes;

    public String id() { return "spot"; }
    public String title() { return "Spot the Difference"; }
    public String explain() {
        return "The two pictures match, except for a few blocks. Hit every difference on the right-hand picture. Three wrong hits and you lose.";
    }

    int count() { return Math.min(9, 3 + tier / 2); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        size = Math.min(11, 7 + tier / 3);
        return seconds(30 + count() * 12);
    }

    private int leftX(int x) { return -size - 1 + x; }
    private int rightX(int x) { return 2 + x; }

    @Override
    protected void build() {
        int colours = Math.min(PALETTE.length, 4 + tier / 3);
        left = new int[size][size];
        // Patches of colour, so the picture has shapes rather than noise.
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                left[x][y] = (x > 0 && random.nextInt(3) > 0) ? left[x - 1][y] : (y > 0 && random.nextBoolean() ? left[x][y - 1] : random.nextInt(colours));
            }
        }
        right = new int[size][size];
        for (int x = 0; x < size; x++) {
            right[x] = left[x].clone();
        }
        while (differences.size() < count()) {
            int x = random.nextInt(size);
            int y = random.nextInt(size);
            if (differences.add(x * 100 + y)) {
                right[x][y] = (left[x][y] + 1 + random.nextInt(colours - 1)) % colours;
            }
        }
        arena.fill(-size - 3, 0, -4, size + 4, 0, 4, Blocks.POLISHED_ANDESITE.defaultBlockState());
        arena.fill(-size - 2, 1, 7, size + 3, size + 3, 7, Blocks.SPRUCE_PLANKS.defaultBlockState());
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                arena.set(leftX(x), 2 + y, 6, PALETTE[left[x][y]].defaultBlockState());
                arena.set(rightX(x), 2 + y, 6, PALETTE[right[x][y]].defaultBlockState());
            }
        }
        int n = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 0.5 + n++, 1, -1.5, 0F);
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
        int x = pos.getX() - arena.origin().getX() - 2;
        int y = pos.getY() - arena.origin().getY() - 2;
        if (pos.getZ() - arena.origin().getZ() != 6 || x < 0 || x >= size || y < 0 || y >= size) {
            return false;
        }
        if (finished() || found.contains(x * 100 + y)) {
            return true;
        }
        if (differences.contains(x * 100 + y)) {
            found.add(x * 100 + y);
            arena.set(rightX(x), 2 + y, 5, Blocks.GLOWSTONE.defaultBlockState()); // a marker in front of the found block
            arena.level().playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 1F, 1.2F);
            players.values().forEach(p -> Gamemaster.bar(p, found.size() + " of " + differences.size() + " found"));
            if (found.size() == differences.size()) {
                players.values().forEach(this::clear);
            }
        } else {
            mistakes++;
            arena.level().playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 1F, 1F);
            players.values().forEach(p -> Gamemaster.bar(p, "Not that one. " + (3 - mistakes) + " mistakes left."));
            if (mistakes >= 3) {
                players.values().forEach(this::fail);
            }
        }
        return true;
    }
}
