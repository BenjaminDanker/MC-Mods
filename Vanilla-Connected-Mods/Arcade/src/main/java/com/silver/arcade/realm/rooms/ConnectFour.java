package com.silver.arcade.realm.rooms;

import com.silver.arcade.realm.Gamemaster;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;

/** Connect four against the Gamemaster. Hit a column's button row to drop your piece; four in a row wins. */
public final class ConnectFour extends BaseRoom {
    private final Random random = new Random();
    private final ConnectFourBoard board = new ConnectFourBoard();
    private ServerPlayer player;
    private long botMoveAt = -1;

    public String id() { return "connect4"; }
    public String title() { return "Connect Four"; }
    public String explain() {
        return "Four in a row, against me. Right-click the stone in a column's bottom row to drop your yellow piece. "
            + "Beat me or draw and you pass. I warn you, I get better.";
    }

    public int timeLimitTicks(int tier) {
        return seconds(150);
    }

    @Override
    protected void build() {
        player = players.isEmpty() ? null : players.values().iterator().next(); // none in a preview
        // The board stands on z=6, x 0..6, rows y 2..7; the stone pickers sit on y 1.
        // Casino carpet (red with black diamonds and a gold border) in front of a gilded board: royal blue face, gold
        // frame, gold column pickers to click.
        for (int x = -1; x <= 7; x++) {
            for (int z = 0; z <= 7; z++) {
                boolean border = x == -1 || x == 7 || z == 0;
                int dd = Math.abs(Math.floorMod(x, 4) - 2) + Math.abs(Math.floorMod(z, 4) - 2);
                arena.set(x, 0, z, st(border ? "gold_block" : dd == 0 ? "gold_block" : dd == 1 ? "black_wool" : "red_wool"));
            }
        }
        for (int x = -1; x <= 7; x++) {
            for (int y = 1; y <= 8; y++) {
                boolean frame = x == -1 || x == 7 || y == 1 || y == 8;
                arena.set(x, y, 7, frame ? Blocks.GOLD_BLOCK.defaultBlockState() : Blocks.CONCRETE.blue().defaultBlockState());
            }
        }
        for (int c = 0; c < ConnectFourBoard.COLS; c++) {
            arena.set(c, 1, 6, Blocks.GOLD_BLOCK.defaultBlockState());
            for (int r = 0; r < ConnectFourBoard.ROWS; r++) {
                arena.set(c, 2 + r, 6, Blocks.STAINED_GLASS.white().defaultBlockState());
            }
        }
        com.silver.arcade.realm.deco.Deco.async(arena, () -> com.silver.arcade.realm.deco.CasinoLook.build(arena, tier));
        if (player != null) {
            arena.put(player, 3.5, 1, 2.5, 0F);
        }
    }

    @Override
    public void rejoined(ServerPlayer who) {
        super.rejoined(who);
        if (player != null && player.getUUID().equals(who.getUUID())) {
            player = who;
        }
    }

    @Override
    public boolean use(ServerPlayer who, BlockPos pos) {
        if (who != player || decided(who.getUUID()) || botMoveAt >= 0) {
            return true;
        }
        int col = pos.getX() - arena.origin().getX();
        if (pos.getZ() - arena.origin().getZ() != 6 || col < 0 || col >= ConnectFourBoard.COLS) {
            return false;
        }
        int row = board.drop(col, 1);
        if (row < 0) {
            Gamemaster.bar(who, "That column is full.");
            return true;
        }
        arena.set(col, 2 + row, 6, Blocks.CONCRETE.yellow().defaultBlockState());
        if (board.wins(1)) {
            Gamemaster.say(who, "...Well played.");
            clear(who);
        } else if (board.full()) {
            Gamemaster.say(who, "A draw. I'll allow it.");
            clear(who);
        } else {
            botMoveAt = 0;
        }
        return true;
    }

    @Override
    public void tick(long tickInRoom) {
        if (botMoveAt == 0) {
            botMoveAt = tickInRoom + 15;
        }
        if (botMoveAt > 0 && tickInRoom >= botMoveAt && !finished()) {
            botMoveAt = -1;
            int col = board.botMove(tier, random);
            int row = board.drop(col, 2);
            arena.set(col, 2 + row, 6, Blocks.CONCRETE.red().defaultBlockState());
            if (board.wins(2)) {
                Gamemaster.say(player, "Four. Mine.");
                fail(player);
            } else if (board.full()) {
                Gamemaster.say(player, "A draw. I'll allow it.");
                clear(player);
            }
        }
    }
}
