package com.silver.elementchoice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimRulesTest {
    private final UUID me = UUID.randomUUID();

    @Test
    void unclaimedLandAndYourOwnClaimsAreFine() {
        assertFalse(ClaimRules.forbids(null, me));
        assertFalse(ClaimRules.forbids(me, me));
    }

    @Test
    void someoneElsesClaimAndTheServersAreOffLimits() {
        assertTrue(ClaimRules.forbids(UUID.randomUUID(), me));
        assertTrue(ClaimRules.forbids(new UUID(0, 0), me), "a server claim such as spawn has its own owner id");
    }
}
