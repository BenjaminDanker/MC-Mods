package com.silver.viewextend;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class AdaptiveWorkBudgetTest {
    @Test void controllerRampsWithinLimitsAndBacksOffUnderPressure() {
        var budget = new AdaptiveWorkBudget(256);
        for (int i=0;i<2000;i++) budget.begin(0, 10_000_000, 0, true);
        assertEquals(256, budget.reads());
        budget.begin(100, 48_000_000, 1, true);
        assertEquals(192, budget.reads());
        assertEquals(400_000, budget.allowance());
        assertTrue(budget.hasTime(400_099)); assertFalse(budget.hasTime(400_100));
        for (int i=0;i<100;i++) budget.begin(0, 60_000_000, 1, true);
        assertEquals(1, budget.reads()); assertEquals(100_000, budget.allowance());
    }
    @Test void lookaheadRespondsToLatencyAndConsumptionWithoutExceedingMemoryCeiling() {
        var budget = new AdaptiveWorkBudget(256);
        assertEquals(4, budget.lookahead(.01f,128));
        int fast = budget.lookahead(10,128);
        budget.completed(20);
        assertTrue(budget.lookahead(10,128)>fast);
        assertEquals(128,budget.lookahead(64,128));
        assertTrue(budget.lookahead(Float.NaN,128)>=4);
    }
    @Test void diskWindowLetsOneReaderUseFastIoAndShrinksWhenIoIsSlow() {
        var budget = new AdaptiveWorkBudget(256);
        assertEquals(32, budget.diskStarts(0));
        assertEquals(0, budget.diskStarts(32));
        for (int i = 0; i < 200; i++) budget.observeDisk(1_000_000, budget.diskWindow());
        assertEquals(256, budget.diskWindow());
        budget.observeDisk(-1, 0);
        assertEquals(256, budget.diskWindow());
        for (int i = 0; i < 100; i++) budget.observeDisk(80_000_000, 0);
        assertEquals(AdaptiveWorkBudget.DISK_MIN_WINDOW, budget.diskWindow());
        // An idle, fast disk does not grow the window without demand.
        budget.observeDisk(1_000_000, 0);
        assertEquals(AdaptiveWorkBudget.DISK_MIN_WINDOW, budget.diskWindow());
    }
}
