package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.SubscriptionAccessWireCodec;
import com.silver.aipets.common.transport.SubscriptionAccessWireResult;
import com.silver.aipets.service.subscription.SubscriptionAccess;
import com.silver.aipets.service.subscription.SubscriptionAccessDetails;
import com.silver.aipets.service.billing.AiBudgetService;
import com.silver.aipets.service.billing.AiBudgetSnapshot;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

/** Authenticated, UUID-bound read used to render billing actions without duplicate Checkout links. */
public final class SubscriptionAccessHttpHandler implements HttpHandler {
    private static final String PREFIX = "/v1/subscriptions/access/";

    private final SubscriptionAccess access;
    private final SubscriptionAccessWireCodec codec = new SubscriptionAccessWireCodec();
    private final byte[] expectedAuthorization;
    private final AiBudgetService budget;

    public SubscriptionAccessHttpHandler(SubscriptionAccess access, String bearerToken) {
        this(access, bearerToken, AiBudgetService.UNLIMITED);
    }

    public SubscriptionAccessHttpHandler(
            SubscriptionAccess access, String bearerToken, AiBudgetService budget) {
        this.access = Objects.requireNonNull(access, "access");
        this.budget = Objects.requireNonNull(budget, "budget");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
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
            if (!"GET".equals(exchange.getRequestMethod())
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
            if (exchange.getRequestBody().readNBytes(1).length != 0) {
                send(exchange, 400, "{\"error\":\"BODY_NOT_ALLOWED\"}");
                return;
            }
            SubscriptionAccessDetails details = access.details(ownerUuid);
            AiBudgetSnapshot budgetSnapshot = budget.snapshot(ownerUuid, java.time.Instant.now());
            send(exchange, 200, codec.encode(new SubscriptionAccessWireResult(
                    details.aiAccessEnabled(),
                    details.status(),
                    details.cancelAtPeriodEnd(),
                    details.currentPeriodEnd() == null ? null : details.currentPeriodEnd().toString(),
                    budgetSnapshot.budgetUsd(), budgetSnapshot.consumedUsd(), budgetSnapshot.remainingUsd())));
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
