package com.silver.aipets.common.authority;

import com.silver.aipets.common.domain.BackendId;
import com.silver.aipets.common.domain.DimensionId;
import java.util.Objects;
import java.util.UUID;

/** Trusted server-side identity read from a marked physical pet representation. */
public record PetEntityIdentity(
        UUID petId,
        UUID ownerUuid,
        BackendId backendId,
        DimensionId dimensionId,
        UUID entityUuid) {
    public PetEntityIdentity {
        Objects.requireNonNull(petId, "petId");
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(entityUuid, "entityUuid");
    }
}
