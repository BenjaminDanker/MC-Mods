package com.silver.aipets.fabric.ai;

import java.util.UUID;

/** Stable per-pet variety without sequential minutes-long runs of one choice range. */
public final class PetActionChoice {
    private PetActionChoice() { }

    public static int roll(UUID petId, long tick) {
        long value = petId.getMostSignificantBits()
                ^ Long.rotateLeft(petId.getLeastSignificantBits(), 29)
                ^ (tick / 120 * 0x9E3779B97F4A7C15L);
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return Math.floorMod(value ^ value >>> 31, 100);
    }
}
