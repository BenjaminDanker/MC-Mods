package com.silver.arcade.realm.rooms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SlideBoardTest {
    /** Packs a 3x3 board into a long, 4 bits per cell in reading order (values 0..8). */
    private static long encode3(int[] cells) {
        long code = 0;
        for (int i = 0; i < 9; i++) {
            code |= ((long) cells[i]) << (4 * i);
        }
        return code;
    }

    private static long encodeBoard(SlideBoard b) {
        int[] cells = new int[b.size * b.size];
        for (int y = 0; y < b.size; y++) {
            for (int x = 0; x < b.size; x++) {
                cells[y * b.size + x] = b.tiles[x][y];
            }
        }
        return encode3(cells);
    }

    /** Every 3x3 configuration reachable from the solved board (exactly the solvable ones). */
    private static Set<Long> reachable3x3() {
        int[] start = new int[9];
        for (int i = 0; i < 9; i++) {
            start[i] = (i + 1) % 9;
        }
        Set<Long> seen = new HashSet<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        long s = encode3(start);
        seen.add(s);
        queue.add(s);
        int[] dx = {1, -1, 0, 0};
        int[] dy = {0, 0, 1, -1};
        while (!queue.isEmpty()) {
            long cur = queue.poll();
            int[] cells = new int[9];
            int blank = -1;
            for (int i = 0; i < 9; i++) {
                cells[i] = (int) ((cur >>> (4 * i)) & 0xF);
                if (cells[i] == 0) blank = i;
            }
            int bx = blank % 3;
            int by = blank / 3;
            for (int d = 0; d < 4; d++) {
                int nx = bx + dx[d];
                int ny = by + dy[d];
                if (nx < 0 || ny < 0 || nx >= 3 || ny >= 3) continue;
                int other = ny * 3 + nx;
                int[] next = cells.clone();
                next[blank] = next[other];
                next[other] = 0;
                long code = encode3(next);
                if (seen.add(code)) queue.add(code);
            }
        }
        return seen;
    }

    @Test
    void newBoardIsSolved() {
        for (int size = 1; size <= 5; size++) {
            assertTrue(new SlideBoard(size).solved(), "size " + size);
        }
    }

    @Test
    void slideOrthogonallyAdjacentToHoleMoves() {
        SlideBoard b = new SlideBoard(3);
        // hole starts at (2,2); tile at (1,2) is value 8, tile at (2,1) is value 6
        assertTrue(b.slide(1, 2));
        assertEquals(8, b.tiles[2][2]);
        assertEquals(0, b.tiles[1][2]);
        assertEquals(1, b.holeX);
        assertEquals(2, b.holeY);

        assertTrue(b.slide(1, 1));
        assertEquals(5, b.tiles[1][2]);
        assertEquals(0, b.tiles[1][1]);
        assertEquals(1, b.holeX);
        assertEquals(1, b.holeY);
    }

    @Test
    void slideNonAdjacentTileIsRejectedAndBoardUnchanged() {
        SlideBoard b = new SlideBoard(3);
        long before = encodeBoard(b);

        assertFalse(b.slide(1, 1), "diagonal to hole");
        assertFalse(b.slide(0, 0), "far from hole");
        assertFalse(b.slide(0, 2), "two cells away in a row");
        assertFalse(b.slide(2, 2), "the hole itself");

        assertEquals(before, encodeBoard(b));
        assertEquals(2, b.holeX);
        assertEquals(2, b.holeY);
    }

    @Test
    void solvedAfterSlidingBackToStart() {
        SlideBoard b = new SlideBoard(3);
        assertTrue(b.slide(2, 1));
        assertFalse(b.solved());
        assertTrue(b.slide(2, 2));
        assertTrue(b.solved());
    }

    @Test
    void scrambledIsNeverSolved() {
        for (int size : new int[] {3, 4}) {
            for (int moves = 1; moves <= 60; moves++) {
                for (long seed = 0; seed < 200; seed++) {
                    SlideBoard b = SlideBoard.scrambled(size, moves, new Random(seed));
                    assertFalse(b.solved(), "size " + size + " moves " + moves + " seed " + seed);
                }
            }
        }
    }

    @Test
    void scrambled3x3IsAlwaysSolvable() {
        Set<Long> reachable = reachable3x3();
        // 9! / 2 solvable configurations on a 3x3 board
        assertEquals(181440, reachable.size());

        for (int moves = 1; moves <= 60; moves++) {
            for (long seed = 0; seed < 200; seed++) {
                SlideBoard b = SlideBoard.scrambled(3, moves, new Random(seed));
                assertTrue(reachable.contains(encodeBoard(b)),
                        "unsolvable 3x3 at moves " + moves + " seed " + seed);
            }
        }
    }

    @Test
    void tileMultisetIsAlwaysZeroToNMinusOne() {
        for (int size : new int[] {3, 4}) {
            int n = size * size;
            int[] expected = new int[n];
            for (int i = 0; i < n; i++) expected[i] = i;
            for (int moves = 1; moves <= 60; moves++) {
                for (long seed = 0; seed < 50; seed++) {
                    SlideBoard b = SlideBoard.scrambled(size, moves, new Random(seed));
                    int[] values = new int[n];
                    int k = 0;
                    for (int y = 0; y < size; y++) {
                        for (int x = 0; x < size; x++) {
                            values[k++] = b.tiles[x][y];
                        }
                    }
                    Arrays.sort(values);
                    assertArrayEquals(expected, values, "size " + size + " moves " + moves + " seed " + seed);
                }
            }
        }
    }
}
