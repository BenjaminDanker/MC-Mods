package com.silver.arcade.realm.rooms;

import java.util.Random;

/** The sliding-tile puzzle: tiles 1..n*n-1 and a hole (0). Scrambled by random legal slides, so always solvable. */
final class SlideBoard {
    final int size;
    final int[][] tiles;
    int holeX;
    int holeY;

    SlideBoard(int size) {
        this.size = size;
        tiles = new int[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                tiles[x][y] = y * size + x + 1;
            }
        }
        holeX = size - 1;
        holeY = size - 1;
        tiles[holeX][holeY] = 0;
    }

    static SlideBoard scrambled(int size, int moves, Random random) {
        SlideBoard board = new SlideBoard(size);
        int lastX = -1;
        int lastY = -1;
        for (int i = 0; i < moves || board.solved(); i++) {
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            int[] pick = d[random.nextInt(4)];
            int x = board.holeX + pick[0];
            int y = board.holeY + pick[1];
            if (x < 0 || y < 0 || x >= size || y >= size || (x == lastX && y == lastY)) {
                i--;
                continue;
            }
            lastX = board.holeX;
            lastY = board.holeY;
            board.slide(x, y);
        }
        return board;
    }

    /** Slides tile (x, y) into the hole if they touch; true when it moved. */
    boolean slide(int x, int y) {
        if (Math.abs(x - holeX) + Math.abs(y - holeY) != 1) {
            return false;
        }
        tiles[holeX][holeY] = tiles[x][y];
        tiles[x][y] = 0;
        holeX = x;
        holeY = y;
        return true;
    }

    boolean solved() {
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int want = (x == size - 1 && y == size - 1) ? 0 : y * size + x + 1;
                if (tiles[x][y] != want) {
                    return false;
                }
            }
        }
        return true;
    }
}
