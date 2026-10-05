package com.silver.aipets.fabric.experience;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class ExperienceGateTest {
    @Test void observesOnlyLoadedLocalPetsAndDebouncesRepeatedEvents() {
        assertFalse(ExperienceGate.aware(false,false,false,true,4));
        assertFalse(ExperienceGate.aware(true,true,false,true,4));
        assertFalse(ExperienceGate.aware(true,false,false,false,4));
        assertFalse(ExperienceGate.aware(true,false,false,true,33*33));
        assertFalse(ExperienceGate.aware(true,false,false,true,17*17));
        assertTrue(ExperienceGate.aware(true,false,false,true,4));
        assertFalse(ExperienceGate.witnessedOwnerAction(true,false,false,true,4,false));
        assertFalse(ExperienceGate.witnessedOwnerAction(true,false,false,true,17*17,true));
        assertTrue(ExperienceGate.witnessedOwnerAction(true,false,false,true,4,true));
        ExperienceGate gate=new ExperienceGate();
        UUID pet=UUID.randomUUID();
        assertTrue(gate.claim(pet,"NEW_AREA","a",100,600));
        assertFalse(gate.claim(pet,"NEW_AREA","a",200,600));
        assertTrue(gate.claim(pet,"NEW_AREA","b",200,600));
        assertTrue(gate.claim(pet,"NEW_AREA","a",700,600));
    }
}
