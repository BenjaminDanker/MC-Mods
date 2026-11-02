package com.silver.magitech;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WellRulesTest {
    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();

    @Test
    void aWellYieldsAfterEnoughUsesThenRests() {
        WellRules.State well = new WellRules.State();
        long now = 1_000_000;
        assertEquals(WellRules.Outcome.COUNTED, well.channel(a, now, 3, 400, 60_000));
        assertEquals(WellRules.Outcome.COUNTED, well.channel(b, now + 100, 3, 400, 60_000));
        assertEquals(WellRules.Outcome.YIELDED, well.channel(a, now + 1000, 3, 400, 60_000));
        assertEquals(WellRules.Outcome.COOLING, well.channel(a, now + 2000, 3, 400, 60_000));
        assertEquals(WellRules.Outcome.COUNTED, well.channel(a, now + 61_001, 3, 400, 60_000), "charges again once rested");
    }

    @Test
    void onePlayerCannotSpamCharge() {
        WellRules.State well = new WellRules.State();
        assertEquals(WellRules.Outcome.COUNTED, well.channel(a, 1000, 5, 400, 1000));
        assertEquals(WellRules.Outcome.TOO_SOON, well.channel(a, 1200, 5, 400, 1000));
        assertEquals(WellRules.Outcome.COUNTED, well.channel(a, 1500, 5, 400, 1000));
        assertEquals(2, well.charge);
    }

    @Test
    void benderswhoChannelTogetherShareInTheYield() {
        WellRules.State well = new WellRules.State();
        assertEquals(WellRules.Outcome.COUNTED, well.channel(a, 1000, 3, 400, 60_000, 60_000));
        assertEquals(WellRules.Outcome.COUNTED, well.channel(b, 1500, 3, 400, 60_000, 60_000));
        assertEquals(WellRules.Outcome.YIELDED, well.channel(a, 2000, 3, 400, 60_000, 60_000));
        assertEquals(java.util.Set.of(a, b), new java.util.HashSet<>(well.participants));
    }

    @Test
    void someoneWhoStoppedLongAgoIsNotPartOfTheGroup() {
        WellRules.State well = new WellRules.State();
        assertEquals(WellRules.Outcome.COUNTED, well.channel(b, 1000, 3, 400, 60_000, 60_000));
        assertEquals(WellRules.Outcome.COUNTED, well.channel(a, 100_000, 3, 400, 60_000, 60_000));
        assertEquals(WellRules.Outcome.YIELDED, well.channel(a, 100_500, 3, 400, 60_000, 60_000));
        assertEquals(java.util.List.of(a), well.participants);
    }

    @Test
    void aLoneBenderGetsOnlyTheBaseYieldAndAGroupIsCapped() {
        assertEquals(0, WellRules.extraCrystals(1, 1, 2, 6));
        assertEquals(2, WellRules.extraCrystals(3, 1, 2, 6));
        assertEquals(4, WellRules.extraCrystals(9, 1, 2, 6), "never above the cap of six in total");
        assertEquals(0, WellRules.extraCrystals(5, 1, 6, 6));
        assertEquals(0, WellRules.extraCrystals(0, 1, 2, 6));
    }
}
