package com.silver.aipets.fabric.reconciliation;

public enum PetRecoveryStatus {
    RECONSTRUCTED,
    AUTHORITATIVE_ENTITY_PRESENT,
    DEFERRED_UNLOADED_CHUNK,
    NOT_LOCAL_PLACED,
    NO_PET,
    OWNER_OFFLINE,
    CONTEXT_CHANGED,
    SERVICE_FAILURE
}
