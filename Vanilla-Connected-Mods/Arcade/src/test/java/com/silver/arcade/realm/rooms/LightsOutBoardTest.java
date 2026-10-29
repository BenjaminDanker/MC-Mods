package com.silver.arcade.realm.rooms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LightsOutBoardTest {
    private static int mask(LightsOutBoard b) {
        int m = 0;
        for (int x = 0; x < b.size; x++) {
            for (int y = 0; y < b.size; y++) {
                if (b.lit(x, y)) m |= 1 << (x * b.size + y);
            }
        }
        return m;
    }

    /** Every state reachable by pressing some subset of lamps on a dark board (press order is irrelevant). */
    private static Set<Integer> reachable(int size) {
        int n = size * size;
        Set<Integer> states = new HashSet<>();
        for (int subset = 0; subset < (1 << n); subset++) {
            LightsOutBoard b = new LightsOutBoard(size);
            for (int i = 0; i < n; i++) {
                if ((subset & (1 << i)) != 0) b.press(i / size, i % size);
            }
            states.add(mask(b));
        }
        return states;
    }

    @Test
    void newBoardIsSolved() {
        for (int size = 1; size <= 6; size++) {
            LightsOutBoard b = new LightsOutBoard(size);
            assertTrue(b.solved());
            assertEquals(0, mask(b));
        }
    }

    @Test
    void centerPressTogglesFivePlus() {
        LightsOutBoard b = new LightsOutBoard(5);
        b.press(2, 2);
        assertFalse(b.solved());
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                boolean expected = (x == 2 && y == 2) || (x == 1 && y == 2) || (x == 3 && y == 2)
                        || (x == 2 && y == 1) || (x == 2 && y == 3);
                assertEquals(expected, b.lit(x, y), "lamp " + x + "," + y);
            }
        }
    }

    @Test
    void cornerAndEdgePressesClipToBoard() {
        int n = 4;
        LightsOutBoard b = new LightsOutBoard(n);
        b.press(0, 0);
        assertEquals(3, Integer.bitCount(mask(b)));
        assertTrue(b.lit(0, 0));
        assertTrue(b.lit(1, 0));
        assertTrue(b.lit(0, 1));

        b = new LightsOutBoard(n);
        b.press(n - 1, n - 1);
        assertEquals(3, Integer.bitCount(mask(b)));
        assertTrue(b.lit(n - 1, n - 1));
        assertTrue(b.lit(n - 2, n - 1));
        assertTrue(b.lit(n - 1, n - 2));

        b = new LightsOutBoard(n);
        b.press(n - 1, 0);
        assertEquals(3, Integer.bitCount(mask(b)));
        assertTrue(b.lit(n - 1, 0) && b.lit(n - 2, 0) && b.lit(n - 1, 1));

        b = new LightsOutBoard(n);
        b.press(0, n - 1);
        assertEquals(3, Integer.bitCount(mask(b)));
        assertTrue(b.lit(0, n - 1) && b.lit(1, n - 1) && b.lit(0, n - 2));

        b = new LightsOutBoard(n);
        b.press(0, 2);
        assertEquals(4, Integer.bitCount(mask(b)));
        assertTrue(b.lit(0, 2) && b.lit(0, 1) && b.lit(0, 3) && b.lit(1, 2));

        b = new LightsOutBoard(n);
        b.press(2, 3);
        assertEquals(4, Integer.bitCount(mask(b)));
        assertTrue(b.lit(2, 3) && b.lit(1, 3) && b.lit(3, 3) && b.lit(2, 2));
    }

    @Test
    void pressingTwiceRestoresBoard() {
        for (int size = 2; size <= 5; size++) {
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    LightsOutBoard b = LightsOutBoard.scrambled(size, 5, new Random(size * 100L + x * 10 + y));
                    int before = mask(b);
                    b.press(x, y);
                    b.press(x, y);
                    assertEquals(before, mask(b));
                }
            }
        }
    }

    @Test
    void scrambledNeverSolved() {
        for (int size = 2; size <= 5; size++) {
            for (int presses : new int[] {1, 2, 3, 5, 8, 20}) {
                for (long seed = 0; seed < 100; seed++) {
                    assertFalse(LightsOutBoard.scrambled(size, presses, new Random(seed)).solved(),
                            "size " + size + " presses " + presses + " seed " + seed);
                }
            }
        }
    }

    @Test
    void scrambledBoardsAreSolvable() {
        for (int size : new int[] {3, 4}) {
            Set<Integer> reachable = reachable(size);
            for (int presses : new int[] {1, 2, 3, 4, 7, 12, 30}) {
                for (long seed = 0; seed < 200; seed++) {
                    LightsOutBoard b = LightsOutBoard.scrambled(size, presses, new Random(seed));
                    assertTrue(reachable.contains(mask(b)),
                            "unsolvable: size " + size + " presses " + presses + " seed " + seed);
                }
            }
        }
    }
}
