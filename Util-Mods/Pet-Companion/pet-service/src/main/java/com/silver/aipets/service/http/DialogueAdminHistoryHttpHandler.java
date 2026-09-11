package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.DialogueHistoryWireCodec;
import com.silver.aipets.service.dialogue.JdbcDialogueAdminHistoryReader;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.UUID;

/** Authenticated bounded admin read; Fabric enforces the in-game permission node. */
public final class DialogueAdminHistoryHttpHandler implements HttpHandler {
    private static final String PREFIX = "/v1/admin/dialogue/history/";
    private final JdbcDialogueAdminHistoryReader reader;
    private final DialogueHistoryWireCodec codec = new DialogueHistoryWireCodec();
    private final byte[] expectedAuthorization;

    public DialogueAdminHistoryHttpHandler(
            JdbcDialogueAdminHistoryReader reader, String bearerToken) {
        this.reader = Objects.requireNonNull(reader, "reader");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Cache-Control", "no-store");
        headers.set("Content-Type", "application/json; charset=utf-8");
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
            var history = reader.read(ownerUuid, 16, 32);
            if (history.isEmpty()) {
                send(exchange, 404, "{\"error\":\"PET_NOT_FOUND\"}");
                return;
            }
            send(exchange, 200, codec.encode(history.orElseThrow()));
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
