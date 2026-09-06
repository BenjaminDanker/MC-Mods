package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.RecallResetWireCodec;
import com.silver.aipets.common.transport.RecallResetWireResult;
import com.silver.aipets.common.transport.RecallResetWireStatus;
import com.silver.aipets.service.recall.RecallAdminService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/** Backend-only administrative recall reset; Fabric separately enforces operator permission. */
public final class RecallAdminHttpHandler implements HttpHandler {
    private static final String PREFIX = "/v1/admin/recall-reset/";
    private final RecallAdminService service;
    private final RecallResetWireCodec codec = new RecallResetWireCodec();
    private final byte[] expectedAuthorization;
    private final AccountLinkHttpHandler.AttemptLimiter limiter;
    private final Clock clock;

    public RecallAdminHttpHandler(RecallAdminService service, String bearerToken, Clock clock) {
        this.service = Objects.requireNonNull(service, "service");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
        this.clock = Objects.requireNonNull(clock, "clock");
        limiter = new AccountLinkHttpHandler.AttemptLimiter(
                this.clock, Duration.ofMinutes(5), 20, 4_096);
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
            String path = exchange.getRequestURI().getRawPath();
            if (!"POST".equals(exchange.getRequestMethod())
                    || exchange.getRequestURI().getRawQuery() != null
                    || !path.startsWith(PREFIX)) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            String petText = path.substring(PREFIX.length());
            if (petText.isEmpty() || petText.contains("/")
                    || exchange.getRequestBody().readNBytes(2).length != 0) {
                send(exchange, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }
            UUID petId = UUID.fromString(petText);
            RecallResetWireResult result = limiter.allow(petId.toString())
                    ? service.resetCurrentPeriod(petId)
                    : new RecallResetWireResult(
                            RecallResetWireStatus.RATE_LIMITED,
                            java.time.YearMonth.now(clock.withZone(java.time.ZoneOffset.UTC)).toString());
            send(exchange, result.status() == RecallResetWireStatus.RATE_LIMITED ? 429 : 200,
                    codec.encode(result));
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
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
