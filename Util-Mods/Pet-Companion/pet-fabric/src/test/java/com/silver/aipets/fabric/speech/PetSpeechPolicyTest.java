package com.silver.aipets.fabric.speech;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PetSpeechPolicyTest {
    @Test
    void sanitizesWrapsCapsSentencesAndClampsLifetime() {
        PetSpeechPolicy policy = new PetSpeechPolicy(
                80, 20, Duration.ofSeconds(5), Duration.ofSeconds(12),
                0.35F, 180.0F, 1.0F);
        PetSpeechPolicy.PreparedSpeech prepared = policy.prepare(
                "§aFirst sentence has https://unsafe.example/path and wraps safely. "
                        + "Second sentence stays! Third sentence must disappear?");

        assertFalse(prepared.plainText().contains("§"));
        assertFalse(prepared.plainText().contains("http"));
        assertFalse(prepared.plainText().contains("Third"));
        assertTrue(prepared.plainText().codePointCount(0, prepared.plainText().length()) <= 80);
        assertTrue(prepared.wrappedText().contains("\n"));
        assertTrue(prepared.lifetime().compareTo(Duration.ofSeconds(5)) >= 0);
        assertTrue(prepared.lifetime().compareTo(Duration.ofSeconds(12)) <= 0);
        assertEquals(180.0F, policy.yawOffsetDegrees());
    }
}
