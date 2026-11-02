package com.silver.arcade.realm.rooms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Random;
import org.junit.jupiter.api.Test;

class MazeGenTest {
    private static void check(int w, int h, long seed) {
        boolean[][] m = MazeGen.generate(w, h, new Random(seed));
        int W = 2 * w + 1, H = 2 * h + 1;
        assertEquals(W, m.length);
        for (boolean[] col : m) assertEquals(H, col.length);
        for (int x = 0; x < W; x++) { assertTrue(m[x][0]); assertTrue(m[x][H - 1]); }
        for (int y = 0; y < H; y++) { assertTrue(m[0][y]); assertTrue(m[W - 1][y]); }
        assertFalse(m[1][1]);
        assertFalse(m[2 * w - 1][2 * h - 1]);
        boolean[][] seen = new boolean[W][H];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[] {1, 1});
        seen[1][1] = true;
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = c[0] + d[0], ny = c[1] + d[1];
                if (nx >= 0 && ny >= 0 && nx < W && ny < H && !m[nx][ny] && !seen[nx][ny]) {
                    seen[nx][ny] = true;
                    q.add(new int[] {nx, ny});
                }
            }
        }
        for (int cx = 0; cx < w; cx++)
            for (int cy = 0; cy < h; cy++) {
                assertFalse(m[2 * cx + 1][2 * cy + 1]);
                assertTrue(seen[2 * cx + 1][2 * cy + 1], "unreachable cell " + cx + "," + cy);
            }
        assertTrue(seen[2 * w - 1][2 * h - 1]);
        int passages = 0;
        for (int x = 1; x < W - 1; x++)
            for (int y = 1; y < H - 1; y++)
                if ((x + y) % 2 == 1 && !m[x][y]) passages++;
        assertEquals(w * h - 1, passages);
    }

    @Test void sizesAndSeeds() {
        int[][] sizes = {{1, 1}, {2, 3}, {10, 10}, {30, 30}};
        for (int[] s : sizes) for (long seed = 1; seed <= 5; seed++) check(s[0], s[1], seed);
    }

    @Test void sameSeedSameMaze() {
        assertArrayEquals(MazeGen.generate(8, 8, new Random(7)), MazeGen.generate(8, 8, new Random(7)));
    }
}
