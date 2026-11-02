package com.silver.aipets.common.authority;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import com.silver.aipets.common.domain.WorldPosition;
import com.silver.aipets.common.test.TestPets;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityAuthorityTest {
    private static final BackendId BACKEND = new BackendId("ocean");
    private static final DimensionId DIMENSION = DimensionId.parse("minecraft:overworld");
    private static final UUID ENTITY_ID = UUID.fromString("30000000-0000-0000-0000-000000000003");

    @Test
    void onlyExactPlacedIdentityIsAuthoritative() {
        Pet pet = TestPets.withPlacement(
                TestPets.held(),
                PlacedPlacement.materialized(
                        BACKEND, DIMENSION, new WorldPosition(1, 64, 1), ENTITY_ID),
                1,
                TestPets.CREATED_AT.plusSeconds(1));
        PetEntityIdentity exact = identity(
                pet.petId(), pet.ownerUuid(), BACKEND, DIMENSION, ENTITY_ID);

        assertEquals(EntityAuthorityDecision.AUTHORITATIVE, EntityAuthority.decide(pet, exact));
        assertEquals(EntityAuthorityDecision.AUTHORITATIVE, EntityAuthority.decide(pet, exact, 0));
        assertEquals(EntityAuthorityDecision.AUTHORITATIVE, EntityAuthority.decide(pet, exact, 1));
        assertEquals(
                EntityAuthorityDecision.DISCARD_RECORD_VERSION_AHEAD,
                EntityAuthority.decide(pet, exact, 2));
        assertEquals(
                EntityAuthorityDecision.DISCARD_PET_ID_MISMATCH,
                EntityAuthority.decide(pet, identity(
                        UUID.randomUUID(), pet.ownerUuid(), BACKEND, DIMENSION, ENTITY_ID)));
        assertEquals(
                EntityAuthorityDecision.DISCARD_OWNER_MISMATCH,
                EntityAuthority.decide(pet, identity(
                        pet.petId(), UUID.randomUUID(), BACKEND, DIMENSION, ENTITY_ID)));
        assertEquals(
                EntityAuthorityDecision.DISCARD_BACKEND_MISMATCH,
                EntityAuthority.decide(pet, identity(
                        pet.petId(), pet.ownerUuid(), new BackendId("desert"), DIMENSION, ENTITY_ID)));
        assertEquals(
                EntityAuthorityDecision.DISCARD_DIMENSION_MISMATCH,
                EntityAuthority.decide(pet, identity(
                        pet.petId(), pet.ownerUuid(), BACKEND,
                        DimensionId.parse("minecraft:the_nether"), ENTITY_ID)));
        assertEquals(
                EntityAuthorityDecision.DISCARD_ENTITY_UUID_MISMATCH,
                EntityAuthority.decide(pet, identity(
                        pet.petId(), pet.ownerUuid(), BACKEND, DIMENSION, UUID.randomUUID())));
    }

    @Test
    void heldAndVirtualizedPetsRejectPhysicalRepresentations() {
        Pet held = TestPets.held();
        assertEquals(
                EntityAuthorityDecision.DISCARD_NOT_PLACED,
                EntityAuthority.decide(held, identity(
                        held.petId(), held.ownerUuid(), BACKEND, DIMENSION, ENTITY_ID)));

        Pet virtualized = TestPets.withPlacement(
                held,
                PlacedPlacement.virtualized(
                        BACKEND, DIMENSION, new WorldPosition(1, 64, 1)),
                1,
                held.updatedAt().plusSeconds(1));
        assertEquals(
                EntityAuthorityDecision.DISCARD_ENTITY_UUID_MISMATCH,
                EntityAuthority.decide(virtualized, identity(
                        held.petId(), held.ownerUuid(), BACKEND, DIMENSION, ENTITY_ID)));
    }

    private static PetEntityIdentity identity(
            UUID petId,
            UUID ownerId,
            BackendId backend,
            DimensionId dimension,
            UUID entityId) {
        return new PetEntityIdentity(petId, ownerId, backend, dimension, entityId);
    }
}
