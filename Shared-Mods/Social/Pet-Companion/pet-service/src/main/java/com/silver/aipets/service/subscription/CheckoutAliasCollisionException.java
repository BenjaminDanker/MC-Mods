package com.silver.aipets.service.subscription;

/** Signals that the database rejected a generated alias because it already exists. */
final class CheckoutAliasCollisionException extends RuntimeException {
    CheckoutAliasCollisionException(Throwable cause) {
        super("Generated checkout alias collided with an existing alias", cause);
    }
}
