package com.silver.aipets.service.subscription;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

/** Stripe v1 HMAC verifier operating on the untouched request bytes. */
public final class StripeSignatureVerifier {
    public static final Duration DEFAULT_TOLERANCE = Duration.ofMinutes(5);

    private final byte[] secret;
    private final Clock clock;
    private final Duration tolerance;

    public StripeSignatureVerifier(String signingSecret, Clock clock) {
        this(signingSecret, clock, DEFAULT_TOLERANCE);
    }

    StripeSignatureVerifier(String signingSecret, Clock clock, Duration tolerance) {
        if (signingSecret == null || signingSecret.isBlank()) {
            throw new IllegalArgumentException("signingSecret must not be blank");
        }
        this.secret = signingSecret.getBytes(StandardCharsets.UTF_8);
        this.clock = Objects.requireNonNull(clock, "clock");
        this.tolerance = Objects.requireNonNull(tolerance, "tolerance");
    }

    public void verify(byte[] rawPayload, String signatureHeader) {
        Objects.requireNonNull(rawPayload, "rawPayload");
        if (signatureHeader == null || signatureHeader.isBlank()
                || signatureHeader.length() > 4_096) {
            throw new StripeSignatureException("Missing or oversized Stripe-Signature header");
        }
        Long timestampSeconds = null;
        List<byte[]> candidates = new ArrayList<>();
        for (String item : signatureHeader.split(",")) {
            int separator = item.indexOf('=');
            if (separator <= 0) continue;
            String key = item.substring(0, separator).trim();
            String value = item.substring(separator + 1).trim();
            try {
                if ("t".equals(key)) timestampSeconds = Long.parseLong(value);
                if ("v1".equals(key) && value.length() == 64) {
                    candidates.add(HexFormat.of().parseHex(value));
                }
            } catch (IllegalArgumentException malformed) {
                throw new StripeSignatureException("Malformed Stripe-Signature header");
            }
        }
        if (timestampSeconds == null || candidates.isEmpty()) {
            throw new StripeSignatureException("Stripe-Signature lacks timestamp or v1 digest");
        }
        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(timestampSeconds);
        } catch (RuntimeException malformed) {
            throw new StripeSignatureException("Stripe signature timestamp is invalid");
        }
        Duration age = Duration.between(signedAt, clock.instant()).abs();
        if (age.compareTo(tolerance) > 0) {
            throw new StripeSignatureException("Stripe signature timestamp is outside tolerance");
        }
        byte[] expected = hmac(timestampSeconds, rawPayload);
        boolean matched = false;
        for (byte[] candidate : candidates) {
            matched |= MessageDigest.isEqual(expected, candidate);
        }
        if (!matched) throw new StripeSignatureException("Stripe signature mismatch");
    }

    private byte[] hmac(long timestampSeconds, byte[] rawPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            mac.update(Long.toString(timestampSeconds).getBytes(StandardCharsets.US_ASCII));
            mac.update((byte) '.');
            return mac.doFinal(rawPayload);
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is unavailable", unavailable);
        }
    }
}
