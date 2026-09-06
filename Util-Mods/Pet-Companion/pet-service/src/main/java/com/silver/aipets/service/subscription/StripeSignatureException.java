package com.silver.aipets.service.subscription;

public final class StripeSignatureException extends RuntimeException {
    public StripeSignatureException(String message) {
        super(message);
    }
}
