package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.CustomerPortalWireCodec;
import com.silver.aipets.common.transport.CustomerPortalWireResult;
import com.silver.aipets.service.subscription.CustomerPortalService;
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

/** Private backend route creating an on-demand portal for the authenticated player's UUID. */
public final class CustomerPortalHttpHandler implements HttpHandler {
    private static final String PREFIX = "/v1/customer-portal/";
    private final CustomerPortalService portals;
    private final CustomerPortalWireCodec codec = new CustomerPortalWireCodec();
    private final byte[] expectedAuthorization;
    private final AccountLinkHttpHandler.AttemptLimiter limiter;

    public CustomerPortalHttpHandler(
            CustomerPortalService portals, String bearerToken, Clock clock) {
        this.portals = Objects.requireNonNull(portals, "portals");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
        limiter = new AccountLinkHttpHandler.AttemptLimiter(
                Objects.requireNonNull(clock, "clock"), Duration.ofMinutes(5), 5, 4_096);
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
            String ownerText = path.substring(PREFIX.length());
            if (ownerText.isEmpty() || ownerText.contains("/")) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            UUID ownerUuid = UUID.fromString(ownerText);
            if (exchange.getRequestBody().readNBytes(2).length != 0) {
                send(exchange, 400, "{\"error\":\"BODY_NOT_ALLOWED\"}");
                return;
            }
            CustomerPortalWireResult result = limiter.allow(ownerUuid.toString())
                    ? portals.create(ownerUuid) : CustomerPortalWireResult.rateLimited();
            int status = switch (result.status()) {
                case CREATED -> 201;
                case NOT_LINKED -> 404;
                case RATE_LIMITED -> 429;
            };
            send(exchange, status, codec.encode(result));
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
}
