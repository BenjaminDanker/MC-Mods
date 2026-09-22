package com.silver.authorization;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** HMAC-SHA256 over an ASCII protocol domain, a zero separator, and canonical payload bytes. */
public final class DomainSeparatedHmac {
    private static final String ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;
    private static final int SIGNATURE_BYTES = 32;

    private DomainSeparatedHmac() {}

    public static byte[] sign(String domain, byte[] payload, byte[] key) {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(key, "key");
        if (key.length < KEY_BYTES) throw new IllegalArgumentException("HMAC key must be at least 256 bits");
        byte[] domainBytes = domainBytes(domain);
        byte[] input = new byte[domainBytes.length + 1 + payload.length];
        System.arraycopy(domainBytes, 0, input, 0, domainBytes.length);
        System.arraycopy(payload, 0, input, domainBytes.length + 1, payload.length);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key.clone(), ALGORITHM));
            return mac.doFinal(input);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 unavailable", unavailable);
        }
    }

    public static boolean verify(String domain, byte[] payload, byte[] key, byte[] signature) {
        if (signature == null || signature.length != SIGNATURE_BYTES) return false;
        return MessageDigest.isEqual(sign(domain, payload, key), signature);
    }

    private static byte[] domainBytes(String domain) {
        Objects.requireNonNull(domain, "domain");
        if (domain.isEmpty()) throw new IllegalArgumentException("HMAC domain must not be empty");
        for (int i = 0; i < domain.length(); i++) {
            char value = domain.charAt(i);
            if (value == 0 || value > 0x7f) throw new IllegalArgumentException("HMAC domain must be non-NUL ASCII");
        }
        return domain.getBytes(StandardCharsets.US_ASCII);
    }
}
