package com.silver.aipets.common.domain;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authoritative placed location; a missing entity UUID means the pet is virtualized/unloaded. */
public record PlacedPlacement(
        BackendId backendId,
        DimensionId dimensionId,
        WorldPosition position,
        Optional<UUID> entityUuid) implements PetPlacement {
    public PlacedPlacement {
        Objects.requireNonNull(backendId, "backendId");
        Objects.requireNonNull(dimensionId, "dimensionId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(entityUuid, "entityUuid");
    }

    public static PlacedPlacement materialized(
            BackendId backendId,
            DimensionId dimensionId,
            WorldPosition position,
            UUID entityUuid) {
        return new PlacedPlacement(
                backendId,
                dimensionId,
                position,
                Optional.of(Objects.requireNonNull(entityUuid, "entityUuid")));
    }

    public static PlacedPlacement virtualized(
            BackendId backendId,
            DimensionId dimensionId,
            WorldPosition lastPosition) {
        return new PlacedPlacement(backendId, dimensionId, lastPosition, Optional.empty());
    }

    public boolean isMaterialized() {
        return entityUuid.isPresent();
    }

    @Override
    public PlacementState state() {
        return PlacementState.PLACED;
    }
}
