package com.silver.aipets.common.authority;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.HeldPlacement;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.test.TestPets;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdminRecoveryTransitionTest {
    @Test
    void invalidatesDeletedRepresentationWithoutChangingPersistentIdentityOrAppearance() {
        Pet adopted = TestPets.held();
        Pet placed = PetTransitions.place(adopted, new PetTransitions.Place(
                adopted.ownerUuid(), adopted.recordVersion(), new BackendId("survival"),
                DimensionId.parse("minecraft:overworld"), new WorldPosition(4, 70, 8),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                adopted.updatedAt().plusSeconds(1))).pet();

        TransitionResult recovered = PetTransitions.adminRecover(
                placed,
                new PetTransitions.AdminRecover(
                        placed.recordVersion(), placed.updatedAt().plusSeconds(1)));

        assertTrue(recovered.applied());
        assertEquals(HeldPlacement.INSTANCE, recovered.pet().placement());
        assertEquals(placed.petId(), recovered.pet().petId());
        assertEquals(placed.ownerUuid(), recovered.pet().ownerUuid());
        assertEquals(placed.name(), recovered.pet().name());
        assertSame(placed.appearance(), recovered.pet().appearance());
        assertSame(placed.traits(), recovered.pet().traits());
        assertSame(placed.mood(), recovered.pet().mood());
        assertEquals(placed.recordVersion() + 1, recovered.pet().recordVersion());
    }
}
