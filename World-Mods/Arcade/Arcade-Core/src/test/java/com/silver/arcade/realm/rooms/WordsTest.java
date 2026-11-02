package com.silver.arcade.realm.rooms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

class WordsTest {
    @Test void accuracyIdentical() { assertEquals(1.0, Words.accuracy("hello world", "hello world")); }

    @Test void accuracyEmpty() {
        assertEquals(1.0, Words.accuracy("", ""));
        assertEquals(0.0, Words.accuracy("abc", ""));
    }

    @Test void accuracyDifferent() { assertTrue(Words.accuracy("aaaaaaaaaa", "bbbbbbbbbb") < 0.1); }

    @Test void accuracyOneTypo() {
        String a = "abcdefghijklmnopqrst";
        String b = "abcdefghijXlmnopqrst";
        assertEquals(20, a.length());
        assertEquals(0.95, Words.accuracy(a, b), 1e-9);
    }

    @Test void lineLowTiers() {
        Random r = new Random(3);
        for (int tier = 1; tier < 10; tier++)
            for (int count : new int[] {1, 5, 12}) {
                String line = Words.line(r, count, tier);
                assertEquals(count, line.split(" ").length);
                assertTrue(line.matches("[a-z ]+"), line);
            }
    }

    @Test void lineHighTierCount() {
        String line = Words.line(new Random(1), 15, 12);
        assertEquals(15, line.split(" ").length);
    }
}
