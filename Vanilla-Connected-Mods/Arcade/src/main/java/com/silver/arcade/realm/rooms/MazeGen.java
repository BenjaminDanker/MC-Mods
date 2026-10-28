package com.silver.arcade.realm.rooms;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/** A perfect maze (one path between any two cells) by depth-first backtracking. Pure, so it is tested alone. */
final class MazeGen {
    private MazeGen() {
    }

    /**
     * Cells form a w x h grid; the result is a (2w+1) x (2h+1) grid of walls (true) and paths (false), with the
     * entrance at (1,1) and the exit at (2w-1, 2h-1).
     */
    static boolean[][] generate(int w, int h, Random random) {
        boolean[][] wall = new boolean[2 * w + 1][2 * h + 1];
        for (boolean[] column : wall) {
            java.util.Arrays.fill(column, true);
        }
        boolean[][] seen = new boolean[w][h];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[] {0, 0});
        seen[0][0] = true;
        wall[1][1] = false;
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            List<int[]> options = new ArrayList<>();
            for (int[] s : steps) {
                int nx = cell[0] + s[0];
                int ny = cell[1] + s[1];
                if (nx >= 0 && ny >= 0 && nx < w && ny < h && !seen[nx][ny]) {
                    options.add(new int[] {nx, ny});
                }
            }
            if (options.isEmpty()) {
                stack.pop();
                continue;
            }
            Collections.shuffle(options, random);
            int[] next = options.get(0);
            seen[next[0]][next[1]] = true;
            wall[cell[0] + next[0] + 1][cell[1] + next[1] + 1] = false;
            wall[2 * next[0] + 1][2 * next[1] + 1] = false;
            stack.push(next);
        }
        return wall;
    }
}
