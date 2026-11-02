package com.silver.aipets.fabric.ai;

import com.silver.aipets.common.domain.PetTraits;
import java.time.Instant;
import java.util.EnumMap;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Twenty minutes of no-chat play, sampled only when a bounded intention ends. */
final class PetIntentionReplayTest {
    @Test void quietPlayHasStableVarietyWithoutRepeatedInvestigations() {
        UUID pet = UUID.fromString("10000000-0000-0000-0000-000000000081");
        PetTraits traits = new PetTraits(80, 45, 50, 20, 75, 30, 35, 50,
                "", 0, Instant.EPOCH);
        var counts = new EnumMap<PetIntent, Integer>(PetIntent.class);
        PetIntent previous = PetIntent.PAUSE;
        long lastExplore = -600, lastSit = -1200;
        StringBuilder trace = new StringBuilder();
        for (long tick = 0; tick < 20 * 60 * 20; tick += 300) {
            var choice = PetStancePolicy.choose(traits, 20, true, 4, 9,
                    PetActionChoice.roll(pet, tick), true,
                    tick - lastExplore, tick - lastSit, previous, 300);
            PetIntent next = choice.intent();
            assertNotEquals(PetIntent.VOCALIZE, next);
            if (next == PetIntent.EXPLORE_NEARBY) {
                assertNotEquals(PetIntent.EXPLORE_NEARBY, previous);
                lastExplore = tick;
            }
            if (next == PetIntent.SIT) {
                assertNotEquals(PetIntent.SIT, previous);
                lastSit = tick;
            }
            counts.merge(next, 1, Integer::sum);
            if (tick < 3600) trace.append(tick / 20).append("s:")
                    .append(next).append('(').append(choice.reason()).append(") ");
            previous = next;
        }
        System.out.println("intention_replay " + trace);
        System.out.println("intention_replay_counts " + counts);
        assertTrue(counts.getOrDefault(PetIntent.EXPLORE_NEARBY, 0) >= 3);
        assertTrue(counts.getOrDefault(PetIntent.LOOK_AT_OWNER, 0) >= 3);
        assertTrue(counts.getOrDefault(PetIntent.PAUSE, 0) >= 3);
    }
}
