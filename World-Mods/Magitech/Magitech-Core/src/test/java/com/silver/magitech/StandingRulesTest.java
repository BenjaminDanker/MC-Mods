package com.silver.magitech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class StandingRulesTest {
    private static Instant at(String iso) {
        return Instant.parse(iso);
    }

    @Test
    void weekTurnsOverAtCentralMidnightBetweenSundayAndMonday() {
        // 2026-10-26 is a Monday; Central is CDT (UTC-5) then.
        assertEquals("2026-10-19", StandingRules.week(at("2026-10-26T04:59:59Z")), "Sunday 23:59:59 Central");
        assertEquals("2026-10-26", StandingRules.week(at("2026-10-26T05:00:00Z")), "Monday 00:00 Central");
    }

    @Test
    void mondayInUtcButSundayInCentralStillBelongsToTheOldWeek() {
        assertEquals("2026-10-19", StandingRules.week(at("2026-10-26T03:00:00Z")));
        assertEquals("2026-10-19", StandingRules.week(at("2026-10-26T00:00:00Z")));
    }

    @Test
    void midWeekInstantsMapToThatWeeksMonday() {
        assertEquals("2026-10-26", StandingRules.week(at("2026-10-28T15:00:00Z")));
        assertEquals("2026-10-26", StandingRules.week(at("2026-10-26T12:00:00Z")));
    }

    @Test
    void daylightSavingEndingDoesNotShiftTheWeek() {
        // Clocks go back on Sunday 2026-11-01; the week of Monday 10-26 still ends at Monday 11-02 00:00 Central (CST, UTC-6).
        assertEquals("2026-10-26", StandingRules.week(at("2026-11-01T05:00:00Z")), "Sunday 00:00 CDT");
        assertEquals("2026-10-26", StandingRules.week(at("2026-11-02T05:59:59Z")), "Sunday 23:59:59 CST");
        assertEquals("2026-11-02", StandingRules.week(at("2026-11-02T06:00:00Z")), "Monday 00:00 CST");
        // Under a naive UTC-5 offset 05:59 UTC would already be Monday; it must not be.
        assertEquals("2026-10-26", StandingRules.week(at("2026-11-02T05:30:00Z")));
    }

    @Test
    void dayTurnsOverAtCentralMidnight() {
        assertEquals("2026-10-25", StandingRules.day(at("2026-10-26T04:59:59Z")));
        assertEquals("2026-10-26", StandingRules.day(at("2026-10-26T05:00:00Z")));
        assertEquals("2026-10-25", StandingRules.day(at("2026-10-26T00:30:00Z")), "past midnight UTC, still the 25th Central");
        assertEquals("2026-11-01", StandingRules.day(at("2026-11-02T05:59:59Z")), "CST: 23:59 on 11-01");
        assertEquals("2026-11-02", StandingRules.day(at("2026-11-02T06:00:00Z")));
    }

    @Test
    void winnerIsTheHigherSideOrNullOnATie() {
        assertEquals("magic", StandingRules.winner(10, 3));
        assertEquals("tech", StandingRules.winner(3, 10));
        assertEquals("magic", StandingRules.winner(1, 0));
        assertEquals("tech", StandingRules.winner(0, 1));
        assertNull(StandingRules.winner(7, 7));
        assertNull(StandingRules.winner(0, 0));
    }

    @Test
    void countableKillsStopAtTheDailyCap() {
        assertEquals(100, StandingRules.HOSTILE_KILLS_PER_DAY);
        assertEquals(5, StandingRules.countableKills(0, 5));
        assertEquals(5, StandingRules.countableKills(94, 5), "exactly reaches the cap");
        assertEquals(3, StandingRules.countableKills(97, 5), "partly over");
        assertEquals(1, StandingRules.countableKills(99, 5));
        assertEquals(0, StandingRules.countableKills(100, 5), "already at the cap");
        assertEquals(0, StandingRules.countableKills(250, 5), "already over the cap");
        assertEquals(0, StandingRules.countableKills(0, 0));
    }

    @Test
    void crossedIsTrueOnlyWhenTheMarkIsReachedThisTime() {
        assertTrue(StandingRules.crossed(90, 100, 100), "below to exactly at");
        assertTrue(StandingRules.crossed(90, 150, 100), "below to above");
        assertFalse(StandingRules.crossed(100, 110, 100), "was already at the mark");
        assertFalse(StandingRules.crossed(120, 130, 100), "already above");
        assertFalse(StandingRules.crossed(50, 99, 100), "still below");
        assertFalse(StandingRules.crossed(50, 50, 100), "no change");
    }
}
