package com.silver.aipets.service.http;

import com.silver.aipets.common.transport.PetDialogueWireCodec;
import com.silver.aipets.common.transport.PetDialogueWireRequest;
import com.silver.aipets.common.transport.PetDialogueWireResult;
import com.silver.aipets.common.transport.PetWireFormatException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;

/** Authenticated transport seam; the responder owns authoritative context/model work. */
public final class PetDialogueHttpHandler implements HttpHandler {
    public interface Responder {
        PetDialogueWireResult respond(PetDialogueWireRequest request);
    }

    @FunctionalInterface
    public interface AsyncResponder {
        CompletionStage<PetDialogueWireResult> respondAsync(PetDialogueWireRequest request);
    }

    private static final String PATH = "/v1/dialogue";
    private static final int MAX_BODY = 4_096;
    private final AsyncResponder responder;
    private final PetDialogueWireCodec codec = new PetDialogueWireCodec();
    private final byte[] expectedAuthorization;

    public PetDialogueHttpHandler(Responder responder, String bearerToken) {
        this(request -> CompletableFuture.completedFuture(responder.respond(request)), bearerToken, true);
        Objects.requireNonNull(responder, "responder");
    }

    public static PetDialogueHttpHandler asynchronous(AsyncResponder responder, String bearerToken) {
        return new PetDialogueHttpHandler(responder, bearerToken, true);
    }

    private PetDialogueHttpHandler(AsyncResponder responder, String bearerToken, boolean ignored) {
        this.responder = Objects.requireNonNull(responder, "responder");
        if (bearerToken == null || bearerToken.isBlank() || bearerToken.length() < 32) {
            throw new IllegalArgumentException("bearerToken must contain at least 32 characters");
        }
        expectedAuthorization = ("Bearer " + bearerToken).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        boolean asyncHandoff = false;
        try {
            if (!authenticated(exchange.getRequestHeaders())) {
                send(exchange, 401, "{\"error\":\"UNAUTHORIZED\"}"); return;
            }
            if (!"POST".equals(exchange.getRequestMethod())
                    || !PATH.equals(exchange.getRequestURI().getRawPath())
                    || exchange.getRequestURI().getRawQuery() != null) {
                send(exchange, 404, "{\"error\":\"NOT_FOUND\"}"); return;
            }
            byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
            if (body.length > MAX_BODY) {
                send(exchange, 413, "{\"error\":\"PAYLOAD_TOO_LARGE\"}"); return;
            }
            PetDialogueWireRequest decoded = codec.decodeRequest(new String(body, StandardCharsets.UTF_8));
            CompletionStage<PetDialogueWireResult> response = responder.respondAsync(decoded);
            asyncHandoff = true;
            response.whenComplete((result, failure) -> {
                try {
                    if (failure != null || result == null) {
                        send(exchange, 503, "{\"error\":\"SERVICE_FAILURE\"}");
                    } else {
                        send(exchange, 200, codec.encode(result));
                    }
                } catch (IOException writeFailure) {
                    System.getLogger(PetDialogueHttpHandler.class.getName()).log(
                            System.Logger.Level.DEBUG, "Dialogue response transmission failed");
                } finally {
                    exchange.close();
                }
            });
        } catch (IllegalArgumentException | PetWireFormatException malformed) {
            send(exchange, 400, "{\"error\":\"BAD_REQUEST\"}");
        } catch (RuntimeException failure) {
            send(exchange, 503, "{\"error\":\"SERVICE_FAILURE\"}");
        } finally {
            if (!asyncHandoff) exchange.close();
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
