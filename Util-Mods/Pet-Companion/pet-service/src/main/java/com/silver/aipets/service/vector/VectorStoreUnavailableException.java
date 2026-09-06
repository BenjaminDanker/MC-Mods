package com.silver.aipets.service.vector;

public final class VectorStoreUnavailableException extends RuntimeException {
    public VectorStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }

    public VectorStoreUnavailableException(String message) {
        super(message);
    }
}
