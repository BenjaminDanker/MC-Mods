package com.silver.aipets.common.transport;

public final class PetWireFormatException extends RuntimeException {
    public PetWireFormatException(String message) {
        super(message);
    }

    public PetWireFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
