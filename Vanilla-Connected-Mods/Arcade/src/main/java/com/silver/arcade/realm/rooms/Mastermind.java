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
 * Mastermind: crack the Gamemaster's colour code. Set your guess on the bottom row (hit a peg to change its colour),
 * then hit the gold button block. Beside each guess: a gold block per peg right in colour and place, an iron block per
 * right colour in the wrong place. Crack it before you run out of rows.
 */
public final class Mastermind extends BaseRoom {
    private static final DyeColor[] COLOURS = {DyeColor.RED, DyeColor.BLUE, DyeColor.LIME, DyeColor.YELLOW, DyeColor.PURPLE,
        DyeColor.ORANGE, DyeColor.WHITE, DyeColor.CYAN};
    private final Random random = new Random();
    private int[] secret;
    private int[] guess;
    private int row;

    public String id() { return "mastermind"; }
    public String title() { return "Crack the Code"; }
    public String explain() {
        return "I've hidden a colour code. Hit the pegs on the bottom row to set your guess, then hit the gold block to check it. "
            + "Gold beside a row means a right colour in the right place; iron means a right colour in the wrong place.";
    }

    int pegs() { return tier >= 6 ? 5 : 4; }
    int colours() { return Math.min(COLOURS.length, 5 + tier / 3); }
    int rows() { return Math.max(7, 10 - tier / 4); }

    public int timeLimitTicks(int tier) {
        this.tier = tier;
        return seconds(60 + rows() * 25);
    }

    private static BlockState wool(int c) {
        return Blocks.WOOL.pick(COLOURS[c]).defaultBlockState();
    }

    /** Row r sits at height 2 + r on the wall (z 6); pegs at x 0..pegs-1; feedback at x pegs+1.. */
    @Override
    protected void build() {
        secret = new int[pegs()];
        for (int i = 0; i < pegs(); i++) {
            secret[i] = random.nextInt(colours());
        }
        guess = new int[pegs()];
        // Vault floor: steel plates (iron and polished deepslate) with riveted seams; the code board is a black steel
        // panel in an iron frame (guesses in wool, answer pegs gold and iron, all bright against it). The gold check
        // button sits on a dark pedestal with a lamp over it.
        int bx1 = pegs() + pegs() + 2, top = 2 + rows() + 1;
        for (int x = -3; x <= bx1 + 1; x++) {
            for (int z = -2; z <= 5; z++) {
                arena.set(x, 0, z, st(Math.floorMod(x, 3) == 0 || Math.floorMod(z, 3) == 0 ? "polished_deepslate"
                    : Math.floorMod(x + z, 2) == 0 ? "iron_block" : "smooth_stone"));
            }
        }
        for (int x = -2; x <= bx1; x++) {
            for (int y = 1; y <= top; y++) {
                arena.set(x, y, 7, st(x == -2 || x == bx1 || y == 1 || y == top ? "iron_block" : "polished_blackstone"));
            }
        }
        arena.set(-2, 2, 6, Blocks.GOLD_BLOCK.defaultBlockState()); // the check button
        arena.set(-2, 1, 6, st("chiseled_polished_blackstone"));
        arena.set(-3, 2, 6, st("redstone_lamp[lit=true]"));
        // A palette strip so players see which colours are in play.
        for (int c = 0; c < colours(); c++) {
            arena.set(-2 - 0, 3 + c, 7, wool(c));
        }
        drawGuess();
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.VaultLook.build(arena, tier, pegs(), rows()));
        int i = 0;
        for (ServerPlayer player : players.values()) {
            arena.put(player, 1.5 + i++, 1, 1.5, 0F);
        }
    }

    private void drawGuess() {
        for (int i = 0; i < pegs(); i++) {
            arena.set(i, 2 + row, 6, wool(guess[i]));
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
        int x = pos.getX() - arena.origin().getX();
        int y = pos.getY() - arena.origin().getY();
        int z = pos.getZ() - arena.origin().getZ();
        if (z != 6) {
            return false;
        }
        if (finished()) {
            return true;
        }
        if (x == -2 && y == 2) {
            check();
            return true;
        }
        if (y == 2 + row && x >= 0 && x < pegs()) {
            guess[x] = (guess[x] + 1) % colours();
            drawGuess();
            arena.level().playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1F, 1F);
            return true;
        }
        return false;
    }

    private void check() {
        int[] s = MastermindCode.score(secret, guess, colours());
        int x = pegs() + 1;
        for (int i = 0; i < s[0]; i++) {
            arena.set(x++, 2 + row, 6, Blocks.GOLD_BLOCK.defaultBlockState());
        }
        for (int i = 0; i < s[1]; i++) {
            arena.set(x++, 2 + row, 6, Blocks.IRON_BLOCK.defaultBlockState());
        }
        if (s[0] == pegs()) {
            players.values().forEach(this::clear);
            players.values().forEach(p -> Gamemaster.bar(p, "Cracked in " + (row + 1) + "!"));
            return;
        }
        row++;
        if (row >= rows()) {
            players.values().forEach(this::fail);
            players.values().forEach(p -> Gamemaster.say(p, "Out of rows. The code was " + describe() + "."));
            return;
        }
        drawGuess();
    }

    private String describe() {
        StringBuilder out = new StringBuilder();
        for (int c : secret) {
            out.append(out.isEmpty() ? "" : ", ").append(COLOURS[c].getName().replace('_', ' '));
        }
        return out.toString();
    }
}
