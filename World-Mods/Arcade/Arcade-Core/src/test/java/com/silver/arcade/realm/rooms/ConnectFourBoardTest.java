package com.silver.arcade.realm.rooms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class ConnectFourBoardTest {
    @Test void dropUndoFull() {
        ConnectFourBoard b = new ConnectFourBoard();
        assertEquals(0, b.drop(2, 1));
        assertEquals(1, b.drop(2, 2));
        b.undo(2);
        assertEquals(0, b.cells[2][1]);
        assertEquals(1, b.cells[2][0]);
        for (int i = 0; i < 6; i++) assertEquals(i, b.drop(0, 1 + i % 2));
        assertFalse(b.canDrop(0));
        assertEquals(-1, b.drop(0, 1));
        assertFalse(b.canDrop(-1));
        assertFalse(b.canDrop(7));
        assertFalse(b.full());
        for (int c = 0; c < 7; c++) while (b.canDrop(c)) b.drop(c, 1);
        assertTrue(b.full());
    }

    @Test void winsHorizontal() {
        ConnectFourBoard b = new ConnectFourBoard();
        for (int c = 1; c <= 3; c++) b.drop(c, 1);
        assertFalse(b.wins(1));
        b.drop(4, 1);
        assertTrue(b.wins(1));
        assertFalse(b.wins(2));
    }

    @Test void winsVertical() {
        ConnectFourBoard b = new ConnectFourBoard();
        for (int i = 0; i < 3; i++) b.drop(5, 2);
        assertFalse(b.wins(2));
        b.drop(5, 2);
        assertTrue(b.wins(2));
    }

    @Test void winsDiagonals() {
        ConnectFourBoard up = new ConnectFourBoard();
        for (int i = 0; i < 4; i++) { up.cells[i][i] = 1; }
        assertTrue(up.wins(1));
        ConnectFourBoard down = new ConnectFourBoard();
        for (int i = 0; i < 4; i++) { down.cells[3 + i][3 - i] = 2; }
        assertTrue(down.wins(2));
    }

    @Test void noFalsePositives() {
        ConnectFourBoard b = new ConnectFourBoard();
        assertFalse(b.wins(1));
        for (int c = 0; c < 3; c++) b.drop(c, 1);
        b.drop(3, 2);
        for (int c = 4; c < 7; c++) b.drop(c, 1);
        assertFalse(b.wins(1)); // broken by the 2
        b.cells[0][1] = 1; b.cells[1][2] = 1; b.cells[2][3] = 1; // diagonal of three
        assertFalse(b.wins(1));
    }

    @Test void takesImmediateWin() {
        for (int tier = 1; tier <= 7; tier++) {
            ConnectFourBoard b = new ConnectFourBoard();
            for (int c = 0; c < 3; c++) b.drop(c, 2);
            b.drop(6, 1); b.drop(6, 1);
            assertEquals(3, b.botMove(tier, new Random(tier)));
        }
    }

    @Test void blocksPlayerWin() {
        for (int tier = 2; tier <= 7; tier++) {
            ConnectFourBoard b = new ConnectFourBoard();
            for (int c = 0; c < 3; c++) b.drop(c, 1);
            b.drop(5, 2); b.drop(6, 2);
            assertEquals(3, b.botMove(tier, new Random(tier)), "tier " + tier);
        }
    }

    @Test void neverFullColumn() {
        ConnectFourBoard b = new ConnectFourBoard();
        // fill column 3 and 2 without a win: alternate players
        for (int c : new int[] {3, 2}) for (int i = 0; i < 6; i++) b.drop(c, 1 + (i / 2 + c) % 2);
        assertFalse(b.canDrop(3));
        if (b.wins(1) || b.wins(2)) return;
        for (int tier = 1; tier <= 6; tier++)
            for (long s = 0; s < 20; s++) assertTrue(b.canDrop(b.botMove(tier, new Random(s))));
    }

    private static int play(int botTierP2, int tierP1, Random r, boolean p1Random) {
        ConnectFourBoard b = new ConnectFourBoard();
        // player 1 moves first using the same bot logic as player 2 via swapped board
        for (int turn = 0; turn < 42; turn++) {
            if (turn % 2 == 0) {
                int c;
                if (p1Random) {
                    do { c = r.nextInt(7); } while (!b.canDrop(c));
                } else {
                    c = swappedMove(b, tierP1, r);
                }
                b.drop(c, 1);
                if (b.wins(1)) return 1;
            } else {
                b.drop(b.botMove(botTierP2, r), 2);
                if (b.wins(2)) return 2;
            }
            if (b.full()) break;
        }
        return 0;
    }

    private static int swappedMove(ConnectFourBoard b, int tier, Random r) {
        ConnectFourBoard s = new ConnectFourBoard();
        for (int c = 0; c < 7; c++) for (int row = 0; row < 6; row++) s.cells[c][row] = b.cells[c][row] == 0 ? 0 : 3 - b.cells[c][row];
        return s.botMove(tier, r);
    }

    @Test void tier6BeatsRandom() {
        Random r = new Random(42);
        int wins = 0;
        for (int g = 0; g < 30; g++) if (play(6, 0, r, true) == 2) wins++;
        assertTrue(wins >= 20, "wins " + wins);
    }

    @Test void tier7BeatsTier1() {
        Random r = new Random(99);
        int win7 = 0, win1 = 0;
        for (int g = 0; g < 10; g++) {
            int res = play(7, 1, r, false);
            if (res == 2) win7++; else if (res == 1) win1++;
        }
        assertTrue(win7 > win1, win7 + " vs " + win1);
    }
}
