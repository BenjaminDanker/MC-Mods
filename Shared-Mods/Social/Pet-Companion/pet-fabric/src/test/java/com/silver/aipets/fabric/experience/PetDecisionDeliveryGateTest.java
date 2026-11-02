package com.silver.aipets.fabric.experience;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

final class PetDecisionDeliveryGateTest {
    @Test void slowResponseIsAcceptedOnceButLaterRetryIsNot() {
        var gate = new PetDecisionDeliveryGate();
        UUID pet = UUID.randomUUID(), entity = UUID.randomUUID(), event = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-26T00:00:00Z");
        gate.begin(pet, entity, event, at);
        assertTrue(gate.accept(pet, entity, event, at.plusSeconds(9)));
        assertFalse(gate.accept(pet, entity, event, at.plusSeconds(10)));
    }

    @Test void newerEventReplacementAndExpiredWorkAreDiscarded() {
        var gate = new PetDecisionDeliveryGate();
        UUID pet = UUID.randomUUID(), entity = UUID.randomUUID();
        UUID old = UUID.randomUUID(), recent = UUID.randomUUID();
        Instant at = Instant.parse("2026-09-26T00:00:00Z");
        gate.begin(pet, entity, old, at);
        gate.begin(pet, entity, recent, at.plusSeconds(2));
        assertFalse(gate.accept(pet, entity, old, at.plusSeconds(8)));
        assertFalse(gate.accept(pet, UUID.randomUUID(), recent, at.plusSeconds(8)));
        assertFalse(gate.accept(pet, entity, recent, at.plusSeconds(23)));
        gate.begin(pet, entity, UUID.randomUUID(), at.plusSeconds(24));
        gate.invalidate(pet);
        assertFalse(gate.isPending(pet, entity, recent, at.plusSeconds(24)));
    }
}
