package com.silver.arcade.realm.rooms;

/** Mastermind scoring: exact = right colour, right place; near = right colour, wrong place (each peg counted once). */
final class MastermindCode {
    private MastermindCode() {
    }

    static int[] score(int[] secret, int[] guess, int colours) {
        int exact = 0;
        int[] a = new int[colours];
        int[] b = new int[colours];
        for (int i = 0; i < secret.length; i++) {
            if (secret[i] == guess[i]) {
                exact++;
            } else {
                a[secret[i]]++;
                b[guess[i]]++;
            }
        }
        int near = 0;
        for (int c = 0; c < colours; c++) {
            near += Math.min(a[c], b[c]);
        }
        return new int[] {exact, near};
    }

    /**
     * The same score peg by peg: 2 (gold) where the peg is right in colour and place, 1 (iron) where its colour is in the
     * code elsewhere and not already used up by other pegs, 0 otherwise. The totals always equal {@link #score}.
     */
    static int[] marks(int[] secret, int[] guess, int colours) {
        int[] marks = new int[guess.length];
        int[] left = new int[colours]; // colours of the code not matched exactly
        for (int i = 0; i < secret.length; i++) {
            if (secret[i] == guess[i]) {
                marks[i] = 2;
            } else {
                left[secret[i]]++;
            }
        }
        for (int i = 0; i < guess.length; i++) {
            if (marks[i] == 0 && left[guess[i]] > 0) {
                left[guess[i]]--;
                marks[i] = 1;
            }
        }
        return marks;
    }
}
