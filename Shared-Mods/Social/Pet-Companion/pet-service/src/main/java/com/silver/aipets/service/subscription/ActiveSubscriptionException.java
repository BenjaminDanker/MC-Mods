package com.silver.aipets.service.subscription;

/** Indicates that a stale Checkout link belongs to an already-entitled owner. */
public final class ActiveSubscriptionException extends RuntimeException {
    public ActiveSubscriptionException(String message) {
        super(message);
    }
}
