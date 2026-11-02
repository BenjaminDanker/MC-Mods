package com.silver.aipets.fabric.ai;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetActionChoiceTest {
    @Test void choicesVaryAcrossDecisionWindowsWithoutChangingWithinOneWindow() {
        UUID pet = UUID.fromString("10000000-0000-0000-0000-000000000081");
        assertEquals(PetActionChoice.roll(pet, 120), PetActionChoice.roll(pet, 239));
        long exploratory = 0;
        for (int window = 0; window < 100; window++)
            if (PetActionChoice.roll(pet, window * 120L) < 20) exploratory++;
        assertTrue(exploratory >= 8 && exploratory <= 32,
                "Local choices should spread investigations across ordinary play");
    }
}
