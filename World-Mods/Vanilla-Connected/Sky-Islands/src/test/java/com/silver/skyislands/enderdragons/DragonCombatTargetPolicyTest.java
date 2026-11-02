package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.level.GameType;
import org.junit.jupiter.api.Test;

class DragonCombatTargetPolicyTest {
    @Test void creativeAttackersRemainProvokedJustLikeSurvivalAttackers() {
        for (GameType mode : new GameType[]{GameType.CREATIVE, GameType.SURVIVAL, GameType.ADVENTURE}) {
            assertTrue(DragonCombatTargetPolicy.retain(true,mode,true,40*40));
        }
    }
    @Test void invalidTargetsStillReleaseCombat() {
        assertFalse(DragonCombatTargetPolicy.retain(true,GameType.SPECTATOR,true,100));
        assertFalse(DragonCombatTargetPolicy.retain(false,GameType.CREATIVE,true,100));
        assertFalse(DragonCombatTargetPolicy.retain(true,GameType.SURVIVAL,false,100));
        assertFalse(DragonCombatTargetPolicy.retain(true,GameType.CREATIVE,true,257*257));
    }
}
