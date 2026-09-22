package com.silver.authorization;

import java.util.Objects;

/** HMAC-authenticated wire object. Signature is Base64 URL-safe without padding. */
public record SignedAuthorizationSnapshot(AuthorizationSnapshot snapshot, String signature) {
    public SignedAuthorizationSnapshot {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(signature, "signature");
        if (signature.isBlank()) throw new IllegalArgumentException("signature is required");
    }
}
