package com.silver.aipets.fabric.ai;

/** Managed ambient calls are reserved for attention and actual pet speech. */
public final class PetVocalizationPolicy {
    public enum Trigger { ATTENTION, SPEECH, ROUTINE }

    private PetVocalizationPolicy() { }

    public static boolean allow(Trigger trigger, long tick, long lastSoundTick) {
        if (trigger == null || trigger == Trigger.ROUTINE || tick < lastSoundTick) return false;
        return tick - lastSoundTick >= (trigger == Trigger.ATTENTION ? 600 : 20);
    }
}
