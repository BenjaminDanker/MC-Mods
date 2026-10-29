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
}
