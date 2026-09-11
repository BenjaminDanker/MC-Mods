package com.silver.aipets.common.transport;

import java.math.BigDecimal;

/** Authenticated subscription projection used by billing and adoption UI. */
public record SubscriptionAccessWireResult(
        boolean aiAccessEnabled,
        String status,
        boolean cancelAtPeriodEnd,
        String currentPeriodEnd,
        BigDecimal budgetUsd,
        BigDecimal consumedUsd,
        BigDecimal remainingUsd) {

    public SubscriptionAccessWireResult(
            boolean aiAccessEnabled, String status, boolean cancelAtPeriodEnd,
            String currentPeriodEnd) {
        this(aiAccessEnabled, status, cancelAtPeriodEnd, currentPeriodEnd,
                null, null, null);
    }

    /** Compatibility constructor for gateways that only expose the boolean projection. */
    public SubscriptionAccessWireResult(boolean aiAccessEnabled) {
        this(aiAccessEnabled, "UNKNOWN", false, null, null, null, null);
    }
}
