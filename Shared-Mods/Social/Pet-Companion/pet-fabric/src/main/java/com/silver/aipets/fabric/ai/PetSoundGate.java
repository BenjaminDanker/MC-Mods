package com.silver.aipets.fabric.ai;

/** Only the managed pet's Mob.playAmbientSound path is gated. */
public final class PetSoundGate {
    private PetSoundGate() { }
    public static boolean cancelAmbient(boolean managedPet, boolean controlledPlayback) {
        return managedPet && !controlledPlayback;
    }
}
