package com.silver.aipets.service.persistence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Validated durable identity and retention window for one service mutation. */
public record IdempotencyRequest(
        String scope,
        UUID operationId,
        String requestFingerprint,
        UUID petId,
        Optional<UUID> ownerUuid,
        Instant createdAt,
        Instant lockedUntil,
        Instant expiresAt) {
    private static final Pattern SCOPE = Pattern.compile("[a-z0-9._-]{1,64}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public IdempotencyRequest {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(operationId, "operationId");
        Objects.requireNonNull(requestFingerprint, "requestFingerprint");
        Objects.requireNonNull(petId, "petId");
        ownerUuid = Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(lockedUntil, "lockedUntil");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!SCOPE.matcher(scope).matches()) {
            throw new IllegalArgumentException("scope must be 1-64 lowercase safe characters");
        }
        if (!SHA_256.matcher(requestFingerprint).matches()) {
            throw new IllegalArgumentException("requestFingerprint must be lowercase SHA-256");
        }
        if (!lockedUntil.isAfter(createdAt)) {
            throw new IllegalArgumentException("lockedUntil must be after createdAt");
        }
        if (!expiresAt.isAfter(lockedUntil)) {
            throw new IllegalArgumentException("expiresAt must be after lockedUntil");
        }
    }
}
