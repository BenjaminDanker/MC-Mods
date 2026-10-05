package com.silver.aipets.service.adoption;

public enum PendingAdoptionState {
    PRE_CHECKOUT,
    CHECKOUT_STARTED,
    REPLACEMENT_PENDING,
    WAITING_FOR_ENTITLEMENT,
    COMPLETED,
    EXPIRED,
    CANCELLED
}
