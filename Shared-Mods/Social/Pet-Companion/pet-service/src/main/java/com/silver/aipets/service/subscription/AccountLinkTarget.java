package com.silver.aipets.service.subscription;

import java.util.Objects;
import java.util.UUID;

/** Server-resolved identity for an opaque account-link token. */
public record AccountLinkTarget(UUID ownerUuid, String tokenHash) {
    public AccountLinkTarget {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(tokenHash, "tokenHash");
        if (!tokenHash.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("tokenHash must be lowercase SHA-256/HMAC hex");
        }
    }
}
