package com.silver.arcade.realm.rooms;

import java.util.Random;

/**
 * Lights Out: pressing a lamp toggles it and its four neighbours; the board is solved when every lamp is off. Boards
 * are made by pressing random lamps on a dark board, so every board can be solved (in at most that many presses).
 */
final class LightsOutBoard {
    final int size;
    private final boolean[][] lit;

    LightsOutBoard(int size) {
        this.size = size;
        this.lit = new boolean[size][size];
    }

    static LightsOutBoard scrambled(int size, int presses, Random random) {
        LightsOutBoard board = new LightsOutBoard(size);
        for (int attempt = 0; attempt < 20 && board.solved(); attempt++) {
            for (int i = 0; i < presses; i++) {
                board.press(random.nextInt(size), random.nextInt(size));
            }
        }
        if (board.solved()) {
            board.press(random.nextInt(size), random.nextInt(size)); // e.g. a 1x1 board pressed an even number of times
        }
        return board;
    }

    void press(int x, int y) {
        flip(x, y);
        flip(x - 1, y);
        flip(x + 1, y);
        flip(x, y - 1);
        flip(x, y + 1);
    }

    private void flip(int x, int y) {
        if (x >= 0 && y >= 0 && x < size && y < size) {
            lit[x][y] = !lit[x][y];
        }
    }

    boolean lit(int x, int y) {
        return lit[x][y];
    }

    boolean solved() {
        for (boolean[] column : lit) {
            for (boolean on : column) {
                if (on) {
                    return false;
                }
            }
        }
        return true;
    }
}
