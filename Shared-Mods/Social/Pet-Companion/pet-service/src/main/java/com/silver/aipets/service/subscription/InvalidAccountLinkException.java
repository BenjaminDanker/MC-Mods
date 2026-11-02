package com.silver.aipets.service.subscription;

public final class InvalidAccountLinkException extends RuntimeException {
    public InvalidAccountLinkException(String message) {
        super(message);
    }
}
