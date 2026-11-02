package com.silver.skyislands.enderdragons;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class DragonGustFallProtectionTest {
    @Test void capsOnlyFallDamageDuringTwoSecondWindow() {
        assertEquals(6,DragonGustFallProtection.cap(100,140,true,20));
        assertEquals(20,DragonGustFallProtection.cap(100,140,false,20));
        assertEquals(20,DragonGustFallProtection.cap(140,140,true,20));
        assertEquals(4,DragonGustFallProtection.cap(100,140,true,4));
        assertEquals(40,DragonGustFallProtection.DURATION_TICKS);
    }
}
