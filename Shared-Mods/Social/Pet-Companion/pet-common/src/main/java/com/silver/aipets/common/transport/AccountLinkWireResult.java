package com.silver.aipets.common.transport;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record AccountLinkWireResult(
        AccountLinkWireStatus status,
        Optional<String> checkoutUrl,
        Optional<Instant> expiresAt) {
    public AccountLinkWireResult {
        Objects.requireNonNull(status, "status");
        checkoutUrl = Objects.requireNonNull(checkoutUrl, "checkoutUrl");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        boolean created = status == AccountLinkWireStatus.CREATED;
        if (created != (checkoutUrl.isPresent() && expiresAt.isPresent())) {
            throw new IllegalArgumentException("Checkout URL presence does not match status");
        }
        checkoutUrl.ifPresent(AccountLinkWireResult::validateCheckoutUrl);
    }

    public static AccountLinkWireResult created(String checkoutUrl, Instant expiresAt) {
        return new AccountLinkWireResult(
                AccountLinkWireStatus.CREATED, Optional.of(checkoutUrl), Optional.of(expiresAt));
    }

    public static AccountLinkWireResult rateLimited() {
        return new AccountLinkWireResult(
                AccountLinkWireStatus.RATE_LIMITED, Optional.empty(), Optional.empty());
    }

    public static AccountLinkWireResult checkoutInProgress() {
        return new AccountLinkWireResult(
                AccountLinkWireStatus.CHECKOUT_IN_PROGRESS, Optional.empty(), Optional.empty());
    }

    private static void validateCheckoutUrl(String value) {
        URI parsed = URI.create(value);
        if (!"https".equalsIgnoreCase(parsed.getScheme())
                || parsed.getHost() == null
                || parsed.getUserInfo() != null
                || parsed.getFragment() != null) {
            throw new IllegalArgumentException("Checkout URL must be an absolute HTTPS URL");
        }
    }
}
