package com.silver.aipets.service.adoption;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record PendingAdoptionLinkResult(
        PendingAdoptionStartStatus status,
        Optional<String> checkoutUrl,
        Optional<Instant> expiresAt) {
    public PendingAdoptionLinkResult {
        Objects.requireNonNull(status, "status");
        checkoutUrl = Objects.requireNonNull(checkoutUrl, "checkoutUrl");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if ((status == PendingAdoptionStartStatus.CREATED)
                != (checkoutUrl.isPresent() && expiresAt.isPresent())) {
            throw new IllegalArgumentException("Pending checkout URL presence does not match status");
        }
    }
}
