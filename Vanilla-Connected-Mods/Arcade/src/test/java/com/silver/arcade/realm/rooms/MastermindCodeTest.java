package com.silver.arcade.realm.rooms;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MastermindCodeTest {
    private static int[] score(int[] secret, int[] guess, int colours) {
        return MastermindCode.score(secret, guess, colours);
    }

    @Test
    void allExact() {
        assertArrayEquals(new int[] {4, 0}, score(new int[] {0, 1, 2, 3}, new int[] {0, 1, 2, 3}, 4));
    }

    @Test
    void noneMatching() {
        assertArrayEquals(new int[] {0, 0}, score(new int[] {0, 0, 0, 0}, new int[] {1, 1, 1, 1}, 4));
    }

    @Test
    void allNearWhenGuessIsPermutation() {
        assertArrayEquals(new int[] {0, 4}, score(new int[] {0, 1, 2, 3}, new int[] {1, 0, 3, 2}, 4));
    }

    @Test
    void duplicatesInSecretOnly() {
        // exact at positions 1 and 3. Remaining secret {1,2} vs remaining guess {2,3}: the 2 is near, and the
        // unmatched secret 1 has no partner in the guess, so it is not counted
        assertArrayEquals(new int[] {2, 1}, score(new int[] {1, 1, 2, 3}, new int[] {2, 1, 3, 3}, 4));
    }

    @Test
    void duplicatesInSecretOnlyNoExact() {
        // remaining secret {1,2,2,3} vs guess {2,1,1,1}: one 1 and one 2 are near, each counted once
        assertArrayEquals(new int[] {0, 2}, score(new int[] {1, 2, 2, 3}, new int[] {2, 1, 1, 1}, 4));
    }

    @Test
    void duplicatesInGuessOnly() {
        // exact at position 0; the three 0s in the guess have no partner left in the secret
        assertArrayEquals(new int[] {1, 0}, score(new int[] {0, 1, 2, 3}, new int[] {0, 0, 0, 0}, 4));
    }

    @Test
    void duplicatesOnBothSidesCountOncePerPeg() {
        assertArrayEquals(new int[] {0, 4}, score(new int[] {0, 0, 1, 1}, new int[] {1, 1, 0, 0}, 2));
    }

    @Test
    void mixedExactAndNearWithDuplicates() {
        // exact at positions 0 and 2; remaining secret {0,1} vs guess {1,0} gives two near
        assertArrayEquals(new int[] {2, 2}, score(new int[] {0, 0, 1, 1}, new int[] {0, 1, 1, 0}, 2));
    }
}
