package com.silver.arsenal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimsTest {
    @Test
    void onlyYourOwnClaimsAndFreeLandAllowWeapons() {
        UUID me = UUID.randomUUID();
        assertFalse(Claims.forbids(null, me));
        assertFalse(Claims.forbids(me, me));
        assertTrue(Claims.forbids(UUID.randomUUID(), me));
        assertTrue(Claims.forbids(new UUID(0, 0), me));
    }
}
