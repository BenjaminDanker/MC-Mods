package com.silver.aipets.service.consolidation;

public final class ConsolidationOutputException extends RuntimeException {
    public ConsolidationOutputException(String message) {
        super(message);
    }

    public ConsolidationOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
