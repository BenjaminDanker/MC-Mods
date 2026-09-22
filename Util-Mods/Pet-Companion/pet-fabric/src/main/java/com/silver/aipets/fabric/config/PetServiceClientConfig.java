package com.silver.aipets.fabric.config;

import com.silver.aipets.common.domain.BackendId;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/** Validated client configuration; both secret values are loaded only from named environment variables. */
public record PetServiceClientConfig(
        BackendId backendId,
        URI baseUri,
        String bearerToken,
        Duration connectTimeout,
        Duration requestTimeout,
        String compassSigningSecret,
        Map<BackendId, String> backendFriendlyNames,
        String conversationMode) {
    public static final String FILE_NAME = "pet-companion.properties";

    private static final Set<String> KNOWN_PROPERTIES = Set.of(
            "authority.enabled",
            "authority.backend-id",
            "authority.base-url",
            "authority.token-environment",
            "authority.compass-signing-environment",
            "authority.backend-friendly-names",
            "authority.connect-timeout-ms",
            "authority.request-timeout-ms",
            "conversation.mode");
    private static final Pattern ENVIRONMENT_NAME = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public PetServiceClientConfig {
        Objects.requireNonNull(backendId, "backendId");
        baseUri = validateBaseUri(baseUri);
        Objects.requireNonNull(bearerToken, "bearerToken");
        if (bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("authority bearer token must contain at least 32 characters");
        }
        Objects.requireNonNull(compassSigningSecret, "compassSigningSecret");
        if (compassSigningSecret.length() < 32 || compassSigningSecret.isBlank()) {
            throw new IllegalArgumentException("compass signing secret must contain at least 32 characters");
        }
        Objects.requireNonNull(backendFriendlyNames, "backendFriendlyNames");
        backendFriendlyNames = Map.copyOf(backendFriendlyNames);
        backendFriendlyNames.forEach((backend, name) -> {
            Objects.requireNonNull(backend, "backendFriendlyNames key");
            if (name == null || name.isBlank() || !name.equals(name.trim()) || name.length() > 64) {
                throw new IllegalArgumentException("backend friendly names must be trimmed non-empty text up to 64 characters");
            }
        });
        validateTimeout(connectTimeout, "connectTimeout");
        validateTimeout(requestTimeout, "requestTimeout");
        conversationMode = Objects.requireNonNull(conversationMode, "conversationMode")
                .trim().toLowerCase(java.util.Locale.ROOT);
        if (!conversationMode.equals("disabled")
                && !conversationMode.equals("staging")
                && !conversationMode.equals("service")) {
            throw new IllegalArgumentException("conversationMode must be disabled, staging, or service");
        }
    }

    /** Compatibility constructor for isolated transport tests; production loading uses a separate key. */
    public PetServiceClientConfig(
            BackendId backendId,
            URI baseUri,
            String bearerToken,
            Duration connectTimeout,
            Duration requestTimeout) {
        this(
                backendId,
                baseUri,
                bearerToken,
                connectTimeout,
                requestTimeout,
                bearerToken,
                Map.of(backendId, backendId.value()),
                "disabled");
    }

    public static Optional<PetServiceClientConfig> load(
            Path configDirectory,
            Map<String, String> environment) throws IOException {
        Objects.requireNonNull(configDirectory, "configDirectory");
        Objects.requireNonNull(environment, "environment");
        Path file = configDirectory.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        for (String name : properties.stringPropertyNames()) {
            if (!KNOWN_PROPERTIES.contains(name)) {
                throw new IllegalArgumentException("Unknown pet service property: " + name);
            }
        }
        if (!Boolean.parseBoolean(properties.getProperty("authority.enabled", "false").trim())) {
            return Optional.empty();
        }

        String environmentName = required(properties, "authority.token-environment");
        if (!ENVIRONMENT_NAME.matcher(environmentName).matches()) {
            throw new IllegalArgumentException("authority.token-environment is invalid");
        }
        String token = environment.get(environmentName);
        if (token == null) {
            throw new IllegalArgumentException(
                    "Required authority token environment variable is missing: " + environmentName);
        }
        String signingEnvironmentName = required(properties, "authority.compass-signing-environment");
        if (!ENVIRONMENT_NAME.matcher(signingEnvironmentName).matches()) {
            throw new IllegalArgumentException("authority.compass-signing-environment is invalid");
        }
        String signingSecret = environment.get(signingEnvironmentName);
        if (signingSecret == null) {
            throw new IllegalArgumentException(
                    "Required compass signing environment variable is missing: " + signingEnvironmentName);
        }
        BackendId backendId = new BackendId(required(properties, "authority.backend-id"));
        return Optional.of(new PetServiceClientConfig(
                backendId,
                URI.create(required(properties, "authority.base-url")),
                token,
                Duration.ofMillis(integer(properties, "authority.connect-timeout-ms", 2_000, 100, 30_000)),
                Duration.ofMillis(integer(properties, "authority.request-timeout-ms", 15_000, 100, 30_000)),
                signingSecret,
                friendlyNames(properties.getProperty("authority.backend-friendly-names", ""), backendId),
                properties.getProperty("conversation.mode", "service")));
    }

    public String friendlyBackendName(BackendId backend) {
        Objects.requireNonNull(backend, "backend");
        return backendFriendlyNames.getOrDefault(backend, backend.value());
    }

    @Override
    public String toString() {
        return "PetServiceClientConfig[backendId=" + backendId
                + ", baseUri=" + baseUri
                + ", bearerToken=<redacted>, connectTimeout=" + connectTimeout
                + ", requestTimeout=" + requestTimeout
                + ", compassSigningSecret=<redacted>, backendFriendlyNames=" + backendFriendlyNames + ']';
    }

    private static Map<BackendId, String> friendlyNames(String encoded, BackendId localBackend) {
        Map<BackendId, String> names = new LinkedHashMap<>();
        if (!encoded.isBlank()) {
            for (String entry : encoded.split(";", -1)) {
                int separator = entry.indexOf('=');
                if (separator <= 0 || separator == entry.length() - 1) {
                    throw new IllegalArgumentException(
                            "authority.backend-friendly-names must use backend=Friendly Name entries separated by semicolons");
                }
                BackendId backend = new BackendId(entry.substring(0, separator).trim());
                String name = entry.substring(separator + 1).trim();
                if (names.putIfAbsent(backend, name) != null) {
                    throw new IllegalArgumentException("Duplicate backend friendly name: " + backend);
                }
            }
        }
        names.putIfAbsent(localBackend, localBackend.value());
        return names;
    }

    private static URI validateBaseUri(URI uri) {
        Objects.requireNonNull(uri, "baseUri");
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("authority.base-url must be an HTTP(S) origin/path without credentials or query");
        }
        String path = uri.getPath();
        try {
            return new URI(
                    uri.getScheme().toLowerCase(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    path.endsWith("/") ? path : path + '/',
                    null,
                    null);
        } catch (URISyntaxException impossible) {
            throw new IllegalArgumentException("authority.base-url is invalid", impossible);
        }
    }

    private static void validateTimeout(Duration timeout, String name) {
        Objects.requireNonNull(timeout, name);
        if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException(name + " must be in 1ms..30s");
        }
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException("Missing or untrimmed property: " + name);
        }
        return value;
    }

    private static int integer(
            Properties properties,
            String name,
            int defaultValue,
            int minimum,
            int maximum) {
        String encoded = properties.getProperty(name);
        int value;
        try {
            value = encoded == null ? defaultValue : Integer.parseInt(encoded.trim());
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException(name + " must be an integer", malformed);
        }
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be in " + minimum + ".." + maximum);
        }
        return value;
    }
}
