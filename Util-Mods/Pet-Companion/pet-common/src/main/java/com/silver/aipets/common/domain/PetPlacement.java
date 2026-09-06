package com.silver.aipets.common.domain;

/**
 * State-specific placement data. The sealed variants make illegal nullable-field combinations
 * unrepresentable in the domain model.
 */
public sealed interface PetPlacement
        permits HeldPlacement, PlacedPlacement, TransferringPlacement {
    PlacementState state();
}
