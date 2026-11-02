package com.silver.aipets.service.metrics;

import com.silver.aipets.common.transport.PetMetricWireCodec;
import com.silver.aipets.common.transport.PetMetricWireEvent;
import com.silver.aipets.common.transport.PetWireFormatException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/** Authenticated bounded ingestion of low-cardinality backend metric deltas. */
public final class PetMetricsEventHttpHandler implements HttpHandler {
    private static final int MAX_BODY = 256;
    private final PetOperationalMetrics metrics;
    private final PetMetricWireCodec codec = new PetMetricWireCodec();
    private final byte[] expectedAuthorization;

    public PetMetricsEventHttpHandler(PetOperationalMetrics metrics, String bearerToken) {
        this.metrics = Objects.requireNonNull(metrics, "metrics");
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
            if (!"POST".equals(exchange.getRequestMethod())
                    || !"/v1/metrics/events".equals(exchange.getRequestURI().getRawPath())
                    || exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                send(exchange, 413, "{\"error\":\"PAYLOAD_TOO_LARGE\"}");
                return;
            }
            PetMetricWireEvent event = codec.decode(new String(body, StandardCharsets.UTF_8));
            metrics.add(toCounter(event.metric()), event.amount());
            send(exchange, 202, "{\"status\":\"ACCEPTED\"}");
        } catch (PetWireFormatException | IllegalArgumentException malformed) {
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

    private static PetOperationalMetrics.Counter toCounter(PetMetricWireEvent.Metric metric) {
        return switch (metric) {
            case DUPLICATE_ENTITY_DISCARDS ->
                    PetOperationalMetrics.Counter.DUPLICATE_ENTITY_DISCARDS;
            case STALE_ENTITY_DISCARDS ->
                    PetOperationalMetrics.Counter.STALE_ENTITY_DISCARDS;
            case TRANSFER_AUTO_PICKUP_FAILURES ->
                    PetOperationalMetrics.Counter.TRANSFER_AUTO_PICKUP_FAILURES;
            case TRANSFER_AUTO_PLACE_FAILURES ->
                    PetOperationalMetrics.Counter.TRANSFER_AUTO_PLACE_FAILURES;
        };
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] encoded = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, encoded.length);
        exchange.getResponseBody().write(encoded);
    }
}
