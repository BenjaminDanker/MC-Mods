package com.silver.aipets.fabric.compass;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Server-only HMAC authority for pet compass identity data. */
public final class PetCompassSigner {
    private static final String ALGORITHM = "HmacSHA256";
    private static final String DOMAIN = "pet-companion-compass:v1";

    private final SecretKeySpec key;

    public PetCompassSigner(String secret) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length() < 32 || secret.isBlank()) {
            throw new IllegalArgumentException("compass signing secret must contain at least 32 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String sign(UUID ownerUuid, UUID petId) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Objects.requireNonNull(petId, "petId");
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] signature = mac.doFinal(payload(ownerUuid, petId));
            return HexFormat.of().formatHex(signature);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("Required HMAC algorithm is unavailable", unavailable);
        }
    }

    public boolean verifies(UUID ownerUuid, UUID petId, String encodedSignature) {
        if (encodedSignature == null || encodedSignature.length() != 64) {
            return false;
        }
        byte[] expected = sign(ownerUuid, petId).getBytes(StandardCharsets.US_ASCII);
        byte[] supplied = encodedSignature.getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, supplied);
    }

    private static byte[] payload(UUID ownerUuid, UUID petId) {
        return (DOMAIN + '\0' + ownerUuid + '\0' + petId).getBytes(StandardCharsets.UTF_8);
    }
}
