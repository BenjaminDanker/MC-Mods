package com.silver.aipets.service.experience;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

final class DecisionPolicyTest {
    @Test void routineEventsNeverCallAndPersistedWindowSuppressesBurstAfterRestart() {
        Instant now=Instant.parse("2026-09-25T12:00:00Z");
        assertFalse(DecisionPolicy.shouldInterpret("OWNER_RETURNED",true,true,now,null));
        assertTrue(DecisionPolicy.shouldInterpret("OWNER_RETURNED_AFTER_ABSENCE",
                true,true,now,null));
        assertFalse(DecisionPolicy.shouldInterpret("NEARBY_THREAT",true,true,now,null));
        assertFalse(DecisionPolicy.shouldInterpret("NEW_AREA",false,true,now,null));
        assertFalse(DecisionPolicy.shouldInterpret("NEW_AREA",true,false,now,null));
        Instant persistedWindow=now.plusSeconds(60);
        assertFalse(DecisionPolicy.shouldInterpret("NEW_AREA",true,true,now,persistedWindow));
        assertTrue(DecisionPolicy.shouldInterpret("NEW_AREA",true,true,
                persistedWindow,persistedWindow));
        assertEquals(30,DecisionPolicy.boundedSeconds(-100));
        assertEquals(120,DecisionPolicy.boundedSeconds(10000));
    }
}
