package com.silver.aipets.service.subscription;

public enum StripeWebhookApplyStatus {
    APPLIED,
    DUPLICATE,
    STALE,
    IGNORED,
    REJECTED,
    RETRY_NEEDED
}
