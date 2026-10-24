package com.silver.elementchoice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AscensionTest {
    @Test
    void theRiteIsOpenOnlyInsideItsWindow() {
        Ascension rite = new Ascension();
        UUID player = UUID.randomUUID();
        assertFalse(rite.armed(player, 0));
        rite.arm(player, 100);
        assertTrue(rite.armed(player, 100));
        assertTrue(rite.armed(player, 100 + Ascension.WINDOW_TICKS - 1));
        assertFalse(rite.armed(player, 100 + Ascension.WINDOW_TICKS));
        assertFalse(rite.armed(player, 100), "once lapsed it stays closed");
    }

    @Test
    void clearingEndsTheRiteAndOtherPlayersAreUntouched() {
        Ascension rite = new Ascension();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        rite.arm(first, 0);
        rite.arm(second, 0);
        rite.clear(first);
        assertFalse(rite.armed(first, 1));
        assertTrue(rite.armed(second, 1));
    }
}
