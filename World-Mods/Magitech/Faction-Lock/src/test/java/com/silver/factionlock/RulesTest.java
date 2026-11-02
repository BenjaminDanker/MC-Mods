package com.silver.factionlock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RulesTest {
    @Test
    void polyFactoryEquipmentIsTech() {
        assertEquals(Faction.TECH, Rules.owner("polyfactory", null));
        assertEquals(Faction.TECH, Rules.owner("arsenal", null), "the Tech Arsenal's weapons are tech equipment");
        assertTrue(Rules.blocked(Faction.MAGIC, Rules.owner("arsenal", null)));
        assertTrue(Rules.blocked(Faction.MAGIC, Rules.owner("polyfactory", null)));
        assertFalse(Rules.blocked(Faction.TECH, Rules.owner("polyfactory", null)));
    }

    @Test
    void customTagsMarkOtherEquipmentAndNeutralStaysOpen() {
        assertEquals(Faction.MAGIC, Rules.owner("minecraft", "magic"));
        assertNull(Rules.owner("minecraft", null));
        assertNull(Rules.owner("minecraft", "nonsense"));
        assertFalse(Rules.blocked(Faction.MAGIC, null));
        assertFalse(Rules.blocked(Faction.NONE, null));
        assertTrue(Rules.blocked(Faction.NONE, Faction.TECH), "players with no faction use nobody's equipment");
    }

    @Test
    void aFactionIsTheOneStoryFinished() {
        assertEquals(Faction.MAGIC, Faction.of(true, false));
        assertEquals(Faction.TECH, Faction.of(false, true));
        assertEquals(Faction.NONE, Faction.of(false, false));
        assertEquals(Faction.NONE, Faction.of(true, true));
    }
}
