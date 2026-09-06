package com.silver.aipets.service.http;

import com.silver.aipets.service.vector.MemoryReindexResult;
import com.silver.aipets.service.vector.MemoryReindexService;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/** Authenticated bounded admin trigger for rebuilding vector jobs from SQL cards. */
public final class MemoryReindexHttpHandler implements HttpHandler {
    private static final String PATH = "/v1/admin/memory/reindex";
    private final MemoryReindexService service;
    private final byte[] expectedAuthorization;

    public MemoryReindexHttpHandler(MemoryReindexService service, String bearerToken) {
        this.service = Objects.requireNonNull(service, "service");
        if (bearerToken == null || bearerToken.length() < 32 || bearerToken.isBlank()) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        Headers responseHeaders = exchange.getResponseHeaders();
        responseHeaders.set("Cache-Control", "no-store");
        responseHeaders.set("Content-Type", "application/json; charset=utf-8");
        try {
            if (!authenticated(exchange.getRequestHeaders())) {
                send(exchange, 401, "{\"error\":\"UNAUTHORIZED\"}");
                return;
            }
            if (!"POST".equals(exchange.getRequestMethod())
                    || !PATH.equals(exchange.getRequestURI().getRawPath())) {
                responseHeaders.set("Allow", "POST");
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}");
                return;
            }
            if (exchange.getRequestBody().readNBytes(2).length != 0) {
                send(exchange, 400, "{\"error\":\"BAD_REQUEST\"}");
                return;
            }
            int pageSize = pageSize(exchange.getRequestURI().getRawQuery());
            MemoryReindexResult result = service.enqueueAll(pageSize);
            send(exchange, 200, "{\"scanned\":" + result.scanned()
                    + ",\"enqueued\":" + result.enqueued()
                    + ",\"already_queued\":" + result.alreadyQueued() + "}");
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

    private static int pageSize(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return 250;
        }
        if (!rawQuery.startsWith("pageSize=") || rawQuery.indexOf('&') >= 0) {
            throw new IllegalArgumentException("Only pageSize query parameter is accepted");
        }
        try {
            return Integer.parseInt(rawQuery.substring("pageSize=".length()));
        } catch (NumberFormatException malformed) {
            throw new IllegalArgumentException("pageSize must be an integer", malformed);
        }
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
