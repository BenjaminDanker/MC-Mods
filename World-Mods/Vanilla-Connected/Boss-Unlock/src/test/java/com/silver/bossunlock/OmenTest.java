package com.silver.bossunlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OmenTest {
    private static final List<String> ELEMENTS = List.of("air", "water", "earth", "fire");
    private static final List<String> GIVEAWAYS = List.of("element", "air", "water", "earth", "fire");

    @Test
    void eachElementHasItsOwnLine() {
        Set<String> lines = new HashSet<>();
        for (String element : ELEMENTS) {
            lines.add(BossUnlockMod.omen(element));
        }
        assertEquals(ELEMENTS.size(), lines.size(), "the four omens must be distinct");
    }

    @Test
    void noLineNamesAnElement() {
        for (String element : ELEMENTS) {
            String line = BossUnlockMod.omen(element).toLowerCase(Locale.ROOT);
            assertFalse(line.isBlank());
            for (String word : GIVEAWAYS) {
                assertFalse(line.contains(word), "omen for " + element + " gives away '" + word + "': " + line);
            }
        }
    }
}
