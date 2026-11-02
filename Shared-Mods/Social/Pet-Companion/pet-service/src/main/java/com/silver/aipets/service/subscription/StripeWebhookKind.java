package com.silver.aipets.service.subscription;

public enum StripeWebhookKind {
    CHECKOUT_COMPLETED,
    SUBSCRIPTION_CREATED,
    SUBSCRIPTION_UPDATED,
    SUBSCRIPTION_DELETED,
    INVOICE_PAID,
    INVOICE_PAYMENT_FAILED,
    UNSUPPORTED;

    public boolean changesEntitlement() {
        return this != CHECKOUT_COMPLETED && this != UNSUPPORTED;
    }
}
