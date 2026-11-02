package com.silver.aipets.fabric.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetActionSafetyTest {
    @Test void leavingOwnerInterruptsSittingAndExplorationAndStuckExplorationStops() {
        assertTrue(PetActionSafety.interrupt(PetIntent.SIT,9,16,true,false,20,0));
        assertTrue(PetActionSafety.interrupt(PetIntent.EXPLORE_NEARBY,25,16,
                false,false,20,1));
        assertTrue(PetActionSafety.interrupt(PetIntent.EXPLORE_NEARBY,4,16,
                false,false,61,0));
        assertTrue(PetActionSafety.interrupt(PetIntent.EXPLORE_NEARBY,4,16,
                false,true,2,0));
        assertFalse(PetActionSafety.interrupt(PetIntent.LOOK_AT_OWNER,4,16,
                true,false,20,0));
        assertFalse(PetActionSafety.interrupt(PetIntent.EXPLORE_NEARBY,15,36,
                false,false,20,1));
    }

    @Test void unknownSpeciesFallsBackAndAmbientGateIsPetOnly() {
        assertFalse(PetActionCapabilities.FALLBACK.supports(PetIntent.SIT));
        assertTrue(PetActionCapabilities.FALLBACK.supports(PetIntent.LOOK_AT_OWNER));
        assertFalse(PetSoundGate.cancelAmbient(false,false));
        assertTrue(PetSoundGate.cancelAmbient(true,false));
        assertFalse(PetSoundGate.cancelAmbient(true,true));
    }
}
