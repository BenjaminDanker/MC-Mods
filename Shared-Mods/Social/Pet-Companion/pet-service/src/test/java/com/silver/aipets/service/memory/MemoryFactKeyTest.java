package com.silver.aipets.service.memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class MemoryFactKeyTest {
    @Test void laterExclusivePreferencesReplaceTheSameFactButLikesRemainIndependent() {
        assertEquals(MemoryFactKey.fromOwnerStatement("Owner prefers cats"),
                MemoryFactKey.fromOwnerStatement("Owner now prefers dogs"));
        assertEquals(MemoryFactKey.fromOwnerStatement("My favorite color is blue"),
                MemoryFactKey.fromOwnerStatement("Owner's favorite color is red"));
        assertNotEquals(MemoryFactKey.fromOwnerStatement("My favorite color is blue"),
                MemoryFactKey.fromOwnerStatement("My favorite food is pizza"));
        assertTrue(MemoryFactKey.fromOwnerStatement("Owner likes cats and dogs").isEmpty());
    }
}
