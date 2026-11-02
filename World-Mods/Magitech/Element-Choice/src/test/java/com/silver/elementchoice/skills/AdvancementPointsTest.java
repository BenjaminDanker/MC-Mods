package com.silver.elementchoice.skills;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AdvancementPointsTest {
    private final AdvancementPoints rules = new AdvancementPoints(Set.of("minecraft", "moog"), Set.of("minecraft:story/mine_stone"), 1, 2, 3,
        java.util.Map.of("rnt.boss", 10, "minor", 2));

    @Test
    void paysByFrame() {
        assertEquals(1, rules.pointsFor("minecraft:story/smelt_iron", "task", false));
        assertEquals(2, rules.pointsFor("minecraft:nether/find_fortress", "GOAL", false));
        assertEquals(3, rules.pointsFor("moog:boss/kill", "challenge", false));
    }

    @Test
    void doesNotPayRootsExcludedOrUnlistedPacks() {
        assertEquals(0, rules.pointsFor("minecraft:story/root", "task", true));
        assertEquals(0, rules.pointsFor("minecraft:story/mine_stone", "task", false));
        assertEquals(0, rules.pointsFor("magicstory:awakened", "challenge", false));
        assertEquals(0, rules.pointsFor("noNamespace", "task", false));
        assertEquals(0, rules.pointsFor("minecraft:x", "unknown", false));
    }

    @Test
    void bossTagsPayTheHighestMatch() {
        assertEquals(10, rules.bossPoints(Set.of("x", "rnt.boss", "minor")));
        assertEquals(2, rules.bossPoints(Set.of("minor")));
        assertEquals(0, rules.bossPoints(Set.of("other")));
    }
}
