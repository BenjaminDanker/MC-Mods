package com.silver.aipets.service.retention;

public record RetentionCleanupResult(int promptEligibilityExpired, int rawTextsRedacted) {
    public RetentionCleanupResult {
        if (promptEligibilityExpired < 0 || rawTextsRedacted < 0) {
            throw new IllegalArgumentException("Cleanup counts cannot be negative");
        }
    }
}
