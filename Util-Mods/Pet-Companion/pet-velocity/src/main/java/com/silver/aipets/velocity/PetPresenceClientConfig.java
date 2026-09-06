package com.silver.aipets.velocity;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Environment-only proxy configuration; {@link #toString()} never contains the token. */
public record PetPresenceClientConfig(URI serviceBaseUri, String bearerToken, Duration timeout) {
    public PetPresenceClientConfig {
        Objects.requireNonNull(serviceBaseUri, "serviceBaseUri");
        Objects.requireNonNull(bearerToken, "bearerToken");
        Objects.requireNonNull(timeout, "timeout");
        String scheme = serviceBaseUri.getScheme();
        if (!("http".equals(scheme) || "https".equals(scheme))
                || serviceBaseUri.getHost() == null
                || serviceBaseUri.getRawUserInfo() != null
                || serviceBaseUri.getRawQuery() != null
                || serviceBaseUri.getRawFragment() != null
                || !(serviceBaseUri.getPath().isEmpty() || "/".equals(serviceBaseUri.getPath()))) {
            throw new IllegalArgumentException("PET_SERVICE_BASE_URI must be an HTTP(S) origin");
        }
        if (bearerToken.length() < 32 || bearerToken.length() > 512 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("PET_SERVICE_TOKEN must contain 32-512 characters");
        }
        if (timeout.isZero() || timeout.isNegative() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("presence timeout must be in 1ms..30s");
        }
    }

    public static Optional<PetPresenceClientConfig> fromEnvironment(Map<String, String> environment) {
        Objects.requireNonNull(environment, "environment");
        String base = environment.get("PET_SERVICE_BASE_URI");
        String token = environment.get("PET_SERVICE_TOKEN");
        if ((base == null || base.isBlank()) && (token == null || token.isBlank())) {
            return Optional.empty();
        }
        if (base == null || base.isBlank() || token == null || token.isBlank()) {
            throw new IllegalArgumentException(
                    "PET_SERVICE_BASE_URI and PET_SERVICE_TOKEN must be configured together");
        }
        long timeoutMs;
        try {
            timeoutMs = Long.parseLong(environment.getOrDefault("PET_PRESENCE_TIMEOUT_MS", "3000"));
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("PET_PRESENCE_TIMEOUT_MS must be an integer", malformed);
        }
        return Optional.of(new PetPresenceClientConfig(
                URI.create(base), token, Duration.ofMillis(timeoutMs)));
    }

    @Override public String toString() {
        return "PetPresenceClientConfig[serviceBaseUri=" + serviceBaseUri
                + ", timeout=" + timeout + "]";
    }
}
