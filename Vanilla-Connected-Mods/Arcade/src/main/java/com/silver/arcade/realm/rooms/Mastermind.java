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
 * then hit the glowing copper check button. Beside each guess: a gold block per peg right in colour and place, an iron block per
 * right colour in the wrong place. Crack it before you run out of rows.
 */
public final class Mastermind extends BaseRoom {
    private static final DyeColor[] COLOURS = {DyeColor.RED, DyeColor.BLUE, DyeColor.LIME, DyeColor.YELLOW, DyeColor.PURPLE,
        DyeColor.ORANGE, DyeColor.WHITE, DyeColor.CYAN};
    private final Random random = new Random();
    private int[] secret;
    private int[] guess;
    private int row; // how many guesses have been checked
    /** Checked guesses, newest first, with their {gold, iron} scores. */
    private final java.util.List<int[][]> history = new java.util.ArrayList<>();

    public String id() { return "crack_the_code"; }
    public String title() { return "Crack the Code"; }
    public String explain() {
        return "I've hidden a colour code. Right-click the pegs on the bottom row to set your guess, then right-click the glowing copper button to check it. "
            + "Beside each row, a gold block counts a right colour in the right place and an iron block a right colour in the wrong place. "
            + "They are only counts, in no order: they don't say which peg.";
    }

    // 25 levels in two stages: 4 pegs with 4 to 8 colours (levels 1-12), then 5 pegs with 5 to 8 colours; never fewer
    // than 8 rows (5 pegs, 8 colours and 7 rows was not reliably solvable).
    int pegs() { return tier <= 12 ? 4 : 5; }
    int colours() { return Math.min(COLOURS.length, tier <= 12 ? 4 + tier / 3 : 5 + (tier - 13) / 3); }
    int rows() { return Math.max(8, tier <= 12 ? 10 - tier / 6 : 10 - (tier - 13) / 4); }

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
        java.util.Arrays.fill(guess, -1); // empty pegs until the player picks colours
        // Vault floor: steel plates (iron and polished deepslate) with riveted seams; the code board is a black steel
        // panel in an iron frame (guesses in wool, answer pegs gold and iron, all bright against it). The gold check
        // button sits on a dark pedestal.
        int bx1 = pegs() + pegs() + 2, top = 2 + rows() + 1;
        for (int x = -3; x <= bx1 + 1; x++) {
            for (int z = -2; z <= 7; z++) { // the floor runs under the board too: no gap to fall through
                arena.set(x, 0, z, st(Math.floorMod(x, 3) == 0 || Math.floorMod(z, 3) == 0 ? "polished_deepslate"
                    : Math.floorMod(x + z, 2) == 0 ? "iron_block" : "smooth_stone"));
            }
        }
        for (int x = -2; x <= bx1; x++) {
            for (int y = 1; y <= top; y++) {
                arena.set(x, y, 7, st(x == -2 || x == bx1 || y == 1 || y == top ? "iron_block" : "polished_blackstone"));
            }
        }
        // a steel plinth the peg row stands on (closes the space under the pegs)
        for (int x = -3; x < bx1; x++) {
            arena.set(x, 1, 6, st("polished_deepslate"));
        }
        arena.set(-2, 2, 6, st("waxed_copper_bulb[lit=true]")); // the check button (gold and iron mean scores here)
        arena.set(-2, 1, 6, st("chiseled_polished_blackstone"));
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

    /** The current guess is always the bottom row (y 2); checked guesses sit above it, newest lowest. */
    private void drawGuess() {
        drawRow(2, guess, null);
    }

    private void drawBoard() {
        drawGuess();
        for (int i = 0; i < history.size(); i++) {
            drawRow(3 + i, history.get(i)[0], history.get(i)[1]);
        }
    }

    private void drawRow(int y, int[] pegsRow, int[] score) {
        for (int i = 0; i < pegs(); i++) {
            arena.set(i, y, 6, pegsRow[i] < 0 ? Blocks.TINTED_GLASS.defaultBlockState() : wool(pegsRow[i])); // dark glass: an empty peg
        }
        // Feedback: gold per right colour in the right place, iron per right colour in the wrong place, dark otherwise.
        int x = pegs() + 1;
        int gold = score == null ? 0 : score[0];
        int iron = score == null ? 0 : score[1];
        for (int i = 0; i < pegs(); i++) {
            arena.set(x + i, y, 6, i < gold ? Blocks.GOLD_BLOCK.defaultBlockState()
                : i < gold + iron ? Blocks.IRON_BLOCK.defaultBlockState() : st("polished_blackstone"));
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
        if (y == 2 && x >= 0 && x < pegs()) {
            guess[x] = (guess[x] + 1) % colours();
            drawGuess();
            arena.level().playSound(null, pos, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 1F, 1F);
            return true;
        }
        return false;
    }

    private void check() {
        for (int g : guess) {
            if (g < 0) {
                players.values().forEach(p -> Gamemaster.bar(p, "Give every peg a colour first."));
                return;
            }
        }
        int[] s = MastermindCode.score(secret, guess, colours());
        history.add(0, new int[][] {guess.clone(), s});
        row++;
        if (s[0] == pegs()) {
            drawBoard();
            players.values().forEach(this::clear);
            players.values().forEach(p -> Gamemaster.bar(p, "Cracked in " + row + "!"));
            return;
        }
        if (row >= rows()) {
            drawBoard();
            players.values().forEach(this::fail);
            players.values().forEach(p -> Gamemaster.say(p, "Out of rows. The code was " + describe() + "."));
            return;
        }
        // the new guess starts as a copy of the last one: change only the pegs you want
        drawBoard();
        String said = s[0] + " right place, " + s[1] + " right colour wrong place · " + (rows() - row) + " guesses left";
        players.values().forEach(p -> Gamemaster.bar(p, said));
    }

    private String describe() {
        StringBuilder out = new StringBuilder();
        for (int c : secret) {
            out.append(out.isEmpty() ? "" : ", ").append(COLOURS[c].getName().replace('_', ' '));
        }
        return out.toString();
    }
}
