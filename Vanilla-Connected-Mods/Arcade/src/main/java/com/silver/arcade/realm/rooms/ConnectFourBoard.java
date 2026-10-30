package com.silver.arcade.realm.rooms;

import java.util.Random;

/** Connect-4 rules and a negamax bot with a depth knob. Pure, so it is tested without a server. */
final class ConnectFourBoard {
    static final int COLS = 7;
    static final int ROWS = 6;
    /** 0 empty, 1 player, 2 bot; [col][row], row 0 at the bottom. */
    final int[][] cells = new int[COLS][ROWS];

    boolean canDrop(int col) {
        return col >= 0 && col < COLS && cells[col][ROWS - 1] == 0;
    }

    /** Drops a piece; returns the row it landed in, or -1. */
    int drop(int col, int who) {
        if (!canDrop(col)) {
            return -1;
        }
        for (int row = 0; row < ROWS; row++) {
            if (cells[col][row] == 0) {
                cells[col][row] = who;
                return row;
            }
        }
        return -1;
    }

    void undo(int col) {
        for (int row = ROWS - 1; row >= 0; row--) {
            if (cells[col][row] != 0) {
                cells[col][row] = 0;
                return;
            }
        }
    }

    boolean full() {
        for (int c = 0; c < COLS; c++) {
            if (canDrop(c)) {
                return false;
            }
        }
        return true;
    }

    /** True when {@code who} has four in a row. */
    boolean wins(int who) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                if (cells[c][r] != who) {
                    continue;
                }
                for (int[] d : dirs) {
                    int k = 1;
                    while (k < 4) {
                        int cc = c + d[0] * k;
                        int rr = r + d[1] * k;
                        if (cc < 0 || cc >= COLS || rr < 0 || rr >= ROWS || cells[cc][rr] != who) {
                            break;
                        }
                        k++;
                    }
                    if (k == 4) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Search depth for a tier: random-ish at 1, then deeper (capped at 9). */
    /**
     * The bot's strength by level: 1 plays at random (it takes its own wins); 2 blocks a winning move half the time;
     * 3 always blocks, otherwise mostly random; 4 looks 2 moves ahead with some random play; 5-7 look 3-5 moves ahead
     * with a little random play left. Capped at 7 (5 moves ahead) so a careful human can always win.
     */
    static int depthFor(int level) {
        return Math.max(2, Math.min(5, level - 2));
    }

    /** How often (percent) the bot plays a random move instead of thinking, by level. */
    static int randomPercent(int level) {
        return switch (Math.min(level, 7)) {
            case 1, 2 -> 100;
            case 3 -> 65;
            case 4 -> 35;
            case 5 -> 20;
            case 6 -> 12;
            default -> 8;
        };
    }

    /** The bot's move at this level (see {@link #depthFor}). */
    int botMove(int level, Random random) {
        for (int c = 0; c < COLS; c++) {
            if (canDrop(c)) {
                drop(c, 2);
                boolean win = wins(2);
                undo(c);
                if (win) {
                    return c;
                }
            }
        }
        boolean blocks = level >= 3 || level == 2 && random.nextBoolean();
        for (int c = 0; c < COLS; c++) {
            if (canDrop(c)) {
                drop(c, 1);
                boolean loss = wins(1);
                undo(c);
                if (loss && blocks) {
                    return c;
                }
            }
        }
        if (random.nextInt(100) < randomPercent(level)) {
            int c;
            do {
                c = random.nextInt(COLS);
            } while (!canDrop(c));
            return c;
        }
        int tier = level;
        int best = -1;
        int bestScore = Integer.MIN_VALUE;
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        for (int c : order) {
            if (!canDrop(c)) {
                continue;
            }
            drop(c, 2);
            int score = -negamax(depthFor(tier) - 1, 1, Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
            undo(c);
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    private int negamax(int depth, int who, int alpha, int beta) {
        int other = 3 - who;
        if (wins(other)) {
            return -100000 - depth;
        }
        if (depth == 0 || full()) {
            return evaluate(who);
        }
        int[] order = {3, 2, 4, 1, 5, 0, 6};
        int best = Integer.MIN_VALUE + 1;
        for (int c : order) {
            if (!canDrop(c)) {
                continue;
            }
            drop(c, who);
            int score = -negamax(depth - 1, other, -beta, -alpha);
            undo(c);
            best = Math.max(best, score);
            alpha = Math.max(alpha, score);
            if (alpha >= beta) {
                break;
            }
        }
        return best;
    }

    /** Centre control and open threes, from {@code who}'s side. */
    private int evaluate(int who) {
        int score = 0;
        for (int r = 0; r < ROWS; r++) {
            if (cells[3][r] == who) {
                score += 3;
            } else if (cells[3][r] == 3 - who) {
                score -= 3;
            }
        }
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS; r++) {
                for (int[] d : dirs) {
                    int mine = 0;
                    int theirs = 0;
                    boolean fits = true;
                    for (int k = 0; k < 4; k++) {
                        int cc = c + d[0] * k;
                        int rr = r + d[1] * k;
                        if (cc < 0 || cc >= COLS || rr < 0 || rr >= ROWS) {
                            fits = false;
                            break;
                        }
                        if (cells[cc][rr] == who) {
                            mine++;
                        } else if (cells[cc][rr] != 0) {
                            theirs++;
                        }
                    }
                    if (!fits) {
                        continue;
                    }
                    if (theirs == 0) {
                        score += mine == 3 ? 5 : mine == 2 ? 2 : 0;
                    } else if (mine == 0) {
                        score -= theirs == 3 ? 5 : theirs == 2 ? 2 : 0;
                    }
                }
            }
        }
        return score;
    }
}
