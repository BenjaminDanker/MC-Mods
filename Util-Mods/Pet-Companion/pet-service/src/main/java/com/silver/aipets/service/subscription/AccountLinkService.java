package com.silver.aipets.service.subscription;

import com.silver.aipets.common.transport.AccountLinkWireResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.net.URI;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Generates opaque URL tokens while persisting only HMAC digests. */
public final class AccountLinkService {
    private static final int TOKEN_BYTES = 32;
    private static final int MAX_GENERATIONS = 3;
    private static final Duration GENERATION_WINDOW = Duration.ofMinutes(10);

    private final AccountLinkRepository repository;
    private final Clock clock;
    private final Duration timeToLive;
    private final SecureRandom random;
    private final SecretKeySpec digestKey;
    private final URI publicBaseUri;

    public AccountLinkService(
            AccountLinkRepository repository,
            Clock clock,
            Duration timeToLive,
            SecureRandom random,
            String pepper,
            URI publicBaseUri) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.timeToLive = Objects.requireNonNull(timeToLive, "timeToLive");
        this.random = Objects.requireNonNull(random, "random");
        this.publicBaseUri = Objects.requireNonNull(publicBaseUri, "publicBaseUri");
        if (timeToLive.compareTo(Duration.ofMinutes(2)) < 0
                || timeToLive.compareTo(Duration.ofMinutes(30)) > 0) {
            throw new IllegalArgumentException("timeToLive must be in 2-30 minutes");
        }
        if (pepper == null || pepper.length() < 32) {
            throw new IllegalArgumentException("pepper must contain at least 32 characters");
        }
        digestKey = new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public AccountLinkWireResult generate(UUID ownerUuid) {
        Objects.requireNonNull(ownerUuid, "ownerUuid");
        Instant now = clock.instant();
        Instant expiresAt = now.plus(timeToLive);
        String token = randomToken();
        boolean created = repository.create(
                ownerUuid,
                digest(token),
                now,
                expiresAt,
                now.minus(GENERATION_WINDOW),
                MAX_GENERATIONS);
        return created
                ? AccountLinkWireResult.created(checkoutUrl(token).toString(), expiresAt)
                : AccountLinkWireResult.rateLimited();
    }

    public Optional<AccountLinkTarget> resolve(String submittedToken) {
        String token = validateToken(submittedToken);
        String tokenHash = digest(token);
        return repository.findValid(tokenHash, clock.instant());
    }

    public boolean attachCheckout(AccountLinkTarget target, String checkoutSessionId) {
        Objects.requireNonNull(target, "target");
        if (checkoutSessionId == null || !checkoutSessionId.startsWith("cs_")
                || checkoutSessionId.length() > 255) {
            throw new IllegalArgumentException("checkoutSessionId is invalid");
        }
        return repository.attachCheckout(target.tokenHash(), checkoutSessionId, clock.instant());
    }

    private String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String digest(String normalizedCode) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(digestKey);
            return HexFormat.of().formatHex(
                    mac.doFinal(normalizedCode.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException unavailable) {
            throw new IllegalStateException("HmacSHA256 is unavailable", unavailable);
        }
    }

    private static String validateToken(String submittedToken) {
        if (submittedToken == null || submittedToken.length() != 43
                || !submittedToken.matches("[A-Za-z0-9_-]{43}")) {
            throw new IllegalArgumentException("token is malformed");
        }
        return submittedToken;
    }

    private URI checkoutUrl(String token) {
        return publicBaseUri.resolve("/checkout/" + token);
    }
}
