package com.silver.aipets.common.authority;

import com.silver.aipets.common.domain.Pet;
import com.silver.aipets.common.domain.PlacedPlacement;
import java.util.Objects;

/** Pure authority check used on entity load and periodic/lazy reconciliation. */
public final class EntityAuthority {
    private EntityAuthority() {
    }

    public static EntityAuthorityDecision decide(Pet pet, PetEntityIdentity entity) {
        Objects.requireNonNull(pet, "pet");
        Objects.requireNonNull(entity, "entity");

        if (!pet.petId().equals(entity.petId())) {
            return EntityAuthorityDecision.DISCARD_PET_ID_MISMATCH;
        }
        if (!pet.ownerUuid().equals(entity.ownerUuid())) {
            return EntityAuthorityDecision.DISCARD_OWNER_MISMATCH;
        }
        if (!(pet.placement() instanceof PlacedPlacement placed)) {
            return EntityAuthorityDecision.DISCARD_NOT_PLACED;
        }
        if (!placed.backendId().equals(entity.backendId())) {
            return EntityAuthorityDecision.DISCARD_BACKEND_MISMATCH;
        }
        if (!placed.dimensionId().equals(entity.dimensionId())) {
            return EntityAuthorityDecision.DISCARD_DIMENSION_MISMATCH;
        }
        if (placed.entityUuid().isEmpty()
                || !placed.entityUuid().orElseThrow().equals(entity.entityUuid())) {
            return EntityAuthorityDecision.DISCARD_ENTITY_UUID_MISMATCH;
        }
        return EntityAuthorityDecision.AUTHORITATIVE;
    }

    /**
     * Includes the entity's persisted record version in the authority decision. An older entity
     * revision can be refreshed after its placement identity is proven; a revision ahead of the
     * authoritative row is impossible in a monotonic CAS history and is discarded safely.
     */
    public static EntityAuthorityDecision decide(
            Pet pet,
            PetEntityIdentity entity,
            long entityRecordVersion) {
        if (entityRecordVersion < 0) {
            throw new IllegalArgumentException("entityRecordVersion must be non-negative");
        }
        EntityAuthorityDecision identityDecision = decide(pet, entity);
        if (identityDecision != EntityAuthorityDecision.AUTHORITATIVE) {
            return identityDecision;
        }
        return entityRecordVersion > pet.recordVersion()
                ? EntityAuthorityDecision.DISCARD_RECORD_VERSION_AHEAD
                : EntityAuthorityDecision.AUTHORITATIVE;
    }
}
