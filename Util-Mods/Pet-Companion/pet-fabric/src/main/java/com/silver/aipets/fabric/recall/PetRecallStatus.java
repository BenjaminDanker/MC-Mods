package com.silver.aipets.fabric.recall;

public enum PetRecallStatus {
    RECALLED,
    NO_PET,
    UNAVAILABLE,
    NO_SAFE_POSITION,
    PLAYER_CONTEXT_CHANGED,
    AUTHORITY_REJECTED,
    SPAWN_FAILED_COMPENSATED,
    SPAWN_FAILED_UNRESOLVED,
    SERVICE_FAILURE
}
