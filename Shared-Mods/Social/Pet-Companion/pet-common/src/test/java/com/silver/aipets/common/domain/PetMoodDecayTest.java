package com.silver.aipets.common.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

final class PetMoodDecayTest {
    @Test void movesTowardSpeciesBaselineAndCarriesPartialInterval() {
        Instant start=Instant.parse("2026-09-25T00:00:00Z");
        PetTraits traits=PetTraits.initial(50,50,50,50,50,start);
        PetMood initial=PetMood.initial(95,90,80,70,start);
        PetMood after=initial.decay(PetSpecies.CAT,traits,start.plus(Duration.ofMinutes(65)));
        assertEquals(91,after.content());
        assertEquals(86,after.excited());
        assertEquals(76,after.anxious());
        assertEquals(66,after.tired());
        assertEquals(start.plus(Duration.ofHours(1)),after.lastDecayAt());
        assertEquals(after,after.decay(PetSpecies.CAT,traits,start.plus(Duration.ofMinutes(89))));
        assertEquals(start.plus(Duration.ofMinutes(90)),after.decay(
                PetSpecies.CAT,traits,start.plus(Duration.ofMinutes(90))).lastDecayAt());
        assertEquals(50,traits.curiosity());
    }
}
