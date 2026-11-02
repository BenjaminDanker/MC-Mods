package com.silver.bossunlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class BossRuleTest {
    @Test
    void anExactTagMatchesOnlyThatTag() {
        var inferno = new BossRule("inferno_base", null, null, "fire");
        assertTrue(inferno.matches("minecraft:blaze", List.of("inferno_base", "inferno")));
        assertFalse(inferno.matches("minecraft:blaze", List.of("infernos_blaze")));
        assertFalse(inferno.matches("minecraft:blaze", List.of()));
    }

    @Test
    void aTagPrefixMatchesManagedBossesWithAnIdSuffix() {
        var leviathan = new BossRule(null, "atlantis_leviathan_id:", null, "water");
        assertTrue(leviathan.matches("minecraft:salmon", List.of("atlantis_leviathan_id:1234-abcd")));
        assertTrue(leviathan.matches("minecraft:squid", List.of("x", "atlantis_leviathan_id:9")));
        assertFalse(leviathan.matches("minecraft:salmon", List.of("sky_islands_giant_id:1234")));
    }

    @Test
    void anEntityTypeConditionMustAlsoHold() {
        var warden = new BossRule("legendary", null, "minecraft:warden", "earth");
        assertTrue(warden.matches("minecraft:warden", List.of("ic.ability", "legendary")));
        assertFalse(warden.matches("minecraft:zombie", List.of("legendary")), "a legendary zombie is not the Warden");
        assertFalse(warden.matches("minecraft:warden", List.of("ic.ability")), "an ordinary Warden does not count");
    }

    @Test
    void aRuleWithNoConditionsNeverMatchesAndOnlyTypeAloneIsAllowed() {
        assertFalse(new BossRule(null, null, null, "air").matches("minecraft:cow", List.of("anything")));
        assertTrue(new BossRule(null, null, "minecraft:ender_dragon", "air").matches("minecraft:ender_dragon", List.of()));
    }

    @Test
    void onlyTheFourElementsAreAccepted() {
        assertEquals("fire", new BossRule("t", null, null, "FIRE").normalizedElement());
        assertNull(new BossRule("t", null, null, "plasma").normalizedElement());
        assertNull(new BossRule("t", null, null, null).normalizedElement());
    }
}
