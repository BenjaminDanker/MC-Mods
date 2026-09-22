package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.PendingAdoptionNoticeWire;
import com.silver.aipets.common.transport.PendingAdoptionNoticeWireCodec;
import com.silver.aipets.service.adoption.PendingAdoptionRepository;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;

/** Authenticated backend-only read/ack API for durable adoption completion messages. */
public final class PetAdoptionNotificationHttpHandler implements HttpHandler {
    private static final String PREFIX = "/v1/adoptions/notifications/";
    private final PendingAdoptionRepository pending;
    private final PendingAdoptionNoticeWireCodec codec = new PendingAdoptionNoticeWireCodec();
    private final byte[] expectedAuthorization;
    private final Clock clock;

    public PetAdoptionNotificationHttpHandler(
            PendingAdoptionRepository pending, String bearerToken, Clock clock) {
        this.pending = Objects.requireNonNull(pending, "pending");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
        this.clock = Objects.requireNonNull(clock, "clock");
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
            if (!path.startsWith(PREFIX) || exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            String[] segments = path.substring(PREFIX.length()).split("/", -1);
            if (segments.length == 1 && "GET".equals(exchange.getRequestMethod())) {
                UUID owner = UUID.fromString(segments[0]);
                var notice = pending.findNotification(owner);
                if (notice.isEmpty()) {
                    exchange.sendResponseHeaders(204, -1);
                } else {
                    PendingAdoptionNoticeWire wire = new PendingAdoptionNoticeWire(
                            notice.orElseThrow().intentId(), notice.orElseThrow().petName());
                    send(exchange, 200, codec.encode(wire));
                }
                return;
            }
            if (segments.length == 3 && "ack".equals(segments[2])
                    && "POST".equals(exchange.getRequestMethod())) {
                if (exchange.getRequestBody().readNBytes(1).length != 0) {
                    send(exchange, 400, "{\"error\":\"BODY_NOT_ALLOWED\"}");
                    return;
                }
                boolean acknowledged = pending.acknowledgeNotification(
                        UUID.fromString(segments[0]), UUID.fromString(segments[1]), clock.instant());
                exchange.sendResponseHeaders(acknowledged ? 204 : 409, -1);
                return;
            }
            send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
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
