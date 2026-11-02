package com.silver.skyislands.enderdragons;

import net.minecraft.world.level.GameType;

/** A player who explicitly provoked the dragon stays its target in Creative too. */
public final class DragonCombatTargetPolicy {
    private DragonCombatTargetPolicy() {}
    public static boolean retain(boolean alive, GameType mode, boolean sameWorld, double distanceSquared) {
        return alive && mode != GameType.SPECTATOR && sameWorld && distanceSquared <= 256.0 * 256.0;
    }
}
