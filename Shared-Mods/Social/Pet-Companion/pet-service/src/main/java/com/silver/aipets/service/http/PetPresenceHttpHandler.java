package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.PetPresenceWireCodec;
import com.silver.aipets.common.transport.PetPresenceWireRequest;
import com.silver.aipets.common.transport.PetPresenceReconcileWireCodec;
import com.silver.aipets.common.transport.PetPresenceReconcileWireRequest;
import com.silver.aipets.service.sleep.PetSleepEvent;
import com.silver.aipets.service.sleep.PetSleepService;
import com.silver.aipets.service.sleep.PetSleepTransition;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.Optional;

/** Authenticated Velocity-only whole-network presence ingestion. */
public final class PetPresenceHttpHandler implements HttpHandler {
    private static final int MAX_BODY = 512 * 1_024;
    private final PetSleepService sleep;
    private final PetPresenceWireCodec codec;
    private final byte[] expectedAuthorization;

    public PetPresenceHttpHandler(PetSleepService sleep, String bearerToken) {
        this.sleep = Objects.requireNonNull(sleep, "sleep");
        this.codec = new PetPresenceWireCodec();
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
                send(exchange, 401, "{\"error\":\"UNAUTHORIZED\"}"); return;
            }
            if (!"POST".equals(exchange.getRequestMethod())
                    || !("/v1/presence".equals(exchange.getRequestURI().getRawPath())
                    || "/v1/presence/reconcile".equals(exchange.getRequestURI().getRawPath()))
                    || exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}"); return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                send(exchange, 413, "{\"error\":\"PAYLOAD_TOO_LARGE\"}"); return;
            }
            String encoded = new String(body, StandardCharsets.UTF_8);
            if ("/v1/presence/reconcile".equals(exchange.getRequestURI().getRawPath())) {
                PetPresenceReconcileWireRequest request =
                        new PetPresenceReconcileWireCodec().decode(encoded);
                int changed = sleep.reconcileOnlineOwners(
                        request.onlineOwnerUuids(), request.occurredAt());
                send(exchange, 200, "{\"status\":\"RECONCILED\",\"changed\":" + changed + "}");
                return;
            }
            PetPresenceWireRequest request = codec.decode(encoded);
            Optional<PetSleepTransition> result = request.online()
                    ? sleep.ownerOnline(request.ownerUuid(), request.occurredAt())
                    : sleep.ownerOffline(request.ownerUuid(), request.absenceSessionId().orElseThrow(),
                            request.occurredAt());
            String status = result.map(PetSleepTransition::event)
                    .map(PetSleepEvent::name).orElse("NO_PET");
            send(exchange, 200, "{\"status\":\"" + status + "\"}");
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
