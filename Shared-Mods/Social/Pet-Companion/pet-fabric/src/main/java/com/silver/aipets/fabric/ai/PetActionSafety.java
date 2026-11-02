package com.silver.aipets.fabric.ai;

/** Pure interruption rules; optional navigation yields to owner tracking. */
public final class PetActionSafety {
    private PetActionSafety() { }

    public static boolean interrupt(PetIntent intent, double ownerDistanceSquared,
            double followStartSquared, boolean ownerMoving,
            boolean navigationFinished, long actionTicks, double movementSquared) {
        if (ownerDistanceSquared > followStartSquared) return true;
        if (ownerMoving && (intent == PetIntent.SIT || intent == PetIntent.REST
                || intent == PetIntent.EXPLORE_NEARBY || intent == PetIntent.PAUSE
                || intent == PetIntent.VOCALIZE)) return true;
        return intent == PetIntent.EXPLORE_NEARBY && (navigationFinished
                || actionTicks > 60 && movementSquared < .25);
    }
}
