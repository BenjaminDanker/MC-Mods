package com.silver.aipets.fabric.ai;

import com.silver.aipets.common.domain.PetTraits;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class PetStancePolicyTest {
    private static PetTraits traits(int curiosity, int independence, int attachment,
            int security) {
        return new PetTraits(curiosity, 50, 50, 30, independence, attachment,
                35, security, "", 0, Instant.EPOCH);
    }

    @Test void independentPetCanExploreButStillTracksItsOwner() {
        PetTraits independent = traits(80, 90, 20, 50);
        assertEquals(PetIntent.EXPLORE_NEARBY, PetStancePolicy.choose(independent,
                10, true, 9, 4, 0, true, 1000, 1500,
                PetIntent.PAUSE, 500).intent());
        assertEquals(PetIntent.LOOK_AT_OWNER, PetStancePolicy.choose(independent,
                10, true, 1, 4, 50, true, 1000, 1500,
                PetIntent.PAUSE, 500).intent());
    }

    @Test void waryPetApproachesWhenSeparatedWithoutBecomingAffectionate() {
        PetTraits wary = traits(60, 75, 20, 20);
        assertEquals(PetIntent.APPROACH_OWNER, PetStancePolicy.choose(wary,
                80, false, 16, 4, 50, true, 1000, 1500,
                PetIntent.PAUSE, 500).intent());
        assertEquals(PetIntent.LOOK_AT_OWNER, PetStancePolicy.choose(wary,
                80, false, 1, 4, 50, true, 1000, 1500,
                PetIntent.PAUSE, 500).intent());
    }
}
