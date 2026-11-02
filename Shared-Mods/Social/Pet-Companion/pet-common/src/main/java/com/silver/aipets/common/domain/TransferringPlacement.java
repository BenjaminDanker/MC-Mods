package com.silver.aipets.common.domain;

import java.util.Objects;

/** TRANSFERRING has reservation metadata and, by construction, no active placed entity. */
public record TransferringPlacement(TransferMetadata transfer) implements PetPlacement {
    public TransferringPlacement {
        Objects.requireNonNull(transfer, "transfer");
    }

    @Override
    public PlacementState state() {
        return PlacementState.TRANSFERRING;
    }
}
