package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.AccountLinkWireCodec;
import com.silver.aipets.common.transport.AccountLinkWireResult;
import com.silver.aipets.common.transport.AccountLinkWireStatus;
import com.silver.aipets.service.subscription.AccountLinkService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Authenticated backend API for generating UUID-bound one-time Checkout URLs. */
public final class AccountLinkHttpHandler implements HttpHandler {
    private static final String PREFIX = "/v1/account-links/";

    private final AccountLinkService links;
    private final AccountLinkWireCodec codec = new AccountLinkWireCodec();
    private final byte[] expectedAuthorization;

    public AccountLinkHttpHandler(
            AccountLinkService links, String bearerToken, Clock clock) {
        this.links = Objects.requireNonNull(links, "links");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
        Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        try {
            if (!authenticated(exchange.getRequestHeaders())) {
                send(exchange, 401, "{\"error\":\"UNAUTHORIZED\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())
                    || exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            String path = exchange.getRequestURI().getRawPath();
            if (!path.startsWith(PREFIX)) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            String ownerText = path.substring(PREFIX.length());
            if (ownerText.isEmpty() || ownerText.contains("/")) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            UUID ownerUuid = UUID.fromString(ownerText);
            byte[] body = exchange.getRequestBody().readNBytes(2);
            if (body.length != 0) {
                send(exchange, 400, "{\"error\":\"BODY_NOT_ALLOWED\"}");
                return;
            }
            AccountLinkWireResult result = links.generate(ownerUuid);
            int status = switch (result.status()) {
                case CREATED -> 201;
                case RATE_LIMITED -> 429;
                case CHECKOUT_IN_PROGRESS -> 409;
            };
            send(exchange, status, codec.encodeResult(result));
        } catch (IllegalArgumentException malformed) {
            send(exchange, 400, "{\"error\":\"BAD_REQUEST\"}");
        } catch (RuntimeException failure) {
            send(exchange, 503, "{\"error\":\"SERVICE_FAILURE\"}");
        } finally {
            exchange.close();
        }
    }

    private boolean authenticated(Headers headers) {
        String value = headers.getFirst("Authorization");
        return value != null && MessageDigest.isEqual(
                expectedAuthorization, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
    }

    /** Small bounded process-local limiter; durable token state still provides consume-once safety. */
    static final class AttemptLimiter {
        private final Clock clock;
        private final Duration window;
        private final int maximumAttempts;
        private final int maximumKeys;
        private final Map<String, Window> windows = new ConcurrentHashMap<>();

        AttemptLimiter(Clock clock, Duration window, int maximumAttempts, int maximumKeys) {
            this.clock = clock;
            this.window = window;
            this.maximumAttempts = maximumAttempts;
            this.maximumKeys = maximumKeys;
        }

        synchronized boolean allow(String key) {
            Instant now = clock.instant();
            windows.entrySet().removeIf(entry -> !entry.getValue().endsAt().isAfter(now));
            Window current = windows.get(key);
            if (current == null) {
                if (windows.size() >= maximumKeys) return false;
                windows.put(key, new Window(now.plus(window), 1));
                return true;
            }
            if (current.attempts() >= maximumAttempts) return false;
            windows.put(key, new Window(current.endsAt(), current.attempts() + 1));
            return true;
        }

        private record Window(Instant endsAt, int attempts) {
        }
    }
}
