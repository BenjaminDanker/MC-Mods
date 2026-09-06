package com.silver.aipets.fabric.reconciliation;

public enum PetEntityReconciliationStatus {
    IGNORED_NOT_PET,
    AUTHORITATIVE_REUSED,
    STALE_DISCARDED,
    SERVICE_FAILURE,
    CONTEXT_CHANGED
}
