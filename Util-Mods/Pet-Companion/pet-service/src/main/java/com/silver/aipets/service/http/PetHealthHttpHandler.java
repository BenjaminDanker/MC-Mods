package com.silver.aipets.service.http;

import com.silver.aipets.service.health.PetReadinessProbe;
import com.silver.aipets.service.health.PetReadinessSnapshot;
import com.silver.aipets.service.metrics.PetOperationalMetrics;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

/** Authenticated liveness/readiness endpoint with explicit optional-provider degradation. */
public final class PetHealthHttpHandler implements HttpHandler {
    private static final byte[] UNAUTHORIZED = jsonError("UNAUTHORIZED");
    private static final byte[] NOT_FOUND = jsonError("NOT_FOUND");
    private static final byte[] METHOD_NOT_ALLOWED = jsonError("METHOD_NOT_ALLOWED");

    private final PetReadinessProbe readiness;
    private final byte[] expectedAuthorization;
    private final PetOperationalMetrics metrics;
    private final Runnable metricsRefresh;

    public PetHealthHttpHandler(PetReadinessProbe readiness, String bearerToken) {
        this(readiness, bearerToken, new PetOperationalMetrics());
    }

    public PetHealthHttpHandler(
            PetReadinessProbe readiness, String bearerToken, PetOperationalMetrics metrics) {
        this(readiness, bearerToken, metrics, () -> { });
    }

    public PetHealthHttpHandler(
            PetReadinessProbe readiness, String bearerToken,
            PetOperationalMetrics metrics, Runnable metricsRefresh) {
        this.readiness = Objects.requireNonNull(readiness, "readiness");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.metricsRefresh = Objects.requireNonNull(metricsRefresh, "metricsRefresh");
        Objects.requireNonNull(bearerToken, "bearerToken");
        if (bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        this.expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set(PetAuthorityHttpHandler.REQUEST_ID_HEADER,
                requestId(exchange.getRequestHeaders()));
        responseHeaders.set("Cache-Control", "no-store");
        responseHeaders.set("Content-Type", "application/json; charset=utf-8");
        try {
            if (!authenticated(exchange.getRequestHeaders())) {
                send(exchange, 401, UNAUTHORIZED);
                return;
            }
            if (!"GET".equals(exchange.getRequestMethod())) {
                responseHeaders.set("Allow", "GET");
                send(exchange, 405, METHOD_NOT_ALLOWED);
                return;
            }
            if (exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, NOT_FOUND);
                return;
            }
            switch (exchange.getRequestURI().getRawPath()) {
                case "/health/live" -> send(exchange, 200,
                        "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8));
                case "/health/ready" -> {
                    PetReadinessSnapshot snapshot = readiness.probe();
                    metrics.recordReadiness(snapshot);
                    send(exchange, snapshot.ready() ? 200 : 503, readinessJson(snapshot));
                }
                case "/health/metrics" -> {
                    responseHeaders.set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
                    try {
                        metricsRefresh.run();
                    } catch (RuntimeException failure) {
                        // Metrics are diagnostic only and must never make the endpoint fail.
                    }
                    send(exchange, 200, metrics.prometheusSnapshot());
                }
                default -> send(exchange, 404, NOT_FOUND);
            }
        } finally {
            exchange.close();
        }
    }

    private boolean authenticated(Headers headers) {
        String authorization = headers.getFirst("Authorization");
        return authorization != null && MessageDigest.isEqual(
                expectedAuthorization, authorization.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] readinessJson(PetReadinessSnapshot snapshot) {
        String status = snapshot.ready() ? "READY" : "NOT_READY";
        return ("{\"status\":\"" + status
                + "\",\"database\":\"" + snapshot.database()
                + "\",\"migrations\":\"" + snapshot.migrations()
                + "\",\"vector\":\"" + snapshot.vector()
                + "\",\"model\":\"" + snapshot.model()
                + "\",\"stripe\":\"" + snapshot.stripe()
                + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static String requestId(Headers headers) {
        String supplied = headers.getFirst(PetAuthorityHttpHandler.REQUEST_ID_HEADER);
        if (supplied != null) {
            try {
                UUID parsed = UUID.fromString(supplied);
                if (parsed.toString().equals(supplied)) {
                    return supplied;
                }
            } catch (IllegalArgumentException ignored) {
                // Replace malformed external correlation IDs.
            }
        }
        return UUID.randomUUID().toString();
    }

    private static byte[] jsonError(String code) {
        return ("{\"error\":\"" + code + "\"}").getBytes(StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }
}
