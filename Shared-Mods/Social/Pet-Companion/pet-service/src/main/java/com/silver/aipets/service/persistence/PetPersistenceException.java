package com.silver.aipets.service.persistence;

/** Sanitized persistence failure; callers must not expose the nested driver message to players. */
public final class PetPersistenceException extends RuntimeException {
    public PetPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
