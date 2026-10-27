package com.silver.magitech;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** The arithmetic of the weekly standing, kept pure so it is tested without a server. */
final class StandingRules {
    /** Weeks turn over at midnight between Sunday and Monday, Central time. */
    static final ZoneId ZONE = ZoneId.of("America/Chicago");
    static final int CRYSTAL = 2;
    static final int DEEP_ORE = 2;
    static final int CHEST = 3;
    static final int HOSTILE_KILL = 1;
    static final int RIVAL_KILL = 10;
    /** Hostile kills that count per player per day, so a mob farm cannot carry a side. */
    static final int HOSTILE_KILLS_PER_DAY = 100;
    static final int RECLAIMER = 100;
    static final int PILLAR = 500;

    private StandingRules() {
    }

    /** The Monday that starts the week holding {@code now}, as yyyy-mm-dd. */
    static String week(Instant now) {
        return LocalDate.ofInstant(now, ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
    }

    static String day(Instant now) {
        return LocalDate.ofInstant(now, ZONE).toString();
    }

    /** "magic", "tech", or null for a tie (including a week nobody played). */
    static String winner(int magic, int tech) {
        return magic > tech ? "magic" : tech > magic ? "tech" : null;
    }

    /** How many of {@code wanted} hostile kills still count today, given {@code alreadyToday}. */
    static int countableKills(int alreadyToday, int wanted) {
        return Math.max(0, Math.min(wanted, HOSTILE_KILLS_PER_DAY - alreadyToday));
    }

    /** True when a player's weekly points went from below {@code mark} to at least it. */
    static boolean crossed(int before, int after, int mark) {
        return before < mark && after >= mark;
    }
}
