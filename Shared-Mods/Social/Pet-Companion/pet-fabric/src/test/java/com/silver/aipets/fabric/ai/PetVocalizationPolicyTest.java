package com.silver.aipets.fabric.ai;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetVocalizationPolicyTest {
    @Test void onlyAttentionAndNewOutputCanVocalizeWithSharedCooldown() {
        assertFalse(PetVocalizationPolicy.allow(
                PetVocalizationPolicy.Trigger.ROUTINE, 1000, -600));
        assertTrue(PetVocalizationPolicy.allow(
                PetVocalizationPolicy.Trigger.ATTENTION, 1000, -600));
        assertFalse(PetVocalizationPolicy.allow(
                PetVocalizationPolicy.Trigger.ATTENTION, 1010, 1000));
        assertFalse(PetVocalizationPolicy.allow(
                PetVocalizationPolicy.Trigger.SPEECH, 1010, 1000));
        assertTrue(PetVocalizationPolicy.allow(
                PetVocalizationPolicy.Trigger.SPEECH, 1020, 1000));
    }
}
