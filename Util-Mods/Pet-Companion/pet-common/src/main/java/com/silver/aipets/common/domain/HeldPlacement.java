package com.silver.aipets.common.domain;

/** HELD has no active placement or transfer metadata. */
public enum HeldPlacement implements PetPlacement {
    INSTANCE;

    @Override
    public PlacementState state() {
        return PlacementState.HELD;
    }
}
