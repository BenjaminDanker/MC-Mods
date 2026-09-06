package com.silver.aipets.common.transport;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

public record CustomerPortalWireResult(
        CustomerPortalWireStatus status, Optional<String> portalUrl) {
    public CustomerPortalWireResult {
        Objects.requireNonNull(status, "status");
        portalUrl = Objects.requireNonNull(portalUrl, "portalUrl");
        if ((status == CustomerPortalWireStatus.CREATED) != portalUrl.isPresent()) {
            throw new IllegalArgumentException("Portal URL presence does not match status");
        }
        portalUrl.ifPresent(value -> {
            URI parsed = URI.create(value);
            if (!"https".equalsIgnoreCase(parsed.getScheme())
                    || !"billing.stripe.com".equalsIgnoreCase(parsed.getHost())
                    || parsed.getUserInfo() != null
                    || parsed.getFragment() != null) {
                throw new IllegalArgumentException("Portal URL is not a Stripe HTTPS URL");
            }
        });
    }

    public static CustomerPortalWireResult created(String portalUrl) {
        return new CustomerPortalWireResult(
                CustomerPortalWireStatus.CREATED, Optional.of(portalUrl));
    }

    public static CustomerPortalWireResult notLinked() {
        return new CustomerPortalWireResult(
                CustomerPortalWireStatus.NOT_LINKED, Optional.empty());
    }

    public static CustomerPortalWireResult rateLimited() {
        return new CustomerPortalWireResult(
                CustomerPortalWireStatus.RATE_LIMITED, Optional.empty());
    }
}
